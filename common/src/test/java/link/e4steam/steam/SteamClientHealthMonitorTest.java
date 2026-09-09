package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamClientHealthMonitorTest {
    @Test
    void nativeProbeIsThrottledAndRequiresConsecutiveFailures() {
        SteamClientHealthMonitor monitor = new SteamClientHealthMonitor(true, 100L, 3);
        AtomicInteger calls = new AtomicInteger();

        assertTrue(monitor.isHealthy(0L, () -> {
            calls.incrementAndGet();
            return false;
        }));
        assertTrue(monitor.isHealthy(99L, () -> {
            calls.incrementAndGet();
            return false;
        }));
        assertTrue(monitor.isHealthy(100L, () -> {
            calls.incrementAndGet();
            return false;
        }));
        assertTrue(monitor.isHealthy(200L, () -> {
            calls.incrementAndGet();
            return false;
        }));
        assertFalse(monitor.isHealthy(300L, () -> {
            calls.incrementAndGet();
            return false;
        }));
        assertTrue(calls.get() == 3);
    }

    @Test
    void successfulProbeResetsFailureSequence() {
        SteamClientHealthMonitor monitor = new SteamClientHealthMonitor(true, 10L, 2);

        assertTrue(monitor.isHealthy(0L, () -> false));
        assertTrue(monitor.isHealthy(10L, () -> false));
        assertTrue(monitor.isHealthy(20L, () -> true));
        assertTrue(monitor.isHealthy(30L, () -> false));
        assertFalse(monitor.isHealthy(40L, () -> false));
    }

    @Test
    void disabledProbeNeverCallsNativeBoundary() {
        SteamClientHealthMonitor monitor = new SteamClientHealthMonitor(false, 10L, 1);

        assertTrue(monitor.isHealthy(0L, () -> {
            throw new AssertionError("native probe must not be called");
        }));
        assertTrue(monitor.isHealthy(10_000L, () -> {
            throw new AssertionError("native probe must not be called");
        }));
    }

    @Test
    void sandboxedAndMacProcessesSkipNativeProbe() {
        Map<String, String> flatpak = new HashMap<>();
        flatpak.put("FLATPAK_ID", "org.prismlauncher.PrismLauncher");
        Map<String, String> snap = new HashMap<>();
        snap.put("SNAP", "/snap/prismlauncher/current");

        assertFalse(SteamClientHealthMonitor.shouldUseNativeProbe("Linux", flatpak, false));
        assertFalse(SteamClientHealthMonitor.shouldUseNativeProbe("Linux", snap, false));
        assertFalse(SteamClientHealthMonitor.shouldUseNativeProbe(
                "Linux", Collections.emptyMap(), true
        ));
        assertFalse(SteamClientHealthMonitor.shouldUseNativeProbe(
                "Mac OS X", Collections.emptyMap(), false
        ));
        assertTrue(SteamClientHealthMonitor.shouldUseNativeProbe(
                "Linux", Collections.emptyMap(), false
        ));
        assertTrue(SteamClientHealthMonitor.shouldUseNativeProbe(
                "Windows 11", Collections.emptyMap(), false
        ));
    }
}
