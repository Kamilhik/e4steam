package link.e4steam;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Java 8 platform facts used by the isolated retro runtime. */
public final class Agnos {
    private static volatile boolean client = true;

    private Agnos() {
    }

    public static boolean isClient() {
        return client;
    }

    public static void installPhysicalSide(boolean physicalClient) {
        client = physicalClient;
    }

    public static Path configDir() {
        String configured = System.getProperty("e4steam.configDir", "").trim();
        if (!configured.isEmpty()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.dir", "."), "config")
                .toAbsolutePath().normalize();
    }

    public static boolean proactivelyAcceptSteamPeerSessions() {
        return true;
    }

    public static boolean autoRestartBrokenSteamSessionForHandshake() {
        return true;
    }
}
