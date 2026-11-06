package link.e4steam.steam;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SteamDedicatedReconnectTest {
    private static final long PEER = 123L;
    private static final class Listener implements SteamNetworkingSocketsP2PTransport.SessionListener {
        int failures;
        public void onSessionRequest(long peer) { }
        public void onSessionFailed(long peer, int reason, String detail) { failures++; }
    }
    private static final class NativeBoundary {
        SteamNetworkingSocketsP2PTransport.ConnectionStatusCallback callback;
        final Map<Integer,Integer> states = new HashMap<>();
        int readResult, readCalls, sentConnection;
        final SteamNetworkingSocketsP2PTransport.FlatApi api = (SteamNetworkingSocketsP2PTransport.FlatApi)
                Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{SteamNetworkingSocketsP2PTransport.FlatApi.class},
                        (proxy,method,args) -> {
                            String name=method.getName();
                            if(name.equals("SteamAPI_ISteamNetworkingUtils_SetGlobalCallback_SteamNetConnectionStatusChanged")) {
                                callback=(SteamNetworkingSocketsP2PTransport.ConnectionStatusCallback)args[1]; return null;
                            }
                            if(name.equals("SteamAPI_ISteamNetworkingSockets_ConnectP2P")) { states.put(42,1); return 42; }
                            if(name.equals("SteamAPI_ISteamNetworkingSockets_CreatePollGroup")) return 77;
                            if(name.equals("SteamAPI_ISteamNetworkingSockets_CreateListenSocketP2P")) return 88;
                            if(name.equals("SteamAPI_ISteamNetworkingSockets_GetConnectionInfo")) {
                                Pointer info=(Pointer)args[2];
                                info.setInt(0,16); info.setInt(4,8); info.setLong(8,PEER);
                                info.setInt(176,states.getOrDefault((Integer)args[1],4));
                                info.setInt(180,1000); return (byte)1;
                            }
                            if(name.equals("SteamAPI_ISteamNetworkingSockets_ReceiveMessagesOnConnection")
                                    || name.equals("SteamAPI_ISteamNetworkingSockets_ReceiveMessagesOnPollGroup")) {
                                readCalls++; return readResult;
                            }
                            if(name.equals("SteamAPI_ISteamNetworkingSockets_SendMessageToConnection")) sentConnection=(Integer)args[1];
                            if(method.getReturnType()==Pointer.class) return new Pointer(1);
                            if(method.getReturnType()==byte.class) return (byte)1;
                            if(method.getReturnType()==int.class) return 1;
                            return null;
                        });
        void event(int connection,int state) {
            states.put(connection,state);
            // Older supported Minecraft lines bundle JNA before Memory was AutoCloseable.
            Memory event=new Memory(8);
            event.clear(); event.setInt(0,connection); callback.invoke(event);
        }
    }
    private static SteamNetworkingSocketsP2PTransport client(NativeBoundary api,Listener listener) throws IOException {
        return new SteamNetworkingSocketsP2PTransport(api.api,false,PEER,listener);
    }

    @Test void intentionalDisconnectNeverPollsTheZeroHandle() throws Exception {
        var api=new NativeBoundary(); var listener=new Listener();
        try(var transport=client(api,listener)) {
            transport.closePeer(PEER);
            assertEquals(0,transport.availablePacketSize());
            assertEquals(0,api.readCalls); assertEquals(0,listener.failures);
        }
    }
    @Test void peerCloseReportsOneFailureAndKeepsReceiveLoopUsable() throws Exception {
        var api=new NativeBoundary(); var listener=new Listener();
        try(var transport=client(api,listener)) {
            api.event(42,4);
            assertEquals(1,listener.failures);
            assertEquals(0,transport.availablePacketSize());
            api.event(42,4);
            assertEquals(1,listener.failures); assertEquals(0,api.readCalls);
        }
    }
    @Test void invalidatedLiveHandleRetiresOnlyThatConnection() throws Exception {
        var api=new NativeBoundary(); var listener=new Listener(); api.readResult=-1;
        try(var transport=client(api,listener)) {
            assertEquals(0,transport.availablePacketSize());
            assertEquals(1,listener.failures);
            assertEquals(0,transport.availablePacketSize());
            assertEquals(1,api.readCalls);
        }
    }
    @Test void corruptNativeCountsStillFailClosed() throws Exception {
        var api=new NativeBoundary(); api.readResult=33;
        try(var transport=client(api,new Listener())) { assertThrows(IOException.class,transport::availablePacketSize); }
        api=new NativeBoundary(); api.readResult=-1;
        try(var transport=new SteamNetworkingSocketsP2PTransport(api.api,true,0,new Listener())) {
            assertThrows(IOException.class,transport::availablePacketSize);
        }
    }
    @Test void staleClientCallbackCannotCloseTheReplacement() throws Exception {
        var api=new NativeBoundary(); var listener=new Listener();
        try(var transport=client(api,listener)) {
            api.event(99,4);
            assertEquals(0,listener.failures);
            assertTrue(transport.send(PEER,ByteBuffer.allocateDirect(1),false));
            assertEquals(42,api.sentConnection);
        }
    }
    @Test void retiredServerHandleCannotEndTheNewPeersAuthentication() throws Exception {
        var api=new NativeBoundary(); var listener=new Listener();
        try(var transport=new SteamNetworkingSocketsP2PTransport(api.api,true,0,listener)) {
            api.event(100,1); assertTrue(transport.accept(PEER));
            transport.closePeer(PEER);
            api.event(101,1); assertTrue(transport.accept(PEER));
            api.event(100,4);
            assertEquals(0,listener.failures);
            assertTrue(transport.send(PEER,ByteBuffer.allocateDirect(1),false));
            assertEquals(101,api.sentConnection);
        }
    }
    @Test void repeatedConnectionsCanCloseAndReopenOnTheSameNativeBoundary() throws Exception {
        var api=new NativeBoundary();var listener=new Listener();
        for(int attempt=0;attempt<5;attempt++) {
            try(var transport=client(api,listener)) {
                assertTrue(transport.send(PEER,ByteBuffer.allocateDirect(1),false));
                api.event(42,4);
                assertEquals(0,transport.availablePacketSize());
            }
        }
        assertEquals(5,listener.failures); assertEquals(0,api.readCalls);
    }
}
