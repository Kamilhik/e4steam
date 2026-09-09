package link.e4steam.steam;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Loader-neutral boundary between the Steam transport and optional addon
 * access modes. Retro artifacts keep the fail-closed default; the modern
 * addon runtime installs its provider during bootstrap.
 */
public final class SteamCustomAccessGate {
    private static final Provider DENY_ALL = new Provider() {
        @Override public boolean isRegistered(String modeId) { return false; }

        @Override public CompletionStage<Boolean> evaluate(String modeId, long remoteSteamId) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
    };

    private static final Object LOCK = new Object();
    private static volatile Provider provider = DENY_ALL;

    private SteamCustomAccessGate() { }

    /** Installs the process-wide provider and returns an identity-safe removal handle. */
    public static AutoCloseable install(Provider replacement) {
        if (replacement == null) throw new NullPointerException("replacement");
        synchronized (LOCK) {
            if (provider != DENY_ALL) {
                throw new IllegalStateException("Custom access provider is already installed");
            }
            provider = replacement;
        }
        return new AutoCloseable() {
            @Override public void close() {
                synchronized (LOCK) {
                    if (provider == replacement) provider = DENY_ALL;
                }
            }
        };
    }

    public static boolean isRegistered(String modeId) {
        if (!validModeId(modeId)) return false;
        try {
            return provider.isRegistered(modeId);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static CompletionStage<Boolean> evaluate(String modeId, long remoteSteamId) {
        if (!validModeId(modeId) || remoteSteamId == 0L) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        try {
            CompletionStage<Boolean> result = provider.evaluate(modeId, remoteSteamId);
            return result == null ? CompletableFuture.completedFuture(Boolean.FALSE) : result;
        } catch (RuntimeException ignored) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
    }

    private static boolean validModeId(String value) {
        return value != null
                && value.matches("[a-z][a-z0-9_.-]{0,31}:[a-z][a-z0-9_.-]{0,63}");
    }

    public interface Provider {
        boolean isRegistered(String modeId);
        CompletionStage<Boolean> evaluate(String modeId, long remoteSteamId);
    }
}
