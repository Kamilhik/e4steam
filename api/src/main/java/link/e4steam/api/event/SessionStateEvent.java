package link.e4steam.api.event;

import link.e4steam.api.session.SessionService.SessionSnapshot;

import java.util.Objects;

/** Replayable immutable event for session creating/active/reconnecting/closed transitions. */
public final class SessionStateEvent implements ApiEvent {
    /** Typed event key. */ public static final EventType<SessionStateEvent> TYPE = new EventType<>("e4steam:session_state", SessionStateEvent.class);
    private final long occurredAtEpochMillis; private final SessionSnapshot snapshot;
    /** Creates a state event. */ public SessionStateEvent(long occurredAtEpochMillis, SessionSnapshot snapshot) { if (occurredAtEpochMillis < 0) throw new IllegalArgumentException("invalid time"); this.occurredAtEpochMillis = occurredAtEpochMillis; this.snapshot = Objects.requireNonNull(snapshot, "snapshot"); }
    @Override public long occurredAtEpochMillis() { return occurredAtEpochMillis; }
    /** Returns immutable session state. */ public SessionSnapshot snapshot() { return snapshot; }
}
