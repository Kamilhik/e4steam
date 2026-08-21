package link.e4steam.retro;

import link.e4steam.E4steamClient;
import link.e4steam.MinecraftVersion;
import link.e4steam.steam.SteamAccessMode;
import link.e4steam.steam.SteamRuntime;
import link.e4steam.steam.SteamSession;

/** Shared idempotent lifecycle called by every branch-scoped retro loader adapter. */
public final class RetroBootstrap {
    private static final Object LOCK = new Object();
    private static int hostedPort;
    private static volatile SteamAccessMode selectedAccessMode = SteamAccessMode.FRIENDS_ONLY;

    private RetroBootstrap() {
    }

    public static void install(String minecraftVersion, RetroPlatform platform) {
        MinecraftVersion.install(minecraftVersion);
        E4steamClient.install(platform);
        SteamRuntime.preloadCompatibilityClasses();
        SteamRuntime.get().startAtGameLaunchAsync();
    }

    /** Publishes a loopback-only relay created by a version-specific Minecraft hook. */
    public static void relayBound(int port) {
        updateHostedPort(port);
    }

    /** Stops Steam exposure when the corresponding Minecraft listener closes. */
    public static void relayClosed() {
        updateHostedPort(0);
    }

    public static void updateHostedPort(int port) {
        if (port < 0 || port > 65535) {
            port = 0;
        }
        synchronized (LOCK) {
            SteamSession current = E4steamClient.session;
            if (port == hostedPort && (port == 0 || current != null)) {
                return;
            }
            if (current != null) {
                E4steamClient.session = null;
                current.stop();
            }
            hostedPort = port;
            if (port > 0 && selectedAccessMode != SteamAccessMode.LOCAL_ONLY) {
                SteamSession replacement = new SteamSession(port, selectedAccessMode);
                E4steamClient.session = replacement;
                replacement.startAsync();
            }
        }
    }

    public static SteamAccessMode selectedAccessMode() {
        return selectedAccessMode;
    }

    public static SteamAccessMode cycleAccessMode() {
        SteamAccessMode current = selectedAccessMode;
        SteamAccessMode[] modes = SteamAccessMode.values();
        SteamAccessMode next = modes[(current.ordinal() + 1) % modes.length];
        selectedAccessMode = next;
        return next;
    }

    public static boolean handleClientCommand(String command) {
        if (command == null) {
            return false;
        }
        String normalized = command.trim().toLowerCase(java.util.Locale.ROOT);
        if ("/e4steam invite".equals(normalized)) {
            SteamSession current = E4steamClient.session;
            if (current == null || current.state() != SteamSession.State.STARTED) {
                E4steamClient.showSteamJoinFailure("Steam lobby is not ready");
            } else {
                current.openInviteOverlayAsync();
            }
            return true;
        }
        if ("/e4steam stop".equals(normalized)) {
            updateHostedPort(0);
            return true;
        }
        return false;
    }
}
