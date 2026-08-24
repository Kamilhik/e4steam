package link.e4steam.api.runtime;

/** High-level lifecycle phase safe for addon readiness decisions. */
public enum LifecyclePhase {
    /** Core is being constructed. */
    BOOTSTRAP,
    /** Addon descriptors are being validated. */
    ADDON_INITIALIZATION,
    /** Runtime is available but no world is open. */
    IDLE,
    /** An integrated world is opening. */
    WORLD_OPENING,
    /** An integrated world is active. */
    WORLD_ACTIVE,
    /** A world or runtime is draining connections. */
    DRAINING,
    /** Deterministic shutdown is running. */
    SHUTTING_DOWN,
    /** All scoped resources are closed. */
    STOPPED
}
