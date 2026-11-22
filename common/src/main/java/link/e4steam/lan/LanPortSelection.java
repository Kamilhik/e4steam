package link.e4steam.lan;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/** Ephemeral port selection for the modern integrated-world dialog only. */
public final class LanPortSelection {
    private LanPortSelection() { }

    public static int availablePort() {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(0));
            return socket.getLocalPort();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not select a free LAN port", failure);
        }
    }
}
