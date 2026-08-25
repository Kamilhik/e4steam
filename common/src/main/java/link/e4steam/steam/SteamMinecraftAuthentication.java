package link.e4steam.steam;

import link.e4steam.E4steamDedicated;

import java.net.SocketAddress;

/** Resolves only exact loopback sockets admitted by an active Steam runtime. */
public final class SteamMinecraftAuthentication {
    private SteamMinecraftAuthentication() {
    }

    public static long authenticatedPeer(SocketAddress remoteAddress) {
        long dedicated = E4steamDedicated.authenticatedMinecraftPeer(remoteAddress);
        return dedicated != 0L
                ? dedicated
                : SteamRuntime.get().authenticatedMinecraftPeer(remoteAddress);
    }

    public static boolean rejectUntrustedDedicatedIngress(SocketAddress remoteAddress) {
        return E4steamDedicated.requiresAuthenticatedIngress()
                && E4steamDedicated.authenticatedMinecraftPeer(remoteAddress) == 0L;
    }
}
