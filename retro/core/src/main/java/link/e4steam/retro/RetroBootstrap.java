package link.e4steam.retro;

import link.e4steam.E4steamClient;
import link.e4steam.MinecraftVersion;
import link.e4steam.steam.SteamAccessMode;
import link.e4steam.steam.SteamRuntime;
import link.e4steam.steam.SteamSession;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Shared idempotent lifecycle called by every branch-scoped retro loader adapter. */
public final class RetroBootstrap {
    private static final Object LOCK = new Object();
    private static final List<String> CLIENT_COMMANDS = Collections.unmodifiableList(Arrays.asList(
            "start", "stop", "restart", "invite", "doctor", "addon", "help"));
    private static int relayPort;
    private static int hostedPort;
    private static volatile SteamAccessMode selectedAccessMode = SteamAccessMode.FRIENDS_ONLY;
    private static volatile RetroPlatform installedPlatform;

    private RetroBootstrap() {
    }

    public static void install(String minecraftVersion, RetroPlatform platform) {
        // The optional Unix overlay relaunch must happen before Minecraft
        // creates its first LWJGL/OpenGL context.
        SteamRuntime.relaunchForOverlayIfNeeded();
        MinecraftVersion.install(minecraftVersion);
        installedPlatform = platform;
        E4steamClient.install(platform);
        SteamRuntime.preloadCompatibilityClasses();
        SteamRuntime.get().startAtGameLaunchAsync();
    }

    /** Publishes a loopback-only relay created by a version-specific Minecraft hook. */
    public static void relayBound(int port) {
        int normalized = normalizePort(port);
        synchronized (LOCK) {
            relayPort = normalized;
            replaceHostedSessionLocked(normalized);
        }
    }

    /** Stops Steam exposure when the corresponding Minecraft listener closes. */
    public static void relayClosed() {
        synchronized (LOCK) {
            relayPort = 0;
            replaceHostedSessionLocked(0);
        }
    }

    /**
     * Fallback for loaders where the listener mixin is not applied reliably.
     * A real listener hook always wins and may replace this port later.
     */
    public static boolean relayBoundFallback(int port) {
        int normalized = normalizePort(port);
        if (normalized == 0) return false;
        synchronized (LOCK) {
            if (relayPort != 0) return relayPort == normalized;
            relayPort = normalized;
            replaceHostedSessionLocked(normalized);
            return true;
        }
    }

    /** Stops sharing only when the fallback still owns the observed LAN port. */
    public static void relayClosedFallback(int port) {
        int normalized = normalizePort(port);
        if (normalized == 0) return;
        synchronized (LOCK) {
            if (relayPort != normalized) return;
            relayPort = 0;
            replaceHostedSessionLocked(0);
        }
    }

    /** Compatibility entry point retained for older loader hooks. */
    public static void updateHostedPort(int port) {
        if (normalizePort(port) == 0) relayClosed();
        else relayBound(port);
    }

    private static int normalizePort(int port) {
        return port > 0 && port <= 65535 ? port : 0;
    }

    private static void replaceHostedSessionLocked(int port) {
        SteamSession current = E4steamClient.session;
        if (port == hostedPort && (port == 0 || isActive(current))) {
            return;
        }
        if (current != null) {
            E4steamClient.session = null;
            current.stop();
        }
        hostedPort = 0;
        if (port > 0 && selectedAccessMode != SteamAccessMode.LOCAL_ONLY) {
            SteamSession replacement = new SteamSession(port, selectedAccessMode);
            E4steamClient.session = replacement;
            hostedPort = port;
            replacement.startAsync();
        }
    }

    private static boolean isActive(SteamSession session) {
        if (session == null) return false;
        SteamSession.State state = session.state();
        return state != SteamSession.State.STOPPED
                && state != SteamSession.State.STOPPING
                && state != SteamSession.State.UNHEALTHY;
    }

    private static boolean startSharing(boolean forceRestart) {
        synchronized (LOCK) {
            SteamSession current = E4steamClient.session;
            if (relayPort == 0 || selectedAccessMode == SteamAccessMode.LOCAL_ONLY) {
                return false;
            }
            if (!forceRestart && isActive(current)) {
                return false;
            }
            if (current != null) {
                E4steamClient.session = null;
                current.stop();
            }
            hostedPort = 0;
            SteamSession replacement = new SteamSession(relayPort, selectedAccessMode);
            E4steamClient.session = replacement;
            hostedPort = relayPort;
            replacement.startAsync();
            return true;
        }
    }

    private static boolean stopSharing() {
        synchronized (LOCK) {
            SteamSession current = E4steamClient.session;
            if (!isActive(current)) return false;
            E4steamClient.session = null;
            hostedPort = 0;
            current.stop();
            return true;
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

    /** Command names exposed to version-specific completion implementations. */
    public static List<String> clientCommandNames() {
        return CLIENT_COMMANDS;
    }

    public static boolean handleClientCommand(String command) {
        if (command == null) {
            return false;
        }
        String normalized = command.trim();
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        String[] arguments = normalized.split("\\s+");
        if (arguments.length == 0 || !"e4steam".equalsIgnoreCase(arguments[0])) {
            return false;
        }
        if (arguments.length == 1) {
            showHelp();
            return true;
        }
        if (arguments.length != 2) {
            showHelp();
            return true;
        }

        String action = arguments[1].toLowerCase(Locale.ROOT);
        if ("copy".equals(action)) {
            final SteamSession current = E4steamClient.session;
            if (current != null && current.address() != null) {
                final String address = current.address().inviteString();
                final RetroPlatform platform = installedPlatform;
                if (platform != null) {
                    platform.execute(new Runnable() {
                        @Override public void run() {
                            if (platform.copyToClipboard(address)) {
                                platform.showTranslatedMessage(
                                        "text.e4steam_minecraft.addressCopied",
                                        "Steam address copied");
                            } else {
                                platform.showTranslatedMessage(
                                        "text.e4steam_minecraft.addressCopyFailed",
                                        "Could not copy the Steam address");
                            }
                        }
                    });
                }
            }
            return true;
        }
        if ("invite".equals(action)) {
            SteamSession current = E4steamClient.session;
            if (current == null || current.state() != SteamSession.State.STARTED) {
                showTranslated("text.e4steam_minecraft.serverAlreadyClosed",
                        "This world is not being shared through Steam");
            } else {
                current.openInviteOverlayAsync().whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        showTranslated("text.e4steam_minecraft.overlayUnavailable",
                                "The Steam invitation window is unavailable");
                    }
                });
            }
            return true;
        }
        if ("stop".equals(action)) {
            if (stopSharing()) {
                showTranslated("text.e4steam_minecraft.closeServer", "Steam sharing stopped");
            } else {
                showTranslated("text.e4steam_minecraft.serverAlreadyClosed",
                        "This world is not being shared through Steam");
            }
            return true;
        }
        if ("start".equals(action)) {
            if (startSharing(false)) {
                showTranslated("text.e4steam_minecraft.startSharing", "Starting Steam sharing");
            } else if (isActive(E4steamClient.session)) {
                showTranslated("text.e4steam_minecraft.serverAlreadyStarted",
                        "This world is already being shared through Steam");
            } else {
                showTranslated("text.e4steam_minecraft.serverAlreadyClosed",
                        "Open this world to LAN before starting Steam sharing");
            }
            return true;
        }
        if ("restart".equals(action)) {
            if (startSharing(true)) {
                showTranslated("text.e4steam_minecraft.startSharing", "Restarting Steam sharing");
            } else {
                showTranslated("text.e4steam_minecraft.serverAlreadyClosed",
                        "Open this world to LAN before restarting Steam sharing");
            }
            return true;
        }
        if ("doctor".equals(action)) {
            showDoctor();
            return true;
        }
        if ("addon".equals(action)) {
            showTranslated("text.e4steam_minecraft.command.addon.none",
                    "e4steam addons: no addons loaded");
            return true;
        }
        if ("help".equals(action)) {
            showHelp();
            return true;
        }
        showHelp();
        return true;
    }

    private static void showDoctor() {
        SteamSession current = E4steamClient.session;
        String session = current == null ? "STOPPED" : current.state().name();
        SteamRuntime runtime = SteamRuntime.get();
        showMessage("e4steam doctor: Steam=" + runtime.safeStatusCode()
                + ", session=" + session
                + ", LAN=" + (relayPort > 0 ? "READY" : "CLOSED"));
        String failure = runtime.safeFailureCategory();
        if (failure != null && !failure.isEmpty()) {
            showMessage("e4steam doctor: failure=" + failure);
        }
    }

    private static void showHelp() {
        showMessage("/e4steam <start|stop|restart|invite|doctor|addon|help>");
    }

    private static void showMessage(final String message) {
        final RetroPlatform platform = installedPlatform;
        if (platform == null) return;
        platform.execute(new Runnable() {
            @Override public void run() { platform.showMessage(message); }
        });
    }

    private static void showTranslated(final String key, final String fallback) {
        final RetroPlatform platform = installedPlatform;
        if (platform == null) return;
        platform.execute(new Runnable() {
            @Override public void run() { platform.showTranslatedMessage(key, fallback); }
        });
    }
}
