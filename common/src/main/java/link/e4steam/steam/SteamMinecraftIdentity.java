package link.e4steam.steam;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Builds the Minecraft identity used by an authenticated Steam bridge guest.
 * Neither value depends on the name supplied by the connecting Minecraft
 * client, so changing that name cannot impersonate the integrated-server
 * owner or evade UUID-based access lists.
 */
public final class SteamMinecraftIdentity {
    private static final String UUID_NAMESPACE = "e4steam:steam-identity:v1:";
    private static final String NAME_PREFIX = "s_";

    private SteamMinecraftIdentity() {
    }

    /** Returns a stable, versioned Minecraft UUID for one authenticated SteamID. */
    public static UUID uuid(long steamId) {
        requireSteamId(steamId);
        String material = UUID_NAMESPACE + Long.toUnsignedString(steamId);
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns a conservative Minecraft-compatible name derived from SteamID.
     * A Steam persona name can still be shown separately in consented UI; it
     * is not an authentication identifier.
     */
    public static String safeName(long steamId) {
        requireSteamId(steamId);
        return NAME_PREFIX + Long.toUnsignedString(steamId, 36);
    }

    /** Steam bridge guests never receive the integrated-server owner bypass. */
    public static boolean allowSingleplayerOwnerBypass(
            long authenticatedSteamId,
            boolean vanillaOwnerMatch
    ) {
        return authenticatedSteamId == 0 && vanillaOwnerMatch;
    }

    private static void requireSteamId(long steamId) {
        if (steamId == 0) {
            throw new IllegalArgumentException("SteamID must be authenticated");
        }
    }
}
