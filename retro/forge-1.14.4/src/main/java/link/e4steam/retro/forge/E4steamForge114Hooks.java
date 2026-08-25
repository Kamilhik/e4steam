package link.e4steam.retro.forge;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;

import java.net.SocketAddress;

/** Runtime calls injected by Forge 1.14's native JavaScript coremod. */
public final class E4steamForge114Hooks {
    private static final E4steamForgeHooksBase<Connection> INSTANCE =
            new E4steamForgeHooksBase<Connection>("Forge 1.14") {
                @Override
                protected boolean onlineMode(MinecraftServer server) {
                    return server.usesAuthentication();
                }

                @Override
                protected SocketAddress remoteAddressOf(Connection connection) {
                    return connection.getRemoteAddress();
                }
            };

    private E4steamForge114Hooks() {
    }

    /** Forces this class to be resolved before old ModLauncher uses it. */
    public static void preload() {
    }

    public static boolean acceptDirectSteamAddress(String host) {
        return INSTANCE.acceptDirectSteamAddress(host);
    }

    /**
     * Gives the initial Forge registry and chunk burst enough time to cross
     * Steam without weakening ordinary LAN connections. Minecraft disconnects
     * after two unanswered keep-alive intervals, so an authenticated Steam
     * bridge receives up to two minutes while vanilla remains at 30 seconds.
     */
    public static long keepAliveIntervalMillis(Connection connection) {
        return INSTANCE.keepAliveIntervalMillis(connection);
    }

    public static boolean useMojangAuthentication(
            MinecraftServer server,
            Connection connection
    ) {
        return INSTANCE.useMojangAuthentication(server, connection);
    }

    public static GameProfile bindSteamIdentity(
            GameProfile original,
            Connection connection
    ) {
        return INSTANCE.bindSteamIdentity(original, connection);
    }
}