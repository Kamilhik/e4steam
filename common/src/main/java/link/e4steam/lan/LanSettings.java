package link.e4steam.lan;

import java.util.Locale;
import java.util.Objects;

/**
 * Loader- and Minecraft-version-neutral settings for an integrated LAN world.
 *
 * <p>The class deliberately contains no Minecraft types. Modern and retro UI
 * adapters can therefore share the same validation and Steam admission limit
 * without linking old game JARs to classes from a newer Minecraft version.</p>
 */
public final class LanSettings {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 64;
    public static final int DEFAULT_PORT = 25565;

    private static volatile Snapshot current = defaults();
    private static volatile java.lang.ref.WeakReference<Object> activeServer = new java.lang.ref.WeakReference<>(null);
    private static volatile Snapshot active = defaults();
    private static volatile int nonSteamPlayers = 1;

    private LanSettings() {
    }

    public static Snapshot current() {
        return current;
    }

    public static void install(Snapshot replacement) {
        current = Objects.requireNonNull(replacement, "replacement").validated();
    }

    public static Snapshot defaults() {
        return new Snapshot(true, true, 8, DEFAULT_PORT, "survival", false);
    }

    public static int steamGuestLimit() {
        return Math.max(0, active.maxPlayers() - nonSteamPlayers);
    }

    public static int steamLobbyCapacity() {
        return active.maxPlayers();
    }

    public static void activate(Object server, Snapshot settings) {
        Objects.requireNonNull(server, "server");
        if (activeServer.get() != server) nonSteamPlayers = 1;
        active = settings.validated();
        activeServer = new java.lang.ref.WeakReference<>(Objects.requireNonNull(server));
    }

    public static Snapshot active(Object server) {
        return server != null && activeServer.get() == server ? active : null;
    }

    /** Includes the local owner; pending Steam bridges are counted by SteamRuntime. */
    public static void nonSteamPlayers(Object server, int count) {
        if (activeServer.get() == server) nonSteamPlayers = Math.max(1, count);
    }

    public static void closed() {
        activeServer.clear();
        active = defaults();
        nonSteamPlayers = 1;
    }

    public static final class Snapshot {
        private final boolean onlineMode;
        private final boolean pvp;
        private final int maxPlayers;
        private final int port;
        private final String gameMode;
        private final boolean allowCheats;

        public Snapshot(boolean onlineMode, boolean pvp, int maxPlayers, int port,
                        String gameMode, boolean allowCheats) {
            this.onlineMode = onlineMode;
            this.pvp = pvp;
            this.maxPlayers = maxPlayers;
            this.port = port;
            this.gameMode = gameMode == null ? "survival"
                    : gameMode.trim().toLowerCase(Locale.ROOT);
            this.allowCheats = allowCheats;
        }

        public boolean onlineMode() { return onlineMode; }
        public boolean pvp() { return pvp; }
        public int maxPlayers() { return maxPlayers; }
        public int port() { return port; }
        public String gameMode() { return gameMode; }
        public boolean allowCheats() { return allowCheats; }

        public Snapshot withOnlineMode(boolean value) {
            return new Snapshot(value, pvp, maxPlayers, port, gameMode, allowCheats);
        }

        public Snapshot withPvp(boolean value) {
            return new Snapshot(onlineMode, value, maxPlayers, port, gameMode, allowCheats);
        }

        public Snapshot withMaxPlayers(int value) {
            return new Snapshot(onlineMode, pvp, value, port, gameMode, allowCheats).validated();
        }

        public Snapshot withPort(int value) {
            return new Snapshot(onlineMode, pvp, maxPlayers, value, gameMode, allowCheats).validated();
        }

        public Snapshot withGameMode(String value) {
            return new Snapshot(onlineMode, pvp, maxPlayers, port, value, allowCheats).validated();
        }

        public Snapshot withAllowCheats(boolean value) {
            return new Snapshot(onlineMode, pvp, maxPlayers, port, gameMode, value);
        }

        public Snapshot validated() {
            if (maxPlayers < MIN_PLAYERS || maxPlayers > MAX_PLAYERS) {
                throw new IllegalArgumentException("LAN max players must be between "
                        + MIN_PLAYERS + " and " + MAX_PLAYERS);
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("LAN port must be between 1 and 65535");
            }
            if (!gameMode.equals("survival") && !gameMode.equals("creative")
                    && !gameMode.equals("adventure") && !gameMode.equals("spectator")) {
                throw new IllegalArgumentException("Unsupported LAN game mode: " + gameMode);
            }
            return this;
        }
    }
}
