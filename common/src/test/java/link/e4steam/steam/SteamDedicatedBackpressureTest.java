package link.e4steam.steam;

import com.sun.jna.Pointer;
import link.e4steam.api.dedicated.DedicatedServerService.DedicatedAccessMode;
import link.e4steam.internal.dedicated.DedicatedRuntimeConfig;
import link.e4steam.internal.dedicated.DedicatedServerController;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SteamDedicatedBackpressureTest {
    @SuppressWarnings("unchecked")
    @Test void fullNativeSendBufferRetainsPacketAndOrderUntilItCanBeSent() throws Exception {
        List<Integer> attempts = new ArrayList<>();
        int[] calls = {0};
        int[] forcedResult = {0};
        int[] closes = {0};
        SteamNetworkingSocketsP2PTransport.FlatApi api =
                (SteamNetworkingSocketsP2PTransport.FlatApi) Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[]{SteamNetworkingSocketsP2PTransport.FlatApi.class},
                        (proxy, method, args) -> {
                            String name = method.getName();
                            if (name.endsWith("_ConnectP2P")) return 42;
                            if (name.endsWith("_SendMessageToConnection")) {
                                attempts.add((int) ((Pointer) args[2]).getByte(0));
                                return forcedResult[0] != 0 ? forcedResult[0]
                                        : calls[0]++ == 0 ? 25 : 1; // LimitExceeded, then OK.
                            }
                            if (name.endsWith("_CloseConnection")) closes[0]++;
                            if (method.getReturnType() == Pointer.class) return new Pointer(1);
                            if (method.getReturnType() == byte.class) return (byte) 1;
                            if (method.getReturnType() == int.class) return 1;
                            return null;
                        });
        SteamNetworkingSocketsP2PTransport.SessionListener listener =
                new SteamNetworkingSocketsP2PTransport.SessionListener() {
                    public void onSessionRequest(long peer) { }
                    public void onSessionFailed(long peer, int reason, String detail) { }
                };
        try (SteamNetworkingSocketsP2PTransport nativeTransport =
                     new SteamNetworkingSocketsP2PTransport(api, false, 123L, listener)) {
            SteamGameServerRuntimeBackend backend = new SteamGameServerRuntimeBackend(null);
            field(backend, "transport").set(backend, nativeTransport);
            ((AtomicReference<SteamRuntimeBackend.State>) field(backend, "state").get(backend))
                    .set(SteamRuntimeBackend.State.TRANSPORT_READY);
            DedicatedServerController controller = new DedicatedServerController(
                    new DedicatedRuntimeConfig(false, DedicatedAccessMode.UNLISTED, 8, 65535, "test"));
            SteamDedicatedServerTransport transport = new SteamDedicatedServerTransport(backend, controller, 8);
            SteamOutboundQueue<SteamConnectionBridge> queue =
                    (SteamOutboundQueue<SteamConnectionBridge>) field(transport, "outbound").get(transport);
            assertTrue(queue.offerData(123L, 1, new byte[]{11}, null));
            assertTrue(queue.offerData(123L, 1, new byte[]{22}, null));
            Method drain = transport.getClass().getDeclaredMethod("drainOutbound");
            drain.setAccessible(true);
            drain.invoke(transport);
            assertEquals(List.of(11), attempts);
            assertNotNull(field(transport, "deferredPacket").get(transport));
            field(transport, "sendRetryNotBeforeMillis").setLong(transport, 0L);
            drain.invoke(transport);
            assertEquals(List.of(11, 11, 22), attempts);
            assertNull(field(transport, "deferredPacket").get(transport));
            assertEquals(0L, field(transport, "sendRetryDeadlineMillis").getLong(transport));
            assertEquals(0, closes[0]);
            // A peer that never drains must still be closed after the bounded retry period.
            forcedResult[0] = 25;
            assertTrue(queue.offerData(123L, 1, new byte[]{33}, null));
            drain.invoke(transport);
            assertNotNull(field(transport, "deferredPacket").get(transport));
            field(transport, "sendRetryNotBeforeMillis").setLong(transport, 0L);
            field(transport, "sendRetryDeadlineMillis").setLong(transport, 1L);
            drain.invoke(transport);
            assertNull(field(transport, "deferredPacket").get(transport));
            assertEquals(1, closes[0]);
        }
    }

    private static Field field(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
