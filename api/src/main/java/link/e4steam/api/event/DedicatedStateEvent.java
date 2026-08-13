package link.e4steam.api.event;

import link.e4steam.api.dedicated.DedicatedServerService.DedicatedServerSnapshot;

import java.util.Objects;

/** Replayable immutable dedicated starting/ready/draining/stopped state event. */
public final class DedicatedStateEvent implements ApiEvent {
    /** Typed event key. */ public static final EventType<DedicatedStateEvent> TYPE = new EventType<>("e4steam:dedicated_state", DedicatedStateEvent.class);
    private final long occurredAtEpochMillis; private final DedicatedServerSnapshot snapshot;
    /** Creates a state event. */ public DedicatedStateEvent(long occurredAtEpochMillis, DedicatedServerSnapshot snapshot) { if (occurredAtEpochMillis < 0) throw new IllegalArgumentException("invalid time"); this.occurredAtEpochMillis = occurredAtEpochMillis; this.snapshot = Objects.requireNonNull(snapshot, "snapshot"); }
    @Override public long occurredAtEpochMillis() { return occurredAtEpochMillis; }
    /** Returns immutable dedicated state. */ public DedicatedServerSnapshot snapshot() { return snapshot; }
}
