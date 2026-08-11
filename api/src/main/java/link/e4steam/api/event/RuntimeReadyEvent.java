package link.e4steam.api.event;

import link.e4steam.api.runtime.RuntimeSnapshot;

import java.util.Objects;

/** Replayable event emitted after a runtime generation reaches readiness. */
public final class RuntimeReadyEvent implements ApiEvent {
    /** Typed event key. */
    public static final EventType<RuntimeReadyEvent> TYPE =
            new EventType<>("e4steam:runtime_ready", RuntimeReadyEvent.class);

    private final long occurredAtEpochMillis;
    private final RuntimeSnapshot snapshot;

    /** Creates an immutable runtime-ready event. */
    public RuntimeReadyEvent(long occurredAtEpochMillis, RuntimeSnapshot snapshot) {
        if (occurredAtEpochMillis < 0) {
            throw new IllegalArgumentException("occurredAtEpochMillis must be non-negative");
        }
        this.occurredAtEpochMillis = occurredAtEpochMillis;
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    @Override
    public long occurredAtEpochMillis() { return occurredAtEpochMillis; }

    /** Returns the privacy-safe ready snapshot. */
    public RuntimeSnapshot snapshot() { return snapshot; }
}
