package link.e4steam.internal.dedicated;

import link.e4steam.api.dedicated.DedicatedServerService.DedicatedAccessMode;
import link.e4steam.api.dedicated.DedicatedServerService.DedicatedServerState;
import link.e4steam.steam.SteamRuntimeBackend;
import org.junit.jupiter.api.Test;

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
        FakeBackend[] backend = new FakeBackend[1];
        DedicatedServerController controller = new DedicatedServerController(config(), listener -> {
            backend[0] = new FakeBackend(listener);
            return backend[0];
        });

        controller.minecraftListening(InetAddress.getByName("127.0.0.1"), 25565);
        assertFalse(controller.accepting());
        controller.minecraftReady();

        assertTrue(controller.accepting());
        assertTrue(controller.requiresAuthenticatedIngress());
        assertEquals(0L, controller.authenticatedMinecraftPeer(
                new InetSocketAddress("127.0.0.1", 41000)));
        assertEquals(DedicatedServerState.ACCEPTING,
                controller.service().snapshot().value().orElseThrow().state());
        assertEquals("d-dc-25.steam", controller.descriptor());

        controller.minecraftStopped();
        assertTrue(backend[0].stopped.get());
        assertFalse(controller.accepting());
        assertEquals(DedicatedServerState.STOPPED,
                controller.service().snapshot().value().orElseThrow().state());
    }

    @Test
    void transportCallbackCannotPublishBeforeBackendIdentityIsReady() throws Exception {
        DeferredBackend[] backend = new DeferredBackend[1];
        DedicatedServerController controller = new DedicatedServerController(config(), listener -> {
            backend[0] = new DeferredBackend(listener);
            return backend[0];
        });

        controller.minecraftReady();
        controller.minecraftListening(InetAddress.getByName("127.0.0.1"), 25565);

        assertFalse(controller.accepting());
        assertEquals("", controller.descriptor());

        backend[0].completeReady();

        assertTrue(controller.accepting());
        assertEquals("d-dc-25.steam", controller.descriptor());
        controller.minecraftStopped();
    }

    private static DedicatedRuntimeConfig config() {
        return new DedicatedRuntimeConfig(
                true, DedicatedAccessMode.UNLISTED, 8, 65535, "test server");
    }

    private static final class FakeBackend implements SteamRuntimeBackend {
        private final StateListener listener;
        private final AtomicBoolean stopped = new AtomicBoolean();
        private volatile State state = State.OFF;

        private FakeBackend(StateListener listener) { this.listener = listener; }

        @Override public RuntimeKind kind() { return RuntimeKind.DEDICATED_GAME_SERVER; }

        @Override public CompletionStage<RuntimeReady> start(Config config) {
            transition(State.CONFIG_VALIDATED);
            transition(State.NATIVES_READY);
            transition(State.STEAM_INITIALIZING);
            transition(State.STEAM_LOGGING_ON);
            transition(State.TRANSPORT_READY);
            return CompletableFuture.completedFuture(new RuntimeReady(77L, 480L));
        }

        @Override public Snapshot snapshot() { return new Snapshot(state, 77L, ""); }

        @Override public CompletionStage<Void> stop(ShutdownReason reason) {
            stopped.set(true);
            transition(State.DRAINING);
            transition(State.STOPPED);
            return CompletableFuture.completedFuture(null);
        }

        private void transition(State next) {
            state = next;
            listener.onState(next, "");
        }
    }

    private static final class DeferredBackend implements SteamRuntimeBackend {
        private final StateListener listener;
        private final CompletableFuture<RuntimeReady> readiness = new CompletableFuture<>();
        private volatile State state = State.OFF;

        private DeferredBackend(StateListener listener) { this.listener = listener; }

        @Override public RuntimeKind kind() { return RuntimeKind.DEDICATED_GAME_SERVER; }

        @Override public CompletionStage<RuntimeReady> start(Config config) {
            transition(State.CONFIG_VALIDATED);
            transition(State.NATIVES_READY);
            transition(State.STEAM_INITIALIZING);
            transition(State.STEAM_LOGGING_ON);
            transition(State.TRANSPORT_READY);
            return readiness;
        }

        @Override public Snapshot snapshot() { return new Snapshot(state, 77L, ""); }

        @Override public CompletionStage<Void> stop(ShutdownReason reason) {
            transition(State.DRAINING);
            transition(State.STOPPED);
            return CompletableFuture.completedFuture(null);
        }

        private void completeReady() { readiness.complete(new RuntimeReady(77L, 480L)); }

        private void transition(State next) {
            state = next;
            listener.onState(next, "");
        }
    }
}
