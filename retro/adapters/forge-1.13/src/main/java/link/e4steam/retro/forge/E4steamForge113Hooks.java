package link.e4steam.retro.forge;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;

import java.net.SocketAddress;

/** Runtime calls injected by Forge 1.13's native JavaScript coremod. */
public final class E4steamForge113Hooks {
    private static final E4steamForgeHooksBase<NetworkManager> INSTANCE =
            new E4steamForgeHooksBase<NetworkManager>("Forge 1.13") {
                @Override
                protected boolean onlineMode(MinecraftServer server) {
                    return server.isServerInOnlineMode();
                }

                @Override
                protected SocketAddress remoteAddressOf(NetworkManager connection) {
                    return connection.getRemoteAddress();
                }
            };

    private E4steamForge113Hooks() {
    }

    /** Forces this class to be resolved before old ModLauncher callback threads use it. */
    public static void preload() {
    }

    public static boolean acceptDirectSteamAddress(String host) {
        return INSTANCE.acceptDirectSteamAddress(host);
    }

    public static boolean useMojangAuthentication(
            MinecraftServer server,
            NetworkManager connection
    ) {
        return INSTANCE.useMojangAuthentication(server, connection);
    }

    public static GameProfile bindSteamIdentity(
            GameProfile original,
            NetworkManager connection
    ) {
        return INSTANCE.bindSteamIdentity(original, connection);
    }
}