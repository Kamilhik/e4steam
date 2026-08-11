package link.e4steam.api.addon;

/** Observable lifecycle state of a discovered addon. */
public enum AddonState {
    /** Metadata was discovered by the normal mod loader. */
    DISCOVERED,
    /** Metadata and dependencies are being validated. */
    VALIDATING,
    /** The bounded initialization callback is running. */
    INITIALIZING,
    /** Initialization completed and resources are active. */
    ACTIVE,
    /** Policy or user configuration disabled the addon. */
    DISABLED,
    /** Validation or an isolated callback failed. */
    FAILED,
    /** Owned resources were closed during shutdown. */
    STOPPED
}
