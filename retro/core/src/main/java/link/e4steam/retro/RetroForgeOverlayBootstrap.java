package link.e4steam.retro;

import link.e4steam.steam.SteamOverlayRelauncher;
import link.e4steam.steam.SteamRuntime;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts the Unix overlay path from Forge's core-plugin phase.
 *
 * <p>Forge 1.7-1.12 creates its LWJGL Display before constructing ordinary
 * mods. Starting from that constructor is therefore too late for Valve's
 * OpenGL hook. The branch-specific core plugin calls this class before the
 * Minecraft display exists. The normal RetroBootstrap call remains as an
 * idempotent fallback for other loaders.</p>
 */
public final class RetroForgeOverlayBootstrap {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private RetroForgeOverlayBootstrap() {
    }

    public static void install() {
        if (!SteamOverlayRelauncher.isUnixOverlayRelaunchRequested()
                || !INSTALLED.compareAndSet(false, true)) {
            return;
        }
        if (SteamOverlayRelauncher.isMacOsUnixOverlayRelaunchRequested()) {
            System.err.println(
                    "[e4steam] Skipping legacy Forge macOS overlay relaunch; "
                            + "Steam will start without overlay injection"
            );
            return;
        }

        // In the original JVM this blocks until the injected replacement JVM
        // exits. In that replacement the marker makes relaunchIfNeeded a no-op,
        // then Steam starts before Forge creates its first OpenGL drawable.
        SteamOverlayRelauncher.relaunchIfNeeded();
        SteamRuntime.preloadCompatibilityClasses();
        SteamRuntime.get().startAtGameLaunchAsync();
    }
}
