package link.e4steam.api.runtime;

/** Distribution role of the current e4steam process. */
public enum RuntimeMode {
    /** Minecraft client before an integrated world opens. */
    CLIENT,
    /** Client process currently owning an integrated server. */
    INTEGRATED_SERVER,
    /** Headless dedicated server backend, when implemented and enabled. */
    DEDICATED_SERVER
}
