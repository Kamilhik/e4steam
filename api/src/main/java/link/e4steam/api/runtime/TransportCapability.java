package link.e4steam.api.runtime;

/** Read-only transport feature availability. */
public enum TransportCapability {
    /** Reliable Minecraft byte-stream bridging. */
    RELIABLE_STREAM,
    /** Unreliable bounded datagrams. */
    DATAGRAM,
    /** Steam lobby invitation flow. */
    LOBBY_INVITES,
    /** Version-negotiated addon channels, once provided by a later service. */
    ADDON_CHANNELS
}
