package link.e4steam;

import link.e4steam.retro.RetroPlatform;
import link.e4steam.steam.SteamAddress;
import link.e4steam.steam.SteamClientBridge;
import link.e4steam.steam.SteamRuntime;
import link.e4steam.steam.SteamSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Loader-neutral callbacks consumed by the shared Steam runtime. */
public final class E4steamClient {
    public static final String MOD_ID = "e4steam";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static volatile SteamSession session;
    private static volatile RetroPlatform platform;

    private E4steamClient() {
    }

    public static void install(RetroPlatform replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException("Retro platform is required");
        }
        platform = replacement;
    }

    public static void sessionReady(final SteamSession ready) {
        final RetroPlatform current = requirePlatform();
        current.execute(new Runnable() {
            @Override public void run() {
                if (session != ready || ready.address() == null) {
                    return;
                }
                current.showSharingReady(ready.address().inviteString());
            }
        });
    }

    public static void sessionFailed(final Throwable failure) {
        LOGGER.error("Could not start e4steam retro sharing", failure);
        showSteamJoinFailure(failure == null ? "Steam startup failed" : failure.getMessage());
    }

    public static void acceptDirectSteamInvite(String endpoint, String hostName) {
        final Optional<SteamAddress> parsed = SteamAddress.tryParse(endpoint);
        if (!parsed.isPresent()) {
            showSteamJoinFailure("Invalid Steam address");
            return;
        }

        // A copied address is the fallback for friends-only sharing and is
        // intentionally not backed by a lobby invitation. World access is
        // still authorized by the host using the secret token and Steam
        // friendship policy when the OPEN frame arrives.
        SteamRuntime.get().cancelGuestJoin();
        openSteamBridgeAsync(parsed.get(), hostName);
    }

    public static void acceptSteamInvite(final String endpoint, final String hostName) {
        final Optional<SteamAddress> parsed = SteamAddress.tryParse(endpoint);
        if (!parsed.isPresent()) {
            showSteamJoinFailure("Invalid Steam address");
            return;
        }
        CompletableFuture<Boolean> claim = SteamRuntime.get().beginGuestConnect(endpoint);
        claim.whenComplete((accepted, failure) -> {
            if (failure != null || !Boolean.TRUE.equals(accepted)) {
                showSteamJoinFailure(failure == null
                        ? "Steam invitation rejected" : failure.getMessage());
                return;
            }
            openSteamBridge(parsed.get(), hostName);
        });
    }

    private static void openSteamBridge(SteamAddress address, final String hostName) {
        try {
            final InetSocketAddress local = SteamClientBridge.open(address);
            final RetroPlatform current = requirePlatform();
            current.execute(new Runnable() {
                @Override public void run() {
                    current.connect(local, safeDisplayName(hostName));
                }
            });
        } catch (Throwable throwable) {
            showSteamJoinFailure(throwable.getMessage());
        }
    }

    private static void openSteamBridgeAsync(
            final SteamAddress address,
            final String hostName
    ) {
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                openSteamBridge(address, hostName);
            }
        }, "e4steam-retro-direct-connect");
        worker.setDaemon(true);
        worker.start();
    }

    public static void showSteamJoinFailure(Object detail) {
        final String message = detail == null ? "Steam operation failed" : String.valueOf(detail);
        LOGGER.warn("Steam join failed: " + message);
        final RetroPlatform current = platform;
        if (current != null) {
            current.execute(new Runnable() {
                @Override public void run() {
                    current.showMessage("e4steam: " + message);
                }
            });
        }
    }

    private static RetroPlatform requirePlatform() {
        RetroPlatform current = platform;
        if (current == null) {
            throw new IllegalStateException("Retro platform is not initialized");
        }
        return current;
    }

    private static String safeDisplayName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "Steam friend";
        }
        String clean = value.trim();
        return clean.length() <= 64 ? clean : clean.substring(0, 64);
    }
}
