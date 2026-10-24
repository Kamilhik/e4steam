package link.e4steam.lan;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;

/** Small server-thread mailbox for old integrated servers without an executor API. */
public final class LanServerTasks {
    private static final ArrayBlockingQueue<Request> PENDING = new ArrayBlockingQueue<>(8);
    private LanServerTasks() { }

    public static CompletableFuture<Boolean> apply(Object server, LanSettings.Snapshot settings) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (!PENDING.offer(new Request(server, settings.validated(), result)))
            result.completeExceptionally(new IllegalStateException("LAN settings queue is full"));
        return result;
    }

    public static void tick(Object server) {
        Request request;
        while ((request = PENDING.poll()) != null) {
            if (request.server != server) {
                request.result.completeExceptionally(new IllegalStateException("The integrated world changed"));
                continue;
            }
            try {
                boolean open = LanServerSettingsApplier.published(server);
                LanSettings.install(request.settings);
                LanServerSettingsApplier.apply(server, request.settings);
                boolean success = open || LanServerSettingsApplier.publish(server, request.settings);
                if (success) LanServerSettingsApplier.apply(server, request.settings);
                request.result.complete(success);
            } catch (RuntimeException failure) { request.result.completeExceptionally(failure); }
        }
    }

    private static final class Request {
        final Object server;
        final LanSettings.Snapshot settings;
        final CompletableFuture<Boolean> result;
        Request(Object server, LanSettings.Snapshot settings, CompletableFuture<Boolean> result) {
            this.server = server; this.settings = settings; this.result = result;
        }
    }
}
