package link.e4steam.steam;

import com.codedisaster.steamworks.*;
import java.nio.*;
import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.*;

/** Manual native benchmark: two isolated JVMs, dummy data, no Minecraft saves or credentials. */
public final class SteamMessagesBulkBenchmark {
    private static final int CHANNEL=479,MAGIC=0x42345354,CHUNK=24*1024,TOTAL=2*1024*1024;
    public static void main(String[] args) throws Exception {
        Path shared=Paths.get(args[1]);Files.createDirectories(shared);Files.writeString(shared.resolve("classpath.txt"),System.getProperty("java.class.path"));
        Files.write(Paths.get("steam_appid.txt"),"480\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        if(args[0].equals("server"))server(shared);else client(shared);
    }
    private static void server(Path shared) throws Exception {
        SteamNativeLibraryLoader loader=new SteamNativeLibraryLoader();
        if(!SteamGameServerAPI.loadLibraries(loader))throw new IllegalStateException("Server native library unavailable");
        if(!SteamGameServerAPI.init(0,(short)29996,(short)0,SteamGameServerAPI.ServerMode.Authentication,"1.0.0.0"))throw new IllegalStateException("Server API unavailable");
        AtomicBoolean ready=new AtomicBoolean();
        SteamGameServer game=new SteamGameServer((SteamGameServerCallback)Proxy.newProxyInstance(SteamGameServerCallback.class.getClassLoader(),new Class<?>[]{SteamGameServerCallback.class},(proxy,method,values)->{
            if(method.getName().equals("onSteamServersConnected"))ready.set(true);return null;
        }));
        try {
            game.setProduct("480");game.setModDir("e4steam-benchmark");game.setGameDescription("Private native bulk benchmark");
            game.setDedicatedServer(true);game.setMaxPlayerCount(1);game.setServerName("Private benchmark");game.setMapName("benchmark");
            game.setAdvertiseServerActive(false);game.logOnAnonymous();
            long deadline=System.currentTimeMillis()+30_000;
            while(!ready.get()&&System.currentTimeMillis()<deadline){SteamGameServerAPI.runCallbacks();Thread.sleep(10);}
            if(!ready.get())throw new IllegalStateException("Anonymous benchmark server did not log on");
            AtomicReference<SteamNetworkingMessagesTransport> transport=new AtomicReference<>();
            try(SteamNetworkingMessagesTransport messages=SteamNetworkingMessagesTransport.openGameServer(loader.steamApiPath(),new SteamNetworkingMessagesTransport.SessionListener(){
                public void onSessionRequest(long id){try{if(id==Long.parseUnsignedLong(Files.readString(shared.resolve("client.id")).trim()))transport.get().accept(id);}catch(Exception ignored){}}
                public void onSessionFailed(long id,int reason,String detail){System.out.println("BENCH_SERVER_SESSION_FAILED="+reason);}
            })) {
                transport.set(messages);Files.writeString(shared.resolve("server.id"),Long.toUnsignedString(SteamNativeHandle.getNativeHandle(SteamGameServerAPI.getSteamID())));
                long received=0;int completed=0;deadline=System.currentTimeMillis()+120_000;
                ByteBuffer packet=ByteBuffer.allocateDirect(64*1024);
                while(completed<2&&System.currentTimeMillis()<deadline) {
                    SteamGameServerAPI.runCallbacks();
                    if(Files.isRegularFile(shared.resolve("client.id")))messages.accept(Long.parseUnsignedLong(Files.readString(shared.resolve("client.id")).trim()));
                    while(messages.availablePacketSize(CHANNEL)>0) {
                        packet.clear();var remote=messages.receive(packet,CHANNEL);packet.flip();
                        if(packet.getInt()!=MAGIC)throw new IllegalStateException("Foreign benchmark data");
                        int type=packet.get(),round=packet.getInt();long offset=packet.getLong();
                        if(type==0) {
                            received=0;
                            if(round==-1){ByteBuffer ack=header(3,-1,0);while(!messages.send(remote.remoteSteamId(),ack,false,CHANNEL)){SteamGameServerAPI.runCallbacks();Thread.sleep(2);}}
                        }
                        else if(type==1){if(offset!=received)throw new IllegalStateException("Benchmark sequence mismatch");received+=packet.remaining();}
                        else if(type==2) {
                            if(received!=TOTAL||offset!=TOTAL)throw new IllegalStateException("Incomplete benchmark");
                            ByteBuffer ack=header(3,round,received);deadline=System.currentTimeMillis()+90_000;
                            while(!messages.send(remote.remoteSteamId(),ack,false,CHANNEL)){SteamGameServerAPI.runCallbacks();Thread.sleep(2);}
                            completed++;
                        }
                    }
                    Thread.sleep(1);
                }
                if(completed!=2)throw new IllegalStateException("Benchmark server timed out");
                Thread.sleep(500);
            }
        } finally {game.logOff();game.dispose();SteamGameServerAPI.shutdown();}
    }
    private static void client(Path shared) throws Exception {
        try(SteamLifecycle lifecycle=new SteamLifecycle(new SteamworksApi())) {
            lifecycle.start();
            SteamUser user=new SteamUser(new SteamUserCallback(){});
            Files.writeString(shared.resolve("client.id"),Long.toUnsignedString(SteamNativeHandle.getNativeHandle(user.getSteamID())));user.dispose();
            var field=SteamLifecycle.class.getDeclaredField("nativeLoader");field.setAccessible(true);
            SteamNativeLibraryLoader loader=(SteamNativeLibraryLoader)field.get(lifecycle);
            long peer=Long.parseUnsignedLong(Files.readString(shared.resolve("server.id")).trim());
            AtomicReference<SteamNetworkingMessagesTransport> ref=new AtomicReference<>();
            try(SteamNetworkingMessagesTransport transport=SteamNetworkingMessagesTransport.open(loader.steamApiPath(),new SteamNetworkingMessagesTransport.SessionListener(){
                public void onSessionRequest(long id){if(id==peer)ref.get().accept(id);}
                public void onSessionFailed(long id,int reason,String detail){System.out.println("BENCH_CLIENT_SESSION_FAILED="+reason);}
            })) {
                ref.set(transport);long[] elapsed=new long[2];
                var accessField=SteamNetworkingMessagesTransport.class.getDeclaredField("nativeAccess");accessField.setAccessible(true);
                SteamNetworkingMessagesTransport.NativeAccess access=(SteamNetworkingMessagesTransport.NativeAccess)accessField.get(transport);
                // Controlled 256 KiB/s baseline, applied in this isolated JVM only.
                access.setGlobalConfigInt32(10,256*1024);access.setGlobalConfigInt32(11,256*1024);
                long warmDeadline=System.currentTimeMillis()+30_000;
                send(transport,lifecycle,peer,header(0,-1,0),warmDeadline,false);
                boolean warm=false;ByteBuffer warmReply=ByteBuffer.allocateDirect(1024);
                while(!warm&&System.currentTimeMillis()<warmDeadline) {
                    lifecycle.runCallbacks();
                    if(transport.availablePacketSize(CHANNEL)>0){warmReply.clear();transport.receive(warmReply,CHANNEL);warmReply.flip();warm=warmReply.getInt()==MAGIC&&warmReply.get()==3&&warmReply.getInt()==-1&&warmReply.getLong()==0;}
                    else Thread.sleep(1);
                }
                if(!warm)throw new IllegalStateException("Benchmark warm-up failed");

                for(int round=0;round<2;round++) {
                    long start=System.nanoTime(),deadline=System.currentTimeMillis()+90_000;
                    if(round==1)transport.beginBulkTransfer(peer,System.currentTimeMillis());
                    send(transport,lifecycle,peer,header(0,round,TOTAL),deadline,round==1);
                    for(long offset=0;offset<TOTAL;) {
                        int n=(int)Math.min(CHUNK,TOTAL-offset);ByteBuffer data=ByteBuffer.allocateDirect(n+17);
                        data.putInt(MAGIC).put((byte)1).putInt(round).putLong(offset);while(data.hasRemaining())data.put((byte)0x42);data.flip();
                        send(transport,lifecycle,peer,data,deadline,round==1);offset+=n;
                    }
                    send(transport,lifecycle,peer,header(2,round,TOTAL),deadline,round==1);
                    boolean done=false;ByteBuffer reply=ByteBuffer.allocateDirect(1024);
                    while(!done&&System.currentTimeMillis()<deadline) {
                        lifecycle.runCallbacks();
                        if(transport.availablePacketSize(CHANNEL)>0) {
                            reply.clear();transport.receive(reply,CHANNEL);reply.flip();
                            done=reply.getInt()==MAGIC&&reply.get()==3&&reply.getInt()==round&&reply.getLong()==TOTAL;
                        } else Thread.sleep(1);
                    }
                    if(!done)throw new IllegalStateException("Benchmark client timed out");
                    elapsed[round]=(System.nanoTime()-start)/1_000_000;
                    System.out.println("NATIVE_BULK_ROUND="+round+" bytes="+TOTAL+" milliseconds="+elapsed[round]+" "+transport.diagnosticState(peer));
                    if(round==1)transport.endBulkTransfer(peer);
                }
                Files.writeString(shared.resolve("result.json"),"{\"controlled_baseline_bytes_per_second\":262144,\"bytes_per_round\":"+TOTAL+",\"baseline_ms\":"+elapsed[0]+",\"bulk_ms\":"+elapsed[1]+"}\n");
            }
        }
    }
    private static ByteBuffer header(int type,int round,long offset){ByteBuffer b=ByteBuffer.allocateDirect(17);b.putInt(MAGIC).put((byte)type).putInt(round).putLong(offset).flip();return b;}
    private static void send(SteamNetworkingMessagesTransport t,SteamLifecycle lifecycle,long peer,ByteBuffer packet,long deadline,boolean bulk) throws Exception {
        for(;;) {
            lifecycle.runCallbacks();if(bulk)t.beginBulkTransfer(peer,System.currentTimeMillis());
            if(!t.isReliableQueueBackpressured(peer,1024*1024,500_000)&&t.send(peer,packet,false,CHANNEL))return;
            if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Benchmark send timeout");Thread.sleep(2);
        }
    }
}
