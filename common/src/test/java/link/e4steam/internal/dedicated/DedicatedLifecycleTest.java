package link.e4steam.internal.dedicated;

import link.e4steam.api.dedicated.DedicatedServerService.DedicatedAccessMode;
import link.e4steam.api.dedicated.DedicatedServerService.DedicatedServerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedicatedLifecycleTest {
    @Test
    void reachesReadinessOnlyAfterGuardedMinecraftIngress() {
        DedicatedLifecycle lifecycle = lifecycle();
        lifecycle.begin(7L);
        lifecycle.transition(DedicatedServerState.NATIVES_READY);
        lifecycle.transition(DedicatedServerState.STEAM_INITIALIZING);
        lifecycle.transition(DedicatedServerState.STEAM_LOGGING_ON);
        lifecycle.transition(DedicatedServerState.TRANSPORT_READY);
        assertTrue(!lifecycle.readiness().toCompletableFuture().isDone());
        lifecycle.accepting(true);
        assertEquals(DedicatedServerState.ACCEPTING, lifecycle.snapshot().state());
        assertTrue(lifecycle.snapshot().ingressGuarded());
        assertTrue(lifecycle.readiness().toCompletableFuture().isDone());
    }

    @Test
    void rejectsInvalidTransitionAndRepeatedGeneration() {
        DedicatedLifecycle lifecycle = lifecycle();
        lifecycle.begin(1L);
        assertThrows(IllegalStateException.class,
                () -> lifecycle.transition(DedicatedServerState.ACCEPTING));
        assertThrows(IllegalStateException.class, () -> lifecycle.begin(2L));
    }

    @Test
    void failureIsSanitizedAndTerminal() {
        DedicatedLifecycle lifecycle = lifecycle();
        lifecycle.begin(1L);
        lifecycle.fail("NATIVE_BINDING_FAILED");
        assertEquals(DedicatedServerState.FAILED, lifecycle.snapshot().state());
        assertEquals("NATIVE_BINDING_FAILED", lifecycle.snapshot().failureCategory());
    }

    private static DedicatedLifecycle lifecycle() {
        return new DedicatedLifecycle(new DedicatedRuntimeConfig(
                true, DedicatedAccessMode.PRIVATE, 8, 65535, "server"));
    }
}
