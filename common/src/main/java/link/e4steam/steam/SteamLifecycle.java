package link.e4steam.steam;

import java.io.IOException;
import java.nio.file.Path;

/** Restartable ownership of the process-global Steam API. */
final class SteamLifecycle implements AutoCloseable {
    private final SteamApi api;
    private SteamNativeLibraryLoader nativeLoader;
    private SteamProcessGuard.Lease processLease;
    private boolean librariesLoaded;
    private boolean initialized;

    SteamLifecycle(SteamApi api) {
        this.api = api;
    }

    void start() throws IOException {
        if (initialized) {
            return;
        }
        SteamProcessGuard.Lease acquired = SteamProcessGuard.acquire(
                SteamProcessGuard.Context.CLIENT
        );
        try {
            processLease = acquired;
            if (!librariesLoaded) {
                nativeLoader = new SteamNativeLibraryLoader();
                if (!api.loadLibraries(nativeLoader)) {
                    throw new IOException(
                            "Could not load Steam native libraries: "
                                    + nativeLoader.failureDescription(),
                            nativeLoader.failureCause()
                    );
                }
                librariesLoaded = true;
            }
            if (!api.init()) {
                throw new IOException(initializationFailureMessage(
                        System.getProperty("os.name", "")
                ));
            }
        } catch (IOException exception) {
            processLease = null;
            acquired.close();
            throw exception;
        } catch (Exception exception) {
            processLease = null;
            acquired.close();
            throw new IOException("SteamAPI_Init failed: " + exception.getMessage(), exception);
        }
        initialized = true;
        if (!api.isSteamRunning()) {
            close();
            throw new IOException("Steam is not running or the current user is not signed in");
        }
    }

    static String initializationFailureMessage(String osName) {
        String normalized = osName == null
                ? "" : osName.trim().toLowerCase(java.util.Locale.ROOT);
        String prefix = "SteamAPI_Init failed. Start Steam and sign in before launching Minecraft. ";
        if (normalized.contains("win")) {
            return prefix + "Run Steam and Minecraft at the same privilege level; "
                    + "do not run only one of them as administrator";
        }
        if (normalized.contains("linux") || normalized.contains("nix")
                || normalized.contains("nux")) {
            return prefix + "Run Steam and the launcher as the same desktop user; "
                    + "a sandboxed launcher must be allowed to access the Steam installation";
        }
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return prefix + "Run Steam and the launcher as the same macOS user";
        }
        return prefix + "Run Steam and Minecraft as the same operating-system user";
    }

    void runCallbacks() {
        if (!initialized) {
            throw new IllegalStateException("Steam lifecycle is not running");
        }
        api.runCallbacks();
    }

    boolean isRunning() {
        return initialized && api.isSteamRunning();
    }

    Path steamApiPath() {
        if (!initialized || nativeLoader == null) {
            throw new IllegalStateException("Steam lifecycle is not running");
        }
        return nativeLoader.steamApiPath();
    }

    @Override
    public void close() {
        if (initialized) {
            initialized = false;
            api.shutdown();
        }
        SteamProcessGuard.Lease lease = processLease;
        processLease = null;
        if (lease != null) lease.close();
    }
}
