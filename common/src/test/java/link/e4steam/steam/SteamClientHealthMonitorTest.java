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
            return true;
        }));
        assertTrue(monitor.isHealthy(200L, () -> {
            calls.incrementAndGet();
            return false;
        }));
        assertTrue(monitor.isHealthy(300L, () -> {
            calls.incrementAndGet();
            return false;
        }));
        assertFalse(monitor.isHealthy(400L, () -> {
            calls.incrementAndGet();
            return false;
        }));
        assertTrue(calls.get() == 4);
    }

    @Test
    void unconfirmedProcessProbeDoesNotTerminateInitializedSession() {
        SteamClientHealthMonitor monitor = new SteamClientHealthMonitor(true, 5_000L, 3);
        for (long now = 0; now <= 120_000L; now += 5_000L) {
            assertTrue(monitor.isHealthy(now, () -> false));
        }
        assertTrue(monitor.isHealthy(125_000L, () -> true));
        assertTrue(monitor.isHealthy(130_000L, () -> false));
        assertTrue(monitor.isHealthy(135_000L, () -> false));
        assertFalse(monitor.isHealthy(140_000L, () -> false));
    }

    @Test
    void resetRequiresFreshProbeConfirmation() {
        SteamClientHealthMonitor monitor = new SteamClientHealthMonitor(true, 10L, 1);
        assertTrue(monitor.isHealthy(0L, () -> true));
        assertTrue(monitor.isHealthy(10L, () -> true));
        assertFalse(monitor.isHealthy(20L, () -> false));
        monitor.reset();
        assertTrue(monitor.isHealthy(30L, () -> false));
        assertTrue(monitor.isHealthy(40L, () -> false));
        assertTrue(monitor.isHealthy(50L, () -> false));
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
