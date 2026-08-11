package link.e4steam.api;

/** Stable machine-readable failure categories returned by addon API operations. */
public enum ApiErrorCode {
    /** An argument failed validation. */
    INVALID_ARGUMENT,
    /** The scoped addon lacks a required capability. */
    CAPABILITY_DENIED,
    /** The requested service is unavailable or not ready. */
    UNAVAILABLE,
    /** A resource belongs to an old or closed generation. */
    STALE_HANDLE,
    /** The addon and API versions are incompatible. */
    INCOMPATIBLE_VERSION,
    /** An operation exceeded its time budget. */
    TIMEOUT,
    /** An operation was cancelled. */
    CANCELLED,
    /** A bounded rate was exceeded. */
    RATE_LIMITED,
    /** A bounded queue or registration table is full. */
    QUEUE_FULL,
    /** Addon-controlled code failed in isolation. */
    ADDON_FAILURE,
    /** Steam transport is unavailable. */
    TRANSPORT_UNAVAILABLE,
    /** A mandatory security rule rejected the operation. */
    SECURITY_REJECTION,
    /** The current Minecraft, loader, platform or mode cannot provide the operation. */
    UNSUPPORTED
}
