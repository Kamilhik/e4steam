package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamConnectionBridgeTest {
    @Test
    void gracefulCloseDeadlineExpiresOnlyAtTheDeadline() {
        assertFalse(SteamConnectionBridge.isGracefulCloseExpired(Long.MAX_VALUE, 100));
        assertFalse(SteamConnectionBridge.isGracefulCloseExpired(101, 100));
        assertTrue(SteamConnectionBridge.isGracefulCloseExpired(100, 100));
    }

    @Test
    void forgeDataWaitsForReadyButHasABoundedCompatibilityFallback() {
        assertTrue(SteamConnectionBridge.isPeerReadyOrFallbackReached(0, false, 1));
        assertFalse(SteamConnectionBridge.isPeerReadyOrFallbackReached(200, false, 199));
        assertTrue(SteamConnectionBridge.isPeerReadyOrFallbackReached(200, true, 199));
        assertTrue(SteamConnectionBridge.isPeerReadyOrFallbackReached(200, false, 200));
    }

    @Test
    void inboundMinecraftBytesWaitForRequiredAddonNegotiation() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        try (ServerSocket listener = new ServerSocket(0);
             Socket client = new Socket("127.0.0.1", listener.getLocalPort());
             Socket local = listener.accept()) {
            client.setSoTimeout(100);
            SteamConnectionBridge bridge = new SteamConnectionBridge(
                    runtime, 77L, 42, local, true, null);
            runtime.bridge = bridge;
            bridge.requireAddonNegotiation();
            bridge.start();

            byte[] payload = new byte[]{4, 8, 15, 16, 23, 42};
            bridge.acceptSteamData(payload);
            assertThrows(SocketTimeoutException.class, () -> client.getInputStream().read());

            client.setSoTimeout(2_000);
            bridge.markAddonNegotiated();
            byte[] received = new byte[payload.length];
            int offset = 0;
            while (offset < received.length) {
                int read = client.getInputStream().read(received, offset, received.length - offset);
                assertTrue(read > 0);
                offset += read;
            }
            assertArrayEquals(payload, received);
            bridge.close(false);
            assertTrue(runtime.unregistered);
        }
    }

    @Test
    void dedicatedGenerationCanOnlyBeBoundOnce() {
        SteamConnectionBridge bridge = new SteamConnectionBridge(
                new FakeRuntime(), 78L, 43, new Socket(), true, null);
        bridge.dedicatedSessionGeneration(99L);
        assertTrue(bridge.dedicatedSessionGeneration() == 99L);
        assertThrows(IllegalStateException.class,
                () -> bridge.dedicatedSessionGeneration(100L));
        bridge.close(false);
    }

    private static final class FakeRuntime implements SteamBridgeRuntime {
        private SteamConnectionBridge bridge;
        private volatile boolean unregistered;
        @Override public boolean sendData(SteamConnectionBridge bridge, byte[] payload) {
            return true;
        }
        @Override public boolean sendFin(SteamConnectionBridge bridge) { return true; }
        @Override public boolean sendReset(SteamConnectionBridge bridge) { return false; }
        @Override public void closeUdpBridge(SteamConnectionBridge bridge) { }
        @Override public void unregister(SteamConnectionBridge candidate) {
            if (bridge == null || bridge == candidate) unregistered = true;
        }
    }
}
