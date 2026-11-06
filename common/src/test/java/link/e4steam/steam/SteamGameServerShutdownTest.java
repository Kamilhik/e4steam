package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SteamGameServerShutdownTest {
    @Test void concurrentStopsWaitForTheSameCleanup() throws Exception {
        SteamGameServerRuntimeBackend backend = new SteamGameServerRuntimeBackend(null);
        CountDownLatch release = blockControl(backend);
        try {
            CompletableFuture<Void> first = backend.stop(SteamRuntimeBackend.ShutdownReason.NORMAL).toCompletableFuture();
            CompletableFuture<Void> second = backend.stop(SteamRuntimeBackend.ShutdownReason.NORMAL).toCompletableFuture();
            assertFalse(first.isDone());
            assertFalse(second.isDone(), "A second stop must not finish before native cleanup");
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(SteamRuntimeBackend.State.STOPPED, backend.snapshot().state());
        } finally { release.countDown(); }
    }

    @Test void minecraftStoppedWaitsBeforeTheJvmCanExit() throws Exception {
        SteamGameServerRuntimeBackend backend = new SteamGameServerRuntimeBackend(null);
        CountDownLatch release = blockControl(backend);
        backend.stop(SteamRuntimeBackend.ShutdownReason.NORMAL);
        CompletableFuture<Void> caller = CompletableFuture.runAsync(() ->
                backend.stop(SteamRuntimeBackend.ShutdownReason.MINECRAFT_STOPPING));
        try {
            assertThrows(TimeoutException.class, () -> caller.get(150, TimeUnit.MILLISECONDS));
            release.countDown();
            caller.get(5, TimeUnit.SECONDS);
            assertEquals(SteamRuntimeBackend.State.STOPPED, backend.snapshot().state());
        } finally { release.countDown(); }
    }

    private static CountDownLatch blockControl(SteamGameServerRuntimeBackend backend) throws Exception {
        Field field = SteamGameServerRuntimeBackend.class.getDeclaredField("control");
        field.setAccessible(true);
        Executor executor = (Executor) field.get(backend);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        executor.execute(() -> {
            entered.countDown();
            try { release.await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        return release;
    }
}
