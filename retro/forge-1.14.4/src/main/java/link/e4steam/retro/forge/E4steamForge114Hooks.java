package link.e4steam.retro.forge;

import com.mojang.authlib.GameProfile;
import link.e4steam.E4steamClient;
import link.e4steam.steam.SteamMinecraftIdentity;
import link.e4steam.steam.RetroSteamAuthentication;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Runtime calls injected by Forge 1.14's native JavaScript coremod. */
public final class E4steamForge114Hooks {
    private static final long VANILLA_KEEP_ALIVE_INTERVAL_MILLIS = 15_000L;
    private static final long STEAM_KEEP_ALIVE_INTERVAL_MILLIS = 60_000L;
    private static final Map<Connection, Long> AUTHENTICATED_CONNECTIONS =
            Collections.synchronizedMap(new WeakHashMap<Connection, Long>());

    private E4steamForge114Hooks() {
    }

    /** Forces this class to be resolved before old ModLauncher uses it. */
    public static void preload() {
    }

    /**
     * Consumes only valid e4steam addresses before vanilla starts its DNS
     * connector thread. Ordinary Minecraft server addresses are untouched.
     */
    public static boolean acceptDirectSteamAddress(String host) {
        if (!E4steamClient.isSteamEndpoint(host)) {
            return false;
        }
        E4steamClient.acceptDirectSteamInvite(host, "Steam host");
        return true;
    }

    /**
     * Gives the initial Forge registry and chunk burst enough time to cross
     * Steam without weakening ordinary LAN connections. Minecraft disconnects
     * after two unanswered keep-alive intervals, so an authenticated Steam
     * bridge receives up to two minutes while vanilla remains at 30 seconds.
     */
    public static long keepAliveIntervalMillis(Connection connection) {
        return authenticatedSteamId(connection) == 0L
                ? VANILLA_KEEP_ALIVE_INTERVAL_MILLIS
                : STEAM_KEEP_ALIVE_INTERVAL_MILLIS;
    }

    public static boolean useMojangAuthentication(
            MinecraftServer server,
            Connection connection
    ) {
        long steamId = authenticatedSteamId(connection);
        if (steamId != 0L) {
            E4steamClient.LOGGER.debug(
                    "Admitted a Steam-authenticated Forge 1.14 login on the local relay");
            return false;
        }
        return server.usesAuthentication();
    }

    public static GameProfile bindSteamIdentity(
            GameProfile original,
            Connection connection
    ) {
        if (original == null) {
            return null;
        }
        long steamId = authenticatedSteamId(connection);
        if (steamId == 0L) {
            return original;
        }
        return new GameProfile(
                SteamMinecraftIdentity.uuid(steamId),
                SteamMinecraftIdentity.preserveMinecraftName(original.getName())
        );
    }

    private static long authenticatedSteamId(Connection connection) {
        if (connection == null) {
            return 0L;
        }
        Long cached = AUTHENTICATED_CONNECTIONS.get(connection);
        if (cached != null) {
            return cached.longValue();
        }
        long steamId = RetroSteamAuthentication.authenticatedPeer(
                connection.getRemoteAddress());
        if (steamId != 0L) {
            AUTHENTICATED_CONNECTIONS.put(connection, Long.valueOf(steamId));
        }
        return steamId;
    }
}
