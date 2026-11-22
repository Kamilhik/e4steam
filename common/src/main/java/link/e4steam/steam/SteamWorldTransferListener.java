package link.e4steam.steam;

/** Internal, client-only transfer hook. It never authorizes a Minecraft connection. */
public interface SteamWorldTransferListener {
    boolean accepts(long steamId);
    boolean retains(long steamId);
    long[] expectedPeers();
    void packet(long steamId, byte[] bytes);
    void disconnected(long steamId);
    void stopped();
}
