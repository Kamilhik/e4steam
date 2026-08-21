package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamOverlayFallbackTest {
    @Test
    void unixFallsBackOnlyWhenOverlayIsUnavailable() {
        assertTrue(SteamLobbyManager.shouldUseStandaloneFriends(true, false));
        assertFalse(SteamLobbyManager.shouldUseStandaloneFriends(true, true));
    }

    @Test
    void windowsNeverUsesTheUnixFallback() {
        assertFalse(SteamLobbyManager.shouldUseStandaloneFriends(false, false));
        assertFalse(SteamLobbyManager.shouldUseStandaloneFriends(false, true));
    }
}
