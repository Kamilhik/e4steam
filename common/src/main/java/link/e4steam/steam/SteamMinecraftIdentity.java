package link.e4steam.steam;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    /**
     * Keeps the Minecraft nickname exactly as supplied by Minecraft. The name
     * is presentation only; Steam identity and the derived UUID remain the
     * authentication and authorization keys.
     */
    public static String preserveMinecraftName(String minecraftName) {
        if (minecraftName == null) {
            throw new IllegalArgumentException("Minecraft name is required");
        }
        return minecraftName;
    }

    /**
     * Reads the unchanged Minecraft nickname across both Authlib layouts.
     * Authlib 7 replaced the legacy {@code getName()} accessor with the
     * {@code name()} record accessor used by Minecraft 26.x. Keeping this
     * narrow reflection bridge here prevents one compiled mod JAR from
     * linking permanently to only one of those incompatible methods.
     */
    public static String profileName(Object gameProfile) {
        if (gameProfile == null) {
            throw new IllegalArgumentException("Game profile is required");
        }
        Class<?> profileType = gameProfile.getClass();
        for (String accessorName : new String[]{"name", "getName"}) {
            try {
                Method accessor = profileType.getMethod(accessorName);
                Object value = accessor.invoke(gameProfile);
                if (value instanceof String) {
                    return preserveMinecraftName((String) value);
                }
                throw new IllegalStateException(
                        "Game profile name accessor did not return a string");
            } catch (NoSuchMethodException ignored) {
                // Try the accessor from the other Authlib generation.
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(
                        "Game profile name accessor is not accessible", exception);
            } catch (InvocationTargetException exception) {
                throw new IllegalStateException(
                        "Game profile name accessor failed", exception.getCause());
            }
        }
        throw new IllegalStateException(
                "Unsupported Authlib GameProfile: no name accessor");
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
