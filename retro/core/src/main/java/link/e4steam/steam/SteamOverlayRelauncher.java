package link.e4steam.steam;

/**
 * Java 8 retro builds do not reconstruct or replace their launcher JVM.
 * Modern/legacy-1.17+ artifacts provide the opt-in Unix implementation.
 */
final class SteamOverlayRelauncher {
    private SteamOverlayRelauncher() {
    }

    static void relaunchIfNeeded() {
        // Intentionally unavailable on the Java 8 retro runtime.
    }
}
