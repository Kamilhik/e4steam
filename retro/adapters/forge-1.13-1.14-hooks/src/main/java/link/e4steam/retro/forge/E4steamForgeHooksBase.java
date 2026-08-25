package link.e4steam.retro.forge;

import com.mojang.authlib.GameProfile;
import link.e4steam.E4steamClient;
import link.e4steam.steam.SteamAddress;
import link.e4steam.steam.SteamMinecraftIdentity;
import link.e4steam.steam.SteamRuntime;
import net.minecraft.server.MinecraftServer;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Forge hooks shared by the 1.13 and 1.14 JavaScript coremods. The only
 * version differences are the connection class and the online-mode check,
 * supplied by the subclass.
 */
public abstract class E4steamForgeHooksBase<C> {
    private static final long VANILLA_KEEP_ALIVE_INTERVAL_MILLIS = 15_000L;
    private static final long STEAM_KEEP_ALIVE_INTERVAL_MILLIS = 60_000L;

    private final String loginLabel;
    private final Map<C, Long> authenticatedConnections =
            Collections.synchronizedMap(new WeakHashMap<>());

    protected E4steamForgeHooksBase(String loginLabel) {
        this.loginLabel = loginLabel;
    }

    protected abstract boolean onlineMode(MinecraftServer server);

    protected abstract SocketAddress remoteAddressOf(C connection);

    /**
     * Consumes only valid e4steam addresses before vanilla starts its DNS
     * connector thread. Ordinary Minecraft server addresses are untouched.
     */
    public final boolean acceptDirectSteamAddress(String host) {
        if (!SteamAddress.tryParse(host).isPresent()) {
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
    public final long keepAliveIntervalMillis(C connection) {
        return authenticatedSteamId(connection) == 0L
                ? VANILLA_KEEP_ALIVE_INTERVAL_MILLIS
                : STEAM_KEEP_ALIVE_INTERVAL_MILLIS;
    }

    public final boolean useMojangAuthentication(MinecraftServer server, C connection) {
        long steamId = authenticatedSteamId(connection);
        if (steamId != 0L) {
            E4steamClient.LOGGER.debug(
                    "Admitted a Steam-authenticated " + loginLabel
                            + " login on the local relay");
            return false;
        }
        return onlineMode(server);
    }

    public final GameProfile bindSteamIdentity(GameProfile original, C connection) {
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

    private long authenticatedSteamId(C connection) {
        if (connection == null) {
            return 0L;
        }
        Long cached = authenticatedConnections.get(connection);
        if (cached != null) {
            return cached.longValue();
        }
        long steamId = SteamRuntime.get().authenticatedMinecraftPeer(
                remoteAddressOf(connection));
        if (steamId != 0L) {
            authenticatedConnections.put(connection, Long.valueOf(steamId));
        }
        return steamId;
    }
}