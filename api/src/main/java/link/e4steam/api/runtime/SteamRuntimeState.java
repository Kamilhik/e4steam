package link.e4steam.api.runtime;

/** Sanitized Steam runtime state without native handles. */
public enum SteamRuntimeState {
    /** No startup has been requested. */
    NEW,
    /** Startup is in progress. */
    STARTING,
    /** Steam transport is ready. */
    READY,
    /** Shutdown is in progress. */
    STOPPING,
    /** Runtime stopped normally. */
    STOPPED,
    /** Runtime failed with a sanitized category. */
    FAILED,
    /** The current platform or mode has no Steam backend. */
    UNSUPPORTED
}
