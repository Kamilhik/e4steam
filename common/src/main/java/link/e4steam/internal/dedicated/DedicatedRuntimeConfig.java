package link.e4steam.internal.dedicated;

import link.e4steam.api.dedicated.DedicatedServerService.DedicatedAccessMode;
import link.e4steam.api.dedicated.DedicatedServerService.DedicatedConfigSnapshot;
import link.e4steam.steam.SteamGameServerRuntimeBackend;

import java.net.InetAddress;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Bounded secret-free dedicated configuration resolved before native startup. */
public final class DedicatedRuntimeConfig {
    private static final int APP_ID = 480;
    private final boolean enabled;
    private final DedicatedAccessMode accessMode;
    private final int maxPeers;
    private final int queryPort;
    private final String serverName;
    private final Set<Long> whitelist;

    public DedicatedRuntimeConfig(boolean enabled, DedicatedAccessMode accessMode,
                                  int maxPeers, int queryPort, String serverName) {
        this(enabled, accessMode, maxPeers, queryPort, serverName, Collections.<Long>emptySet());
    }

    public DedicatedRuntimeConfig(boolean enabled, DedicatedAccessMode accessMode,
                                  int maxPeers, int queryPort, String serverName,
                                  Set<Long> whitelist) {
        this.enabled = enabled;
        this.accessMode = java.util.Objects.requireNonNull(accessMode, "accessMode");
        if (accessMode == DedicatedAccessMode.CUSTOM) {
            throw new IllegalArgumentException("Custom access requires an installed provider");
        }
        if (maxPeers < 1 || maxPeers > 64) throw new IllegalArgumentException("maxPeers");
        if (queryPort < 0 || queryPort > 65535) throw new IllegalArgumentException("queryPort");
        String name = java.util.Objects.requireNonNull(serverName, "serverName").trim();
        if (name.isEmpty() || name.length() > 64) throw new IllegalArgumentException("serverName");
        for (int index = 0; index < name.length(); index++) {
            if (Character.isISOControl(name.charAt(index))) {
                throw new IllegalArgumentException("serverName");
            }
        }
        this.maxPeers = maxPeers;
        this.queryPort = queryPort;
        this.serverName = name;
        LinkedHashSet<Long> checkedWhitelist = new LinkedHashSet<>();
        if (whitelist != null) {
            if (whitelist.size() > 1_024) throw new IllegalArgumentException("whitelist");
            for (Long value : whitelist) {
                if (value == null || value == 0L) throw new IllegalArgumentException("whitelist");
                checkedWhitelist.add(value);
            }
        }
        this.whitelist = Collections.unmodifiableSet(checkedWhitelist);
    }

    public static DedicatedRuntimeConfig fromEnvironment(
            Map<String, String> environment,
            java.util.Properties properties
    ) {
        Map<String, String> env = environment == null
                ? java.util.Collections.<String, String>emptyMap() : environment;
        java.util.Properties props = properties == null ? new java.util.Properties() : properties;
        boolean enabled = bool(first(props.getProperty("e4steam.dedicated.enabled"),
                env.get("E4STEAM_DEDICATED_ENABLED")), false);
        String modeText = first(props.getProperty("e4steam.dedicated.access"),
                env.get("E4STEAM_DEDICATED_ACCESS"));
        DedicatedAccessMode mode = modeText == null ? DedicatedAccessMode.PRIVATE
                : DedicatedAccessMode.valueOf(modeText.trim().toUpperCase(Locale.ROOT));
        int maxPeers = number(first(props.getProperty("e4steam.dedicated.maxPeers"),
                env.get("E4STEAM_DEDICATED_MAX_PEERS")), 8, 1, 64);
        int queryPort = number(first(props.getProperty("e4steam.dedicated.queryPort"),
                env.get("E4STEAM_DEDICATED_QUERY_PORT")), 65535, 0, 65535);
        String name = first(props.getProperty("e4steam.dedicated.name"),
                env.get("E4STEAM_DEDICATED_NAME"));
        Set<Long> whitelist = parseSteamIds(first(
                props.getProperty("e4steam.dedicated.whitelist"),
                env.get("E4STEAM_DEDICATED_WHITELIST")));
        return new DedicatedRuntimeConfig(enabled, mode, maxPeers, queryPort,
                name == null ? "e4steam Minecraft server" : name, whitelist);
    }

    public static DedicatedRuntimeConfig load(
            Path configFile,
            Map<String, String> environment,
            java.util.Properties systemProperties
    ) throws IOException {
        java.util.Properties merged = DedicatedConfigFile.load(configFile);
        if (systemProperties != null) {
            for (String name : systemProperties.stringPropertyNames()) {
                if (name.startsWith("e4steam.dedicated.")) {
                    merged.setProperty(name, systemProperties.getProperty(name));
                }
            }
        }
        return fromEnvironment(environment, merged);
    }

    public boolean enabled() { return enabled; }
    public DedicatedAccessMode accessMode() { return accessMode; }
    public int maxPeers() { return maxPeers; }
    public int queryPort() { return queryPort; }
    public String serverName() { return serverName; }
    public boolean isWhitelisted(long steamId) { return whitelist.contains(steamId); }

    public SteamGameServerRuntimeBackend.Config backend(int minecraftPort) {
        return new SteamGameServerRuntimeBackend.Config(
                APP_ID, minecraftPort, queryPort, maxPeers, serverName, null
        );
    }

    public DedicatedConfigSnapshot safeSnapshot() {
        return new DedicatedConfigSnapshot(1, accessMode, maxPeers, false, "ANONYMOUS");
    }

    public void validateMinecraftBind(InetAddress bindAddress) {
        if (bindAddress == null || !bindAddress.isLoopbackAddress()) {
            throw new IllegalStateException(
                    "Dedicated e4steam requires server-ip=127.0.0.1 or ::1"
            );
        }
    }

    @Override public String toString() {
        return "DedicatedRuntimeConfig{enabled=" + enabled + ", access=" + accessMode
                + ", maxPeers=" + maxPeers + ", queryPort=" + queryPort
                + ", whitelistEntries=" + whitelist.size()
                + ", login=ANONYMOUS, publication=false}";
    }

    private static String first(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first.trim();
        return second == null || second.trim().isEmpty() ? null : second.trim();
    }

    private static int number(String value, int fallback, int minimum, int maximum) {
        if (value == null) return fallback;
        int parsed;
        try { parsed = Integer.parseInt(value); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException("Invalid number"); }
        if (parsed < minimum || parsed > maximum) throw new IllegalArgumentException("Out of range");
        return parsed;
    }

    private static boolean bool(String value, boolean fallback) {
        if (value == null) return fallback;
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) return true;
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) return false;
        throw new IllegalArgumentException("Invalid boolean");
    }

    private static Set<Long> parseSteamIds(String value) {
        if (value == null) return Collections.emptySet();
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        String[] parts = value.split(",", -1);
        if (parts.length > 1_024) throw new IllegalArgumentException("Too many whitelist entries");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) throw new IllegalArgumentException("Invalid whitelist entry");
            try {
                long parsed = Long.parseUnsignedLong(trimmed);
                if (parsed == 0L) throw new IllegalArgumentException("Invalid whitelist entry");
                ids.add(parsed);
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("Invalid whitelist entry");
            }
        }
        return ids;
    }
}
