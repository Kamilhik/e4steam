package link.e4steam.retro;

import java.net.InetSocketAddress;

/** Thin Minecraft-version adapter; no Steam or loader implementation leaks through it. */
public interface RetroPlatform {
    void execute(Runnable action);

    void connect(InetSocketAddress localAddress, String displayName);

    void showMessage(String message);

    default void showSharingReady(String endpoint) {
        showMessage("e4steam: " + endpoint);
        showMessage("/e4steam invite - invite Steam friends");
        showMessage("/e4steam stop - stop sharing");
    }
}
