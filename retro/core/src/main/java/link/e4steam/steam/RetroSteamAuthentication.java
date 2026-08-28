package link.e4steam.steam;

import link.e4steam.Agnos;
import link.e4steam.retro.RetroDedicatedBootstrap;

import java.net.SocketAddress;

/**
 * Resolves Steam-authenticated Minecraft sockets without loading the client
 * runtime on a physical dedicated server.
 */
public final class RetroSteamAuthentication {
    private RetroSteamAuthentication() {
    }

    public static long authenticatedPeer(SocketAddress remoteAddress) {
        long dedicated = RetroDedicatedBootstrap.authenticatedMinecraftPeer(remoteAddress);
        if (dedicated != 0L) return dedicated;
        return Agnos.isClient()
                ? SteamRuntime.get().authenticatedMinecraftPeer(remoteAddress)
                : 0L;
    }

    public static boolean rejectUntrustedDedicatedIngress(SocketAddress remoteAddress) {
        return RetroDedicatedBootstrap.requiresAuthenticatedIngress()
                && RetroDedicatedBootstrap.authenticatedMinecraftPeer(remoteAddress) == 0L;
    }
}
