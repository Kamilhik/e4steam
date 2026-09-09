package link.e4steam.steam;

/**
 * Thread-safe, credential-free projection of the current public-directory
 * join. Steam callbacks update it on the Steam worker while addon UI polls it
 * from an arbitrary client thread.
 */
final class SteamPublicJoinTracker {
    enum Phase {
        IDLE,
        JOINING_STEAM_TARGET,
        AUTHENTICATING,
        CONNECTING_MINECRAFT,
        ACTIVE,
        CANCELLED,
        STALE,
        FULL,
        INCOMPATIBLE,
        FAILED
    }

    private volatile Snapshot current = Snapshot.idle();

    void begin(long lobbyId) {
        if (lobbyId == 0L) return;
        current = new Snapshot(lobbyId, Phase.JOINING_STEAM_TARGET, "");
    }

    void authenticating(long lobbyId) {
        advance(lobbyId, Phase.AUTHENTICATING, "");
    }

    void connecting(long lobbyId) {
        advance(lobbyId, Phase.CONNECTING_MINECRAFT, "");
    }

    void active(long lobbyId) {
        advance(lobbyId, Phase.ACTIVE, "");
    }

    void cancel(long lobbyId) {
        advance(lobbyId, Phase.CANCELLED, "");
    }

    void fail(long lobbyId, Phase phase, String detailCode) {
        if (phase != Phase.STALE && phase != Phase.FULL
                && phase != Phase.INCOMPATIBLE && phase != Phase.FAILED) {
            throw new IllegalArgumentException("phase is not a failure state");
        }
        advance(lobbyId, phase, detailCode);
    }

    Snapshot snapshot(long lobbyId) {
        Snapshot snapshot = current;
        return lobbyId != 0L && snapshot.lobbyId == lobbyId
                ? snapshot : Snapshot.idle();
    }

    private void advance(long lobbyId, Phase phase, String detailCode) {
        if (lobbyId == 0L) return;
        Snapshot before = current;
        if (before.lobbyId != lobbyId || terminal(before.phase)) return;
        current = new Snapshot(lobbyId, phase, safeDetail(detailCode));
    }

    private static boolean terminal(Phase phase) {
        return phase == Phase.ACTIVE || phase == Phase.CANCELLED
                || phase == Phase.STALE || phase == Phase.FULL
                || phase == Phase.INCOMPATIBLE || phase == Phase.FAILED;
    }

    private static String safeDetail(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.matches("[a-z][a-z0-9_.-]{0,95}") ? value : "join-failed";
    }

    static final class Snapshot {
        private final long lobbyId;
        private final Phase phase;
        private final String detailCode;

        private Snapshot(long lobbyId, Phase phase, String detailCode) {
            this.lobbyId = lobbyId;
            this.phase = phase;
            this.detailCode = detailCode;
        }

        private static Snapshot idle() {
            return new Snapshot(0L, Phase.IDLE, "");
        }

        Phase phase() { return phase; }
        String detailCode() { return detailCode; }

        @Override public String toString() {
            return "SteamPublicJoinSnapshot{phase=" + phase + ", target=opaque}";
        }
    }
}
