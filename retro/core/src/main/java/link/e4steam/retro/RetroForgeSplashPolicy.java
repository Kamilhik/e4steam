package link.e4steam.retro;

import link.e4steam.steam.SteamOverlayRelauncher;

/** Runtime policy for the legacy Forge splash-window compatibility patch. */
public final class RetroForgeSplashPolicy {
    private RetroForgeSplashPolicy() {
    }

    /**
     * The old Forge splash owns a second LWJGL drawable. On Linux and macOS
     * that drawable is created between Steam overlay injection and the real
     * Minecraft window, so Valve's OpenGL hook can attach to the wrong one.
     * Windows does not use the Unix preload path and remains untouched.
     * macOS legacy Forge is also left untouched because relaunching the Java 8
     * LWJGL 2 process can hide the replacement window from Dock entirely.
     */
    public static boolean shouldDisable() {
        return SteamOverlayRelauncher.isUnixOverlayRelaunchRequested()
                && !SteamOverlayRelauncher.isMacOsUnixOverlayRelaunchRequested();
    }

    /**
     * LWJGL 2 on macOS can retain a stale Display binding after Valve's
     * OpenGL interposer is injected. Repairing it is intentionally limited
     * to the replacement JVM; normal launches and every other platform keep
     * Minecraft's original GL allocation path.
     */
    public static boolean shouldRepairLegacyDisplayContext() {
        return false;
    }
}
