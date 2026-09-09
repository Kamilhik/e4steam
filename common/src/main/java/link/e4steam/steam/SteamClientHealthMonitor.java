package link.e4steam.steam;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Throttles the optional native Steam-client probe and tolerates transient
 * failures. SteamAPI initialization remains the authoritative startup check.
 */
final class SteamClientHealthMonitor {
    static final long DEFAULT_PROBE_INTERVAL_MILLIS = 5_000L;
    static final int DEFAULT_FAILURE_THRESHOLD = 3;

    private final boolean nativeProbeEnabled;
    private final long probeIntervalMillis;
    private final int failureThreshold;

    private boolean probeScheduled;
    private long nextProbeMillis;
    private int consecutiveFailures;

    SteamClientHealthMonitor(
            boolean nativeProbeEnabled,
            long probeIntervalMillis,
            int failureThreshold
    ) {
        if (probeIntervalMillis <= 0) {
            throw new IllegalArgumentException("probeIntervalMillis must be positive");
        }
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        this.nativeProbeEnabled = nativeProbeEnabled;
        this.probeIntervalMillis = probeIntervalMillis;
        this.failureThreshold = failureThreshold;
    }

    static SteamClientHealthMonitor forCurrentProcess() {
        boolean flatpakMarker = false;
        Map<String, String> environment = java.util.Collections.emptyMap();
        try {
            flatpakMarker = Files.exists(Paths.get("/.flatpak-info"));
        } catch (SecurityException ignored) {
            // Environment variables below still identify normal Flatpak/Snap
            // launches when direct file access is restricted.
        }
        try {
            environment = System.getenv();
        } catch (SecurityException ignored) {
            // A restricted environment simply falls back to the OS policy.
        }
        return new SteamClientHealthMonitor(
                shouldUseNativeProbe(
                        System.getProperty("os.name", ""),
                        environment,
                        flatpakMarker
                ),
                DEFAULT_PROBE_INTERVAL_MILLIS,
                DEFAULT_FAILURE_THRESHOLD
        );
    }

    static boolean shouldUseNativeProbe(
            String osName,
            Map<String, String> environment,
            boolean flatpakMarker
    ) {
        String normalizedOs = osName == null
                ? "" : osName.trim().toLowerCase(Locale.ROOT);

        // steamworks4j warns against this native probe on macOS. Sandboxed
        // launchers can also initialize Steam successfully while this probe
        // incorrectly reports that the client is absent.
        if (normalizedOs.contains("mac") || normalizedOs.contains("darwin")) {
            return false;
        }
        if (flatpakMarker || hasValue(environment, "FLATPAK_ID")
                || "flatpak".equalsIgnoreCase(value(environment, "container"))
                || hasValue(environment, "SNAP")
                || hasValue(environment, "SNAP_NAME")) {
            return false;
        }

        return normalizedOs.contains("win")
                || normalizedOs.contains("linux")
                || normalizedOs.contains("nix")
                || normalizedOs.contains("nux");
    }

    private static boolean hasValue(Map<String, String> environment, String name) {
        return !value(environment, name).trim().isEmpty();
    }

    private static String value(Map<String, String> environment, String name) {
        if (environment == null) return "";
        String value = environment.get(name);
        return value == null ? "" : value;
    }

    boolean isHealthy(long nowMillis, BooleanSupplier nativeProbe) {
        if (!nativeProbeEnabled) {
            return true;
        }
        if (!probeScheduled) {
            probeScheduled = true;
            nextProbeMillis = saturatedAdd(nowMillis, probeIntervalMillis);
            return true;
        }
        if (nowMillis < nextProbeMillis) {
            return true;
        }

        nextProbeMillis = saturatedAdd(nowMillis, probeIntervalMillis);
        if (nativeProbe.getAsBoolean()) {
            consecutiveFailures = 0;
            return true;
        }

        consecutiveFailures++;
        return consecutiveFailures < failureThreshold;
    }

    void reset() {
        probeScheduled = false;
        nextProbeMillis = 0L;
        consecutiveFailures = 0;
    }

    private static long saturatedAdd(long left, long right) {
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
