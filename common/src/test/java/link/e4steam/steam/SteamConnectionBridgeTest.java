package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamConnectionBridgeTest {
    @Test
    void gracefulCloseDeadlineExpiresOnlyAtTheDeadline() {
        assertFalse(SteamConnectionBridge.isGracefulCloseExpired(Long.MAX_VALUE, 100));
        assertFalse(SteamConnectionBridge.isGracefulCloseExpired(101, 100));
        assertTrue(SteamConnectionBridge.isGracefulCloseExpired(100, 100));
    }

    @Test
    void forgeDataWaitsForReadyButHasABoundedCompatibilityFallback() {
        assertTrue(SteamConnectionBridge.isPeerReadyOrFallbackReached(0, false, 1));
        assertFalse(SteamConnectionBridge.isPeerReadyOrFallbackReached(200, false, 199));
        assertTrue(SteamConnectionBridge.isPeerReadyOrFallbackReached(200, true, 199));
        assertTrue(SteamConnectionBridge.isPeerReadyOrFallbackReached(200, false, 200));
    }
}
