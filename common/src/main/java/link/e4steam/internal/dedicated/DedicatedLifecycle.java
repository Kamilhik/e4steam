package link.e4steam.internal.dedicated;

import link.e4steam.api.dedicated.DedicatedServerService;
import link.e4steam.api.dedicated.DedicatedServerService.DedicatedServerState;
import link.e4steam.api.dedicated.DedicatedServerService.DedicatedServerSnapshot;
import link.e4steam.api.dedicated.DedicatedServerService.ServerAuthorityRef;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Strict dedicated lifecycle with readiness separated from process liveness. */
final class DedicatedLifecycle {
    private static final Map<DedicatedServerState, EnumSet<DedicatedServerState>> ALLOWED =
            allowedTransitions();
    private final Object lock = new Object();
    private final CompletableFuture<DedicatedServerSnapshot> readiness = new CompletableFuture<>();
    private final DedicatedRuntimeConfig config;
    private DedicatedServerState state = DedicatedServerState.OFF;
    private ServerAuthorityRef authority;
    private boolean ingressGuarded;
    private int players;
    private String failureCategory = "";

    DedicatedLifecycle(DedicatedRuntimeConfig config) {
        this.config = config;
    }

    void begin(long generation) {
        synchronized (lock) {
            if (authority != null) throw new IllegalStateException("Dedicated generation already started");
            authority = new ServerAuthorityRef(opaqueAuthority(), generation);
            transitionLocked(DedicatedServerState.CONFIG_VALIDATED, "");
        }
    }

    void transition(DedicatedServerState next) {
        synchronized (lock) {
            transitionLocked(next, "");
        }
    }

    void accepting(boolean guarded) {
        synchronized (lock) {
            ingressGuarded = guarded;
            transitionLocked(DedicatedServerState.MINECRAFT_READY, "");
            transitionLocked(DedicatedServerState.ACCEPTING, "");
            readiness.complete(snapshotLocked());
        }
    }

    void fail(String category) {
        synchronized (lock) {
            if (terminal(state)) return;
            failureCategory = safeCategory(category);
            state = DedicatedServerState.FAILED;
            DedicatedServerSnapshot snapshot = snapshotLocked();
            readiness.completeExceptionally(new IllegalStateException(failureCategory));
        }
    }

    void players(int value) {
        synchronized (lock) {
            if (value < 0 || value > config.maxPeers()) throw new IllegalArgumentException("players");
            players = value;
        }
    }

    DedicatedServerSnapshot snapshot() {
        synchronized (lock) {
            return snapshotLocked();
        }
    }

    CompletionStage<DedicatedServerSnapshot> readiness() {
        return readiness.thenApply(value -> value);
    }

    private void transitionLocked(DedicatedServerState next, String category) {
        if (next == state) return;
        EnumSet<DedicatedServerState> allowed = ALLOWED.get(state);
        if (allowed == null || !allowed.contains(next)) {
            throw new IllegalStateException("Invalid dedicated lifecycle transition");
        }
        state = next;
        failureCategory = category;
    }

    private DedicatedServerSnapshot snapshotLocked() {
        ServerAuthorityRef current = authority;
        if (current == null) {
            current = new ServerAuthorityRef("dedicated_off", 1L);
        }
        return new DedicatedServerSnapshot(
                state,
                current,
                config.accessMode(),
                ingressGuarded,
                false,
                players,
                config.maxPeers(),
                failureCategory
        );
    }

    private static boolean terminal(DedicatedServerState value) {
        return value == DedicatedServerState.STOPPED || value == DedicatedServerState.FAILED;
    }

    private static String safeCategory(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{3,96}")) return "DEDICATED_FAILED";
        return value;
    }

    private static String opaqueAuthority() {
        byte[] random = new byte[18];
        new SecureRandom().nextBytes(random);
        return "srv_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static Map<DedicatedServerState, EnumSet<DedicatedServerState>> allowedTransitions() {
        EnumMap<DedicatedServerState, EnumSet<DedicatedServerState>> result =
                new EnumMap<>(DedicatedServerState.class);
        result.put(DedicatedServerState.OFF, EnumSet.of(DedicatedServerState.CONFIG_VALIDATED));
        result.put(DedicatedServerState.CONFIG_VALIDATED,
                EnumSet.of(DedicatedServerState.NATIVES_READY, DedicatedServerState.DRAINING));
        result.put(DedicatedServerState.NATIVES_READY,
                EnumSet.of(DedicatedServerState.STEAM_INITIALIZING, DedicatedServerState.DRAINING));
        result.put(DedicatedServerState.STEAM_INITIALIZING,
                EnumSet.of(DedicatedServerState.STEAM_LOGGING_ON, DedicatedServerState.DRAINING));
        result.put(DedicatedServerState.STEAM_LOGGING_ON,
                EnumSet.of(DedicatedServerState.TRANSPORT_READY, DedicatedServerState.DRAINING));
        result.put(DedicatedServerState.TRANSPORT_READY,
                EnumSet.of(DedicatedServerState.MINECRAFT_READY, DedicatedServerState.DRAINING));
        result.put(DedicatedServerState.MINECRAFT_READY,
                EnumSet.of(DedicatedServerState.ACCEPTING, DedicatedServerState.DRAINING));
        result.put(DedicatedServerState.ACCEPTING,
                EnumSet.of(DedicatedServerState.DRAINING));
        result.put(DedicatedServerState.DRAINING,
                EnumSet.of(DedicatedServerState.STOPPED));
        return result;
    }
}
