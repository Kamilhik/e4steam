package link.e4steam.steam;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;

/**
 * Bounded owner for RESET frames that Steam temporarily refuses to enqueue.
 * Entries are tied to one Steam worker generation and never expose payloads in
 * diagnostics. All state transitions are synchronized so close/send races can
 * only create one retry entry for a bridge generation.
 */
final class SteamResetRetryQueue<B> {
    enum OfferStatus {
        ACCEPTED,
        DUPLICATE,
        FULL
    }

    enum SendOutcome {
        SUCCESS,
        TEMPORARY_FAILURE,
        PERMANENT_FAILURE
    }

    enum State {
        RETRY_SCHEDULED,
        AWAITING_SEND,
        SENT,
        CANCELLED_STALE_GENERATION,
        CANCELLED_STALE_CONNECTION,
        CANCELLED_SHUTDOWN,
        EXHAUSTED,
        PERMANENT_FAILURE
    }

    static final class Offer<B> {
        private final OfferStatus status;
        private final Entry<B> entry;

        private Offer(OfferStatus status, Entry<B> entry) {
            this.status = status;
            this.entry = entry;
        }

        OfferStatus status() { return status; }
        Entry<B> entry() { return entry; }
    }

    static final class Entry<B> {
        private final Key<B> key;
        private final byte[] payload;
        private final long createdAtMillis;
        private int attempts;
        private long nextAttemptMillis;
        private State state;

        private Entry(
                Key<B> key,
                byte[] payload,
                long createdAtMillis,
                long nextAttemptMillis
        ) {
            this.key = key;
            this.payload = payload.clone();
            this.createdAtMillis = createdAtMillis;
            this.attempts = 1; // The first send already failed before admission.
            this.nextAttemptMillis = nextAttemptMillis;
            this.state = State.RETRY_SCHEDULED;
        }

        long remoteSteamId() { return key.remoteSteamId; }
        int connectionId() { return key.connectionId; }
        long generation() { return key.generation; }
        B bridge() { return key.bridge; }
        int attempts() { return attempts; }
        long nextAttemptMillis() { return nextAttemptMillis; }
        State state() { return state; }

        void putPayload(ByteBuffer target) {
            target.put(payload);
        }

        byte[] payloadCopy() {
            return payload.clone();
        }

        @Override
        public String toString() {
            return "SteamResetRetryEntry{generation=" + generation()
                    + ", attempts=" + attempts
                    + ", state=" + state
                    + '}';
        }
    }

    private final int capacity;
    private final int maxAttempts;
    private final long maxAgeMillis;
    private final long baseDelayMillis;
    private final long maxDelayMillis;
    private final LongUnaryOperator jitter;
    private final LinkedHashMap<Key<B>, Entry<B>> entries = new LinkedHashMap<>();

    SteamResetRetryQueue(
            int capacity,
            int maxAttempts,
            long maxAgeMillis,
            long baseDelayMillis,
            long maxDelayMillis
    ) {
        this(
                capacity,
                maxAttempts,
                maxAgeMillis,
                baseDelayMillis,
                maxDelayMillis,
                bound -> bound <= 0 ? 0 : ThreadLocalRandom.current().nextLong(bound)
        );
    }

    SteamResetRetryQueue(
            int capacity,
            int maxAttempts,
            long maxAgeMillis,
            long baseDelayMillis,
            long maxDelayMillis,
            LongUnaryOperator jitter
    ) {
        if (capacity < 1 || maxAttempts < 2 || maxAgeMillis < 1
                || baseDelayMillis < 1 || maxDelayMillis < baseDelayMillis) {
            throw new IllegalArgumentException("Invalid RESET retry bounds");
        }
        this.capacity = capacity;
        this.maxAttempts = maxAttempts;
        this.maxAgeMillis = maxAgeMillis;
        this.baseDelayMillis = baseDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
        this.jitter = jitter;
    }

    synchronized Offer<B> offerAfterTemporaryFailure(
            long remoteSteamId,
            int connectionId,
            byte[] payload,
            B bridge,
            long generation,
            long nowMillis
    ) {
        Key<B> key = new Key<>(remoteSteamId, connectionId, bridge, generation);
        Entry<B> existing = entries.get(key);
        if (existing != null) {
            return new Offer<>(OfferStatus.DUPLICATE, existing);
        }
        if (entries.size() >= capacity) {
            return new Offer<>(OfferStatus.FULL, null);
        }
        Entry<B> entry = new Entry<>(
                key,
                payload,
                nowMillis,
                nowMillis + retryDelayMillis(1)
        );
        entries.put(key, entry);
        return new Offer<>(OfferStatus.ACCEPTED, entry);
    }

    /** Returns either a ready send or a terminal stale/exhausted entry. */
    synchronized Entry<B> poll(long currentGeneration, long nowMillis) {
        Iterator<Map.Entry<Key<B>, Entry<B>>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<B> entry = iterator.next().getValue();
            if (entry.generation() != currentGeneration) {
                iterator.remove();
                entry.state = State.CANCELLED_STALE_GENERATION;
                return entry;
            }
            if (entry.attempts >= maxAttempts
                    || nowMillis - entry.createdAtMillis >= maxAgeMillis) {
                iterator.remove();
                entry.state = State.EXHAUSTED;
                return entry;
            }
            if (entry.state == State.RETRY_SCHEDULED
                    && nowMillis >= entry.nextAttemptMillis) {
                entry.state = State.AWAITING_SEND;
                return entry;
            }
        }
        return null;
    }

    synchronized State complete(Entry<B> entry, SendOutcome outcome, long nowMillis) {
        Entry<B> current = entries.get(entry.key);
        if (current != entry || entry.state != State.AWAITING_SEND) {
            return entry.state;
        }
        switch (outcome) {
            case SUCCESS:
                entries.remove(entry.key);
                entry.state = State.SENT;
                return entry.state;
            case PERMANENT_FAILURE:
                entries.remove(entry.key);
                entry.state = State.PERMANENT_FAILURE;
                return entry.state;
            case TEMPORARY_FAILURE:
                entry.attempts++;
                if (entry.attempts >= maxAttempts
                        || nowMillis - entry.createdAtMillis >= maxAgeMillis) {
                    entries.remove(entry.key);
                    entry.state = State.EXHAUSTED;
                    return entry.state;
                }
                entry.nextAttemptMillis = nowMillis + retryDelayMillis(entry.attempts);
                entry.state = State.RETRY_SCHEDULED;
                return entry.state;
            default:
                throw new IllegalStateException("Unhandled RESET retry outcome: " + outcome);
        }
    }

    synchronized State cancelStaleConnection(Entry<B> entry) {
        if (entries.remove(entry.key, entry)) {
            entry.state = State.CANCELLED_STALE_CONNECTION;
        }
        return entry.state;
    }

    synchronized List<Entry<B>> purge(B bridge) {
        List<Entry<B>> removed = new ArrayList<>();
        entries.entrySet().removeIf(candidate -> {
            Entry<B> entry = candidate.getValue();
            if (entry.bridge() != bridge) {
                return false;
            }
            entry.state = State.CANCELLED_STALE_CONNECTION;
            removed.add(entry);
            return true;
        });
        return removed;
    }

    synchronized List<Entry<B>> cancelAll() {
        List<Entry<B>> removed = new ArrayList<>(entries.values());
        entries.clear();
        for (Entry<B> entry : removed) {
            entry.state = State.CANCELLED_SHUTDOWN;
        }
        return removed;
    }

    synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    synchronized int size() {
        return entries.size();
    }

    private long retryDelayMillis(int failedAttempts) {
        long delay = baseDelayMillis;
        for (int attempt = 1; attempt < failedAttempts && delay < maxDelayMillis; attempt++) {
            delay = Math.min(maxDelayMillis, delay > maxDelayMillis / 2
                    ? maxDelayMillis
                    : delay * 2);
        }
        long jitterBound = Math.max(1, delay / 4 + 1);
        long jitterValue = Math.floorMod(jitter.applyAsLong(jitterBound), jitterBound);
        return delay + jitterValue;
    }

    private static final class Key<B> {
        private final long remoteSteamId;
        private final int connectionId;
        private final B bridge;
        private final long generation;

        private Key(long remoteSteamId, int connectionId, B bridge, long generation) {
            this.remoteSteamId = remoteSteamId;
            this.connectionId = connectionId;
            this.bridge = bridge;
            this.generation = generation;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            Key<?> key = (Key<?>) other;
            return remoteSteamId == key.remoteSteamId
                    && connectionId == key.connectionId
                    && bridge == key.bridge
                    && generation == key.generation;
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(remoteSteamId);
            result = 31 * result + connectionId;
            result = 31 * result + System.identityHashCode(bridge);
            result = 31 * result + Long.hashCode(generation);
            return result;
        }
    }
}
