package link.e4steam.retro;

import java.net.InetSocketAddress;

/** Thin Minecraft-version adapter; no Steam or loader implementation leaks through it. */
public interface RetroPlatform {
    void execute(Runnable action);

    void connect(InetSocketAddress localAddress, String displayName);

    void showMessage(String message);
}
