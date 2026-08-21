package link.e4steam.steam;

/** Minimal transport contract shared by integrated and dedicated TCP bridges. */
interface SteamBridgeRuntime {
    boolean sendData(SteamConnectionBridge bridge, byte[] payload);

    boolean sendFin(SteamConnectionBridge bridge);

    boolean sendReset(SteamConnectionBridge bridge);

    void closeUdpBridge(SteamConnectionBridge bridge);

    void unregister(SteamConnectionBridge bridge);
}
