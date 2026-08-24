package link.e4steam.steam;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents the compatibility pre-accept poll from reviving a native Steam
 * session which belongs to a bridge generation that is being torn down.
 */
final class SteamKnownPeerSessionGate {
    private final long reacceptDelayMillis;
    private final ConcurrentHashMap<Long, Long> reacceptDeadlines = new ConcurrentHashMap<>();

    SteamKnownPeerSessionGate(long reacceptDelayMillis) {
        if (reacceptDelayMillis < 0) {
            throw new IllegalArgumentException("reacceptDelayMillis must not be negative");
        }
        this.reacceptDelayMillis = reacceptDelayMillis;
    }

    void defer(long remoteSteamId, long nowMillis) {
        long requestedDeadline = nowMillis + reacceptDelayMillis;
        reacceptDeadlines.merge(
                remoteSteamId,
                requestedDeadline,
                (previous, requested) -> Math.max(previous, requested)
        );
    }

    /** A real request callback or registered bridge identifies a new generation. */
    void observeNewSession(long remoteSteamId) {
        reacceptDeadlines.remove(remoteSteamId);
    }

    boolean mayProactivelyAccept(
            long remoteSteamId,
            boolean bridgeExists,
            boolean pending,
            boolean capacityAvailable,
            long nowMillis
    ) {
        return !bridgeExists
                && !pending
                && capacityAvailable
                && nowMillis >= reacceptDeadlines.getOrDefault(remoteSteamId, 0L);
    }

    void clear() {
        reacceptDeadlines.clear();
    }
}
