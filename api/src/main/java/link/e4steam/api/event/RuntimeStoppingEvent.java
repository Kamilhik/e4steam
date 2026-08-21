package link.e4steam.api.event;

/** Observational event emitted before runtime-scoped resources close. */
public final class RuntimeStoppingEvent implements ApiEvent {
    /** Typed event key. */
    public static final EventType<RuntimeStoppingEvent> TYPE =
            new EventType<>("e4steam:runtime_stopping", RuntimeStoppingEvent.class);

    private final long occurredAtEpochMillis;

    /** Creates an immutable runtime-stopping event. */
    public RuntimeStoppingEvent(long occurredAtEpochMillis) {
        if (occurredAtEpochMillis < 0) {
            throw new IllegalArgumentException("occurredAtEpochMillis must be non-negative");
        }
        this.occurredAtEpochMillis = occurredAtEpochMillis;
    }

    @Override
    public long occurredAtEpochMillis() { return occurredAtEpochMillis; }
}
