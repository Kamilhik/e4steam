package link.e4steam.api.runtime;

/** Normalized operating system reported without filesystem paths. */
public enum Platform {
    /** Microsoft Windows. */
    WINDOWS,
    /** Linux desktop or SteamOS. */
    LINUX,
    /** Apple macOS; availability must still be checked separately. */
    MACOS,
    /** Unrecognized operating system. */
    UNKNOWN
}
