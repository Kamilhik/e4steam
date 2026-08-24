package link.e4steam.api.runtime;

/** Safe compatibility markers that do not imply an unperformed runtime test. */
public enum CompatibilityFlag {
    /** Current loader adapter compiled successfully. */
    LOADER_ADAPTER_PRESENT,
    /** Steam client runtime is available in this process. */
    STEAM_CLIENT_BACKEND_AVAILABLE,
    /** Dedicated GameServer backend is available in this process. */
    DEDICATED_BACKEND_AVAILABLE,
    /** Runtime combination is experimental rather than verified supported. */
    EXPERIMENTAL_COMBINATION
}
