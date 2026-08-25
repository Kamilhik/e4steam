package link.e4steam.internal.dedicated;

import link.e4steam.api.dedicated.DedicatedServerService.DedicatedAccessMode;
import link.e4steam.api.dedicated.DedicatedServerService.DedicatedServerState;
import link.e4steam.steam.SteamGameServerRuntimeBackend;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedicatedServerControllerTest {
    @Test
    void readinessNeedsBothMinecraftSignalsAndIngressRemainsFailClosed() throws Exception {
        FakeBackend backend = new FakeBackend();
        DedicatedServerController controller = new DedicatedServerController(config(), backend);

        controller.minecraftListening(InetAddress.getByName("127.0.0.1"), 25565);
        controller.backendState(SteamGameServerRuntimeBackend.State.NATIVES_READY, "");
        controller.backendState(SteamGameServerRuntimeBackend.State.STEAM_INITIALIZING, "");
        controller.backendState(SteamGameServerRuntimeBackend.State.STEAM_LOGGING_ON, "");
        controller.backendState(SteamGameServerRuntimeBackend.State.TRANSPORT_READY, "");
        assertFalse(controller.accepting());
        controller.minecraftReady();

        assertTrue(controller.accepting());
        assertTrue(controller.requiresAuthenticatedIngress());
        assertEquals(0L, controller.authenticatedMinecraftPeer(
                new InetSocketAddress("127.0.0.1", 41000)));
        assertEquals(DedicatedServerState.ACCEPTING,
                controller.service().snapshot().value().orElseThrow().state());

        controller.minecraftStopped();
        assertTrue(backend.stopped.get());
        assertFalse(controller.accepting());
        assertEquals(DedicatedServerState.STOPPED,
                controller.service().snapshot().value().orElseThrow().state());
    }

    private static DedicatedRuntimeConfig config() {
        return new DedicatedRuntimeConfig(
                true, DedicatedAccessMode.UNLISTED, 8, 65535, "test server");
    }

    private static final class FakeBackend extends SteamGameServerRuntimeBackend {
        private final AtomicBoolean stopped = new AtomicBoolean();
        private volatile State state = State.OFF;

        private FakeBackend() { super(null); }

        @Override public CompletionStage<RuntimeReady> start(Config config) {
            state = State.TRANSPORT_READY;
            return CompletableFuture.completedFuture(new RuntimeReady(77L, 480L));
        }

        @Override public Snapshot snapshot() { return new Snapshot(state, 77L, ""); }

        @Override public CompletionStage<Void> stop(ShutdownReason reason) {
            stopped.set(true);
            state = State.STOPPED;
            return CompletableFuture.completedFuture(null);
        }

        @Override public int availablePacketSize() throws IOException { return 0; }
    }
}