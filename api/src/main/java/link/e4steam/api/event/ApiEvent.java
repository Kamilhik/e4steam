package link.e4steam.api.event;

/** Immutable observational event; security gates are never cancellable events. */
public interface ApiEvent {
    /** Returns event creation time in Unix epoch milliseconds. */
    long occurredAtEpochMillis();
}
