package link.e4steam.retro;

import link.e4steam.internal.dedicated.DedicatedConfigFile;
import link.e4steam.internal.dedicated.DedicatedServerPropertiesValidator;
import link.e4steam.steam.SteamRuntimeBackend;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Strict Java 8 projection of the shared dedicated-server configuration. */
final class RetroDedicatedConfig {
    enum AccessMode { PRIVATE, WHITELIST, UNLISTED }

    private final boolean enabled;
    private final AccessMode accessMode;
    private final int maxPeers;
    private final int queryPort;
    private final String serverName;
    private final Set<Long> whitelist;

    private RetroDedicatedConfig(boolean enabled, AccessMode accessMode, int maxPeers,
                                 int queryPort, String serverName, Set<Long> whitelist) {
        this.enabled = enabled;
        this.accessMode = accessMode;
        this.maxPeers = maxPeers;
        this.queryPort = queryPort;
        this.serverName = serverName;
        this.whitelist = Collections.unmodifiableSet(new LinkedHashSet<Long>(whitelist));
    }

    static RetroDedicatedConfig load() throws IOException {
        Path file = Paths.get(System.getProperty("user.dir", "."),
                "config", "e4steam-dedicated.toml");
        Properties values = DedicatedConfigFile.load(file);
        Properties system = System.getProperties();
        for (String key : system.stringPropertyNames()) {
            if (key.startsWith("e4steam.dedicated.")) {
                values.setProperty(key, system.getProperty(key));
            }
        }
        Map<String, String> environment = System.getenv();
        boolean enabled = bool(first(values.getProperty("e4steam.dedicated.enabled"),
                environment.get("E4STEAM_DEDICATED_ENABLED")), false);
        String accessText = first(values.getProperty("e4steam.dedicated.access"),
                environment.get("E4STEAM_DEDICATED_ACCESS"));
        AccessMode access = accessText == null ? AccessMode.PRIVATE
                : AccessMode.valueOf(accessText.toUpperCase(Locale.ROOT));
        int maxPeers = number(first(values.getProperty("e4steam.dedicated.maxPeers"),
                environment.get("E4STEAM_DEDICATED_MAX_PEERS")), 8, 1, 64);
        int queryPort = number(first(values.getProperty("e4steam.dedicated.queryPort"),
                environment.get("E4STEAM_DEDICATED_QUERY_PORT")), 65535, 0, 65535);
        String name = first(values.getProperty("e4steam.dedicated.name"),
                environment.get("E4STEAM_DEDICATED_NAME"));
        if (name == null) name = "e4steam Minecraft server";
        if (name.length() > 64 || hasControl(name)) {
            throw new IllegalArgumentException("Invalid dedicated server name");
        }
        Set<Long> whitelist = steamIds(first(
                values.getProperty("e4steam.dedicated.whitelist"),
                environment.get("E4STEAM_DEDICATED_WHITELIST")));
        return new RetroDedicatedConfig(enabled, access, maxPeers, queryPort, name, whitelist);
    }

    static void validateServerProperties() {
        Path path = Paths.get(System.getProperty("user.dir", "."), "server.properties")
                .toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(
                    "e4steam dedicated requires a regular server.properties file");
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read server.properties", failure);
        }
        DedicatedServerPropertiesValidator.Validation result =
                DedicatedServerPropertiesValidator.validate(properties);
        if (!result.allowed()) {
            throw new IllegalStateException(
                    "Unsafe e4steam dedicated server.properties: " + result.category());
        }
        if (Boolean.parseBoolean(properties.getProperty("online-mode", "true"))) {
            throw new IllegalStateException(
                    "e4steam dedicated requires online-mode=false; Steam authenticates every ingress");
        }
    }

    SteamRuntimeBackend.Config backend(int minecraftPort) {
        return new SteamRuntimeBackend.Config(
                480, minecraftPort, queryPort, maxPeers, serverName, null);
    }

    boolean enabled() { return enabled; }
    int maxPeers() { return maxPeers; }
    AccessMode accessMode() { return accessMode; }
    boolean allows(long steamId) {
        return accessMode == AccessMode.UNLISTED || whitelist.contains(Long.valueOf(steamId));
    }

    private static String first(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first.trim();
        return second == null || second.trim().isEmpty() ? null : second.trim();
    }

    private static boolean bool(String value, boolean fallback) {
        if (value == null) return fallback;
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) return true;
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) return false;
        throw new IllegalArgumentException("Invalid dedicated boolean");
    }

    private static int number(String value, int fallback, int minimum, int maximum) {
        if (value == null) return fallback;
        int parsed;
        try { parsed = Integer.parseInt(value); }
        catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid dedicated number");
        }
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException("Dedicated number is out of range");
        }
        return parsed;
    }

    private static Set<Long> steamIds(String value) {
        if (value == null) return Collections.emptySet();
        String[] parts = value.split(",", -1);
        if (parts.length > 1024) throw new IllegalArgumentException("Too many Steam IDs");
        LinkedHashSet<Long> result = new LinkedHashSet<Long>();
        for (String part : parts) {
            try {
                long parsed = Long.parseUnsignedLong(part.trim());
                if (parsed == 0L || !result.add(Long.valueOf(parsed))) {
                    throw new IllegalArgumentException("Invalid dedicated Steam ID");
                }
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("Invalid dedicated Steam ID");
            }
        }
        return result;
    }

    private static boolean hasControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) return true;
        }
        return false;
    }
}
