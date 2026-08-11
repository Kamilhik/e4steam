package link.e4steam.api;

/** Safe retry guidance that never instructs an addon to loop indefinitely. */
public enum Retryability {
    /** Retrying the same request cannot succeed without a state or input change. */
    PERMANENT,
    /** A bounded retry may succeed after the service-provided delay. */
    TRANSIENT,
    /** The caller must wait for a documented lifecycle transition. */
    AFTER_STATE_CHANGE
}
