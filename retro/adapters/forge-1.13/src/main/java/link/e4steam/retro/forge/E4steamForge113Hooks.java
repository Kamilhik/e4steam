package link.e4steam.retro.forge;

import com.mojang.authlib.GameProfile;
import link.e4steam.E4steamClient;
import link.e4steam.steam.SteamAddress;
import link.e4steam.steam.SteamMinecraftIdentity;
import link.e4steam.steam.SteamRuntime;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Runtime calls injected by Forge 1.13's native JavaScript coremod. */
public final class E4steamForge113Hooks {
    private static final Map<NetworkManager, Long> AUTHENTICATED_CONNECTIONS =
            Collections.synchronizedMap(new WeakHashMap<NetworkManager, Long>());

    private E4steamForge113Hooks() {
    }

    /** Forces this class to be resolved before old ModLauncher callback threads use it. */
    public static void preload() {
    }

    /**
     * Consumes only valid e4steam addresses before vanilla starts its DNS
     * connector thread. Ordinary Minecraft server addresses are untouched.
     */
    public static boolean acceptDirectSteamAddress(String host) {
        if (!SteamAddress.tryParse(host).isPresent()) {
            return false;
        }
        E4steamClient.acceptDirectSteamInvite(host, "Steam host");
        return true;
    }

    public static boolean useMojangAuthentication(
            MinecraftServer server,
            NetworkManager connection
    ) {
        long steamId = authenticatedSteamId(connection);
        if (steamId != 0L) {
            E4steamClient.LOGGER.debug(
                    "Admitted a Steam-authenticated Forge 1.13 login on the local relay");
            return false;
        }
        return server.isServerInOnlineMode();
    }

    public static GameProfile bindSteamIdentity(
            GameProfile original,
            NetworkManager connection
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

    private static long authenticatedSteamId(NetworkManager connection) {
        if (connection == null) {
            return 0L;
        }
        Long cached = AUTHENTICATED_CONNECTIONS.get(connection);
        if (cached != null) {
            return cached.longValue();
        }
        long steamId = SteamRuntime.get().authenticatedMinecraftPeer(
                connection.getRemoteAddress());
        if (steamId != 0L) {
            AUTHENTICATED_CONNECTIONS.put(connection, Long.valueOf(steamId));
        }
        return steamId;
    }
}
