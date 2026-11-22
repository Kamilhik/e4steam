package link.e4steam.worldtransfer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Single-transfer state machine. All file/network work is off Minecraft's render thread. */
public final class TransferEngine implements AutoCloseable {
    public enum State { IDLE, LISTENING, OFFERING, OFFERED, SAVING, WAITING, SENDING, RECEIVING, VERIFYING, COMPLETE, FAILED, CANCELLED }
    public interface Transport {
        CompletionStage<Boolean> send(long peer,byte[] packet);
        void release(long peer);
    }
    public interface Source {
        CompletionStage<WorldArchive.Snapshot> saveAndSnapshot();
    }
    public interface PlayerImporter { void prepare(Path world,long previousHost,long newHost) throws IOException; }
    public interface ReceiptWriter { void save(Path world,TransferProtocol.Offer offer,long previousHost,long recipient) throws IOException; }
    public static final class View {
        public final State state;
        public final TransferProtocol.Offer offer;
        public final long peer, done, total;
        public final String error;
        public final Path installed;
        View(State state,TransferProtocol.Offer offer,long peer,long done,long total,String error,Path installed) {
            this.state=state;this.offer=offer;this.peer=peer;this.done=done;this.total=total;this.error=error;this.installed=installed;
        }
    }
    private final ScheduledExecutorService loop=Executors.newSingleThreadScheduledExecutor(r -> daemon(r,"e4steam-world-transfer"));
    private final ExecutorService disk=Executors.newSingleThreadExecutor(r -> daemon(r,"e4steam-world-import"));
    private final Transport transport;
    private final Path saves,cache;
    private final LongSupplier identity;
    private final String game,mods;
    private final PlayerImporter playerImporter;
    private final ReceiptWriter receiptWriter;
    private volatile View view=new View(State.IDLE,null,0,0,0,"",null);
    private volatile long listeningPeer,activePeer,localIdentity;
    private volatile boolean closed,cancelled;
    private long listenUntil,lastReceived,lastPing,lastOffer,terminalAt,offerDeadline,startedAt;
    private State state=State.IDLE;
    private TransferProtocol.Offer offer;
    private Source source;
    private WorldArchive.Snapshot snapshot;
    private InputStream sending;
    private OutputStream receiving;
    private Path incoming,installed;
    private long cursor,acknowledged,total,expanded;
    private String expectedHash="",error="";
    private boolean pendingSend,endSent;
    private long networkStarted;
    private final ArrayDeque<byte[]> outbound=new ArrayDeque<>();
    private volatile long epoch;

    public TransferEngine(Transport transport,Path saves,Path cache,LongSupplier identity,String game,String mods) {
        this(transport,saves,cache,identity,game,mods,(world,from,to)->WorldPlayerData.prepareImportedWorld(world));
    }
    public TransferEngine(Transport transport,Path saves,Path cache,LongSupplier identity,String game,String mods,PlayerImporter playerImporter) {
        this(transport,saves,cache,identity,game,mods,playerImporter,
            (world,offer,from,to)->new TransferReceipt(offer.worldId,offer.origin,offer.generation).save(world));
    }
    public TransferEngine(Transport transport,Path saves,Path cache,LongSupplier identity,String game,String mods,
                          PlayerImporter playerImporter,ReceiptWriter receiptWriter) {
        this(transport,saves,cache,identity,game,mods,playerImporter,receiptWriter,20);
    }
    TransferEngine(Transport transport,Path saves,Path cache,LongSupplier identity,String game,String mods,
                   PlayerImporter playerImporter,ReceiptWriter receiptWriter,long maintenanceMillis) {
        if(maintenanceMillis<1 || maintenanceMillis>5000)throw new IllegalArgumentException("maintenanceMillis");
        this.transport=transport;this.saves=saves;this.cache=cache;this.identity=identity;this.game=game;this.mods=mods;
        this.playerImporter=Objects.requireNonNull(playerImporter);
        this.receiptWriter=Objects.requireNonNull(receiptWriter);
        loop.scheduleWithFixedDelay(() -> guarded(this::tick),0,maintenanceMillis,TimeUnit.MILLISECONDS);
    }
    private static Thread daemon(Runnable action,String name) { Thread t=new Thread(action,name);t.setDaemon(true);return t; }
    public View view() { return view; }
    public boolean accepts(long peer) { return !closed && (peer==activePeer && peer!=0 || peer==listeningPeer && peer!=0); }
    public boolean retains(long peer) { return !closed && peer!=0 && peer==activePeer; }
    public long[] expectedPeers() { return new long[]{activePeer,listeningPeer}; }
    public boolean busy() { State s=view.state;return s!=State.IDLE && s!=State.LISTENING && s!=State.COMPLETE && s!=State.FAILED && s!=State.CANCELLED; }
    public void clear() { execute(() -> { if (!busy()) {reset();publish();} }); }

    public void listen(long peer) { execute(() -> {
        if (busy()) return;
        reset();
        if (!TransferProtocol.validSteam(peer) || peer==identity.getAsLong()) throw new IOException("INVALID_TRANSFER_PEER");
        listeningPeer=peer;listenUntil=System.currentTimeMillis()+120_000;state=State.LISTENING;publish();
    }); }

    public void offer(long peer,TransferProtocol.Offer value,Source source) { execute(() -> {
        if (busy()) return;
        reset();
        if (!TransferProtocol.validSteam(peer) || peer==identity.getAsLong() || !value.gameVersion.equals(game) || !value.mods.equals(mods))
            throw new IOException("INVALID_TRANSFER_PEER");
        this.offer=value;this.source=Objects.requireNonNull(source);activePeer=peer;localIdentity=identity.getAsLong();
        lastReceived=startedAt=lastOffer=System.currentTimeMillis();offerDeadline=startedAt+120_000;
        state=State.OFFERING;queue(TransferProtocol.offer(value));publish();
    }); }

    public void packet(long peer,byte[] packet) {
        if (!accepts(peer) || packet==null || packet.length>TransferProtocol.CHUNK+150) return;
        // A bounded semaphore prevents a hostile peer from filling the executor's work queue.
        if (!packetBudget.tryAcquire()) { disconnected(peer);return; }
        byte[] safe=packet.clone();
        try { loop.execute(() -> { try { guarded(() -> receive(peer,safe)); } finally { packetBudget.release(); } }); }
        catch (RejectedExecutionException stopped) { packetBudget.release(); }
    }
    private final Semaphore packetBudget=new Semaphore(128);
    public void accept() { execute(() -> {
        if (state!=State.OFFERED) return;
        localIdentity=identity.getAsLong();state=State.SAVING;queue(empty(TransferProtocol.ACCEPT));publish();
    }); }
    public void cancel() { execute(() -> terminate(State.CANCELLED,"TRANSFER_CANCELLED",true)); }
    public void disconnected(long peer) { if (peer==activePeer) execute(() -> terminate(State.FAILED,"STEAM_DISCONNECTED",false)); }
    public void stopped() { execute(() -> terminate(State.FAILED,"STEAM_UNAVAILABLE",false)); }

    private void receive(long peer,byte[] bytes) throws Exception {
        if (!accepts(peer)) return;
        if (state==State.FAILED || state==State.CANCELLED) return;
        TransferProtocol.Packet packet=TransferProtocol.decode(bytes);
        if (packet.type==TransferProtocol.OFFER && state==State.LISTENING && peer==listeningPeer) {
            TransferProtocol.Offer proposed=TransferProtocol.offer(packet);
            offer=proposed;activePeer=peer;listeningPeer=0;lastReceived=startedAt=System.currentTimeMillis();offerDeadline=startedAt+120_000;
            localIdentity=identity.getAsLong();
            if (!proposed.gameVersion.equals(game) || !proposed.mods.equals(mods)) {
                queue(empty(TransferProtocol.DECLINE));terminate(State.FAILED,"MODPACK_MISMATCH",false);return;
            }
            state=State.OFFERED;publish();return;
        }
        if (offer==null || peer!=activePeer || !packet.id.equals(offer.id)) return;
        lastReceived=System.currentTimeMillis();
        if (packet.type!=TransferProtocol.OFFER && packet.type!=TransferProtocol.START
                && packet.type!=TransferProtocol.DATA && packet.type!=TransferProtocol.ACK && packet.payload.length!=0)
            throw new IOException("INVALID_TRANSFER_PACKET");
        switch (packet.type) {
            case TransferProtocol.PING: return;
            case TransferProtocol.CANCEL: case TransferProtocol.DECLINE:
                terminate(State.CANCELLED,"PEER_DECLINED",false);return;
            case TransferProtocol.ACCEPT:
                if (state!=State.OFFERING) return;
                state=State.SAVING;publish();
                long token=epoch;
                source.saveAndSnapshot().whenComplete((value,failure) -> {
                    if (closed) { closeQuietly(value);return; }
                    execute(() -> {
                        if (token!=epoch || cancelled || state!=State.SAVING) { closeQuietly(value);return; }
                        if (failure!=null || value==null) { diagnostic(failure);terminate(State.FAILED,"WORLD_SAVE_FAILED",true);return; }
                        snapshot=value;total=value.size;expanded=value.expanded;expectedHash=value.sha256;
                        sending=Files.newInputStream(value.path);
                        ByteArrayOutputStream metadata=new ByteArrayOutputStream();
                        try (DataOutputStream output=new DataOutputStream(metadata)) {
                            output.writeLong(total);output.writeLong(expanded);output.writeUTF(expectedHash);
                        }
                        state=State.WAITING;queue(TransferProtocol.packet(TransferProtocol.START,offer.id,metadata.toByteArray()));publish();
                    });
                });
                return;
            case TransferProtocol.START:
                if (state!=State.SAVING || source!=null || packet.payload.length>128) throw new IOException("UNEXPECTED_TRANSFER_START");
                try (DataInputStream input=new DataInputStream(new ByteArrayInputStream(packet.payload))) {
                    total=input.readLong();expanded=input.readLong();expectedHash=input.readUTF();
                    if (input.available()!=0 || total<1 || total>WorldArchive.MAX_ARCHIVE || expanded<1
                            || expanded>WorldArchive.MAX_EXPANDED || !expectedHash.matches("[0-9a-f]{64}")) throw new IOException("WORLD_TOO_LARGE");
                }
                Files.createDirectories(cache);Files.createDirectories(saves);
                FileStore cacheStore=Files.getFileStore(cache),saveStore=Files.getFileStore(saves);
                long cacheNeeded=total+128L*1024*1024+(cacheStore.equals(saveStore)?expanded:0);
                if (Files.isSymbolicLink(cache) || Files.isSymbolicLink(saves)
                        || cacheStore.getUsableSpace()<cacheNeeded || saveStore.getUsableSpace()<expanded+128L*1024*1024)
                    throw new IOException("NOT_ENOUGH_SPACE");
                incoming=Files.createTempFile(cache,"incoming-", ".zip.part");
                receiving=new BufferedOutputStream(Files.newOutputStream(incoming));
                state=State.RECEIVING;queue(empty(TransferProtocol.READY));publish();return;
            case TransferProtocol.READY:
                if (state==State.WAITING && source!=null) { state=State.SENDING;networkStarted=System.currentTimeMillis();
                    org.apache.logging.log4j.LogManager.getLogger("e4steam").info("World handoff: sending {} bytes",total);pump();publish(); } return;
            case TransferProtocol.DATA:
                if (state!=State.RECEIVING || receiving==null) throw new IOException("UNEXPECTED_TRANSFER_DATA");
                long offset=TransferProtocol.dataOffset(packet);
                int count=packet.payload.length-8;
                if (offset!=cursor || count>total-cursor) throw new IOException("TRANSFER_OFFSET_MISMATCH");
                receiving.write(packet.payload,8,count);cursor+=count;
                queueAcknowledgement();publish();return;
            case TransferProtocol.ACK:
                if (state!=State.SENDING) return;
                long ack=TransferProtocol.offset(packet);
                if (ack>cursor) throw new IOException("TRANSFER_OFFSET_MISMATCH");
                if(ack==total && acknowledged<total)org.apache.logging.log4j.LogManager.getLogger("e4steam").info(
                    "World handoff: network acknowledged {} bytes in {} ms",total,System.currentTimeMillis()-networkStarted);
                acknowledged=Math.max(acknowledged,ack);pump();publish();return;
            case TransferProtocol.END:
                if (state!=State.RECEIVING || cursor!=total) throw new IOException("TRANSFER_INCOMPLETE");
                receiving.close();receiving=null;state=State.VERIFYING;publish();
                long installationEpoch=epoch;
                Path archive=incoming;
                TransferProtocol.Offer acceptedOffer=offer;
                long acceptedExpanded=expanded;
                String acceptedHash=expectedHash;
                long previousHost=peer,newHost=localIdentity;
                long importStarted=System.currentTimeMillis();
                org.apache.logging.log4j.LogManager.getLogger("e4steam").info("World handoff: received data; verifying and installing {} bytes",total);
                disk.submit(() -> {
                    Path result=null;Throwable failed=null;
                    try {
                        result=WorldArchive.install(archive,saves,acceptedOffer.id,acceptedExpanded,acceptedHash,
                            () -> closed || cancelled || installationEpoch!=epoch,path -> {
                                playerImporter.prepare(path,previousHost,newHost);
                                receiptWriter.save(path,acceptedOffer,previousHost,newHost);
                            });
                    } catch (Throwable failure) { failed=failure; }
                    finally { try { Files.deleteIfExists(archive); } catch (IOException ignored) { } }
                    Path committed=result;Throwable failure=failed;
                    execute(() -> {
                        if (installationEpoch!=epoch) return;
                        if (failure!=null) { diagnostic(failure);terminate(State.FAILED,"WORLD_IMPORT_FAILED",true);return; }
                        org.apache.logging.log4j.LogManager.getLogger("e4steam").info("World handoff: archive installation completed in {} ms",System.currentTimeMillis()-importStarted);
                        installed=committed;state=State.COMPLETE;terminalAt=System.currentTimeMillis();
                        queue(empty(TransferProtocol.COMPLETE));cleanupFiles();publish();
                    });
                });return;
            case TransferProtocol.COMPLETE:
                if (state==State.SENDING && acknowledged==total && endSent) {
                    state=State.COMPLETE;terminalAt=System.currentTimeMillis();cleanupFiles();publish();
                }
                return;
            default: return;
        }
    }

    private void pump() throws IOException {
        if (state!=State.SENDING) return;
        while (cursor<total && cursor-acknowledged<(long)TransferProtocol.CHUNK*TransferProtocol.WINDOW && outbound.size()<20) {
            byte[] buffer=new byte[(int)Math.min(TransferProtocol.CHUNK,total-cursor)];
            int count=sending.read(buffer);
            if (count<1) throw new IOException("ARCHIVE_TRUNCATED");
            queue(TransferProtocol.data(offer.id,cursor,buffer,count));cursor+=count;
        }
        if (acknowledged==total && !endSent) { endSent=true;queue(empty(TransferProtocol.END)); }
    }

    private void tick() throws Exception {
        if (closed) return;
        long now=System.currentTimeMillis();
        if (state==State.LISTENING && now>listenUntil) { listeningPeer=0;state=State.IDLE;publish(); }
        if (activePeer!=0 && localIdentity!=0 && identity.getAsLong()!=localIdentity) terminate(State.FAILED,"STEAM_ACCOUNT_CHANGED",true);
        if (busy() && now-lastReceived>120_000) terminate(State.FAILED,"TRANSFER_TIMEOUT",true);
        if ((state==State.OFFERING || state==State.OFFERED) && now>offerDeadline
                || busy() && startedAt>0 && now-startedAt>3*60*60*1000L) terminate(State.FAILED,"TRANSFER_TIMEOUT",true);
        if (activePeer!=0 && busy() && now-lastPing>5000 && outbound.size()<20) { queue(empty(TransferProtocol.PING));lastPing=now; }
        // The receiver may still be fingerprinting its modpack when the first offer arrives.
        if (state==State.OFFERING && now-lastOffer>=3000 && outbound.size()<20) {
            queue(TransferProtocol.offer(offer));lastOffer=now;
        }
        if (state==State.SENDING) pump();
        flush();
        long grace=state==State.COMPLETE && installed!=null?15_000:3000;
        if (!busy() && activePeer!=0 && terminalAt>0 && now-terminalAt>grace && !pendingSend) {
            long peer=activePeer;activePeer=0;outbound.clear();transport.release(peer);
        }
    }

    private void flush() {
        if (pendingSend || outbound.isEmpty() || activePeer==0) return;
        byte[] next=outbound.peekFirst();
        pendingSend=true;
        long token=epoch;
        withSendTimeout(transport.send(activePeer,next)).whenComplete((sent,failure) -> execute(() -> {
            if (token!=epoch) return;
            pendingSend=false;
            if (failure!=null) { terminate(State.FAILED,"STEAM_UNAVAILABLE",false);outbound.clear();return; }
            if (Boolean.TRUE.equals(sent)) {
                if(outbound.peekFirst()==next)outbound.removeFirst();
                if(state==State.SENDING)pump();flush();
            }
        }));
    }

    private CompletableFuture<Boolean> withSendTimeout(CompletionStage<Boolean> sending) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        ScheduledFuture<?> deadline = loop.schedule(() -> result.completeExceptionally(new TimeoutException("TRANSFER_SEND_TIMEOUT")),10,TimeUnit.SECONDS);
        sending.whenComplete((sent,failure) -> {
            deadline.cancel(false);
            if (failure != null) result.completeExceptionally(failure);
            else result.complete(sent);
        });
        return result;
    }

    private byte[] empty(int type) throws IOException { return TransferProtocol.packet(type,offer.id,new byte[0]); }
    private void queue(byte[] packet) throws IOException {
        if (outbound.size()>=32) throw new IOException("TRANSFER_QUEUE_FULL");
        outbound.addLast(packet);flush();
    }
    private void queueAcknowledgement() throws IOException {
        boolean first=true;
        for(Iterator<byte[]> it=outbound.iterator();it.hasNext();) {
            byte[] queued=it.next();boolean inFlight=first&&pendingSend;first=false;
            if(!inFlight && TransferProtocol.decode(queued).type==TransferProtocol.ACK)it.remove();
        }
        queue(TransferProtocol.offset(TransferProtocol.ACK,offer.id,cursor));
    }
    private void terminate(State terminal,String reason,boolean notify) throws IOException {
        if (state==State.COMPLETE) return; // A successful import remains successful even if its acknowledgment is lost.
        if (state==State.FAILED || state==State.CANCELLED) return;
        cancelled=true;state=terminal;error=reason;terminalAt=System.currentTimeMillis();listeningPeer=0;
        org.apache.logging.log4j.LogManager.getLogger("e4steam").warn("World transfer stopped: {}",reason);
        outbound.clear();
        if (notify && offer!=null && activePeer!=0) queue(empty(TransferProtocol.CANCEL));
        cleanupFiles();publish();
    }
    private void cleanupFiles() {
        closeQuietly(sending);sending=null;closeQuietly(receiving);receiving=null;
        closeQuietly(snapshot);snapshot=null;
        if (incoming!=null && state!=State.VERIFYING) { try { Files.deleteIfExists(incoming);incoming=null; } catch (IOException ignored) { } }
    }
    private void reset() {
        epoch++;cancelled=true;cleanupFiles();
        if (activePeer!=0) { long peer=activePeer;activePeer=0;transport.release(peer); }
        listeningPeer=0;offer=null;source=null;snapshot=null;installed=null;error="";
        cursor=acknowledged=total=expanded=terminalAt=0;endSent=pendingSend=false;outbound.clear();state=State.IDLE;cancelled=false;
    }
    private void publish() { view=new View(state,offer,activePeer!=0?activePeer:listeningPeer,source!=null?acknowledged:cursor,total,error,installed); }
    private void execute(Action action) {
        if (closed) return;
        try { loop.execute(() -> guarded(action)); } catch (RejectedExecutionException stopped) { }
    }
    private void guarded(Action action) {
        try { action.run(); }
        catch (Exception failure) {
            diagnostic(failure);
            String code=failure.getMessage();
            if (code==null || !code.matches("[A-Z_]{3,64}")) code="TRANSFER_FAILED";
            try { terminate(State.FAILED,code,true); } catch (IOException ignored) { }
        }
    }
    private static void diagnostic(Throwable failure) {
        if (failure==null) return;
        while ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause()!=null) failure=failure.getCause();
        String code=failure.getMessage();
        if (code==null || !code.matches("[A-Z_]{3,64}")) code="DETAILS_REDACTED";
        org.apache.logging.log4j.LogManager.getLogger("e4steam").warn("World transfer failure: {} ({})",code,failure.getClass().getSimpleName());
    }
    private static void closeQuietly(AutoCloseable value) { if (value!=null) try { value.close(); } catch (Exception ignored) { } }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    @Override public void close() {
        if (closed) return;
        cancelled=true;closed=true;loop.shutdownNow();disk.shutdownNow();cleanupFiles();
        if (activePeer!=0) transport.release(activePeer);
    }
}
