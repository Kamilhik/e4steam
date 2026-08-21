package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SteamPeerPrivacyTest {
    @Test
    void peerProjectionIsDeterministicGenerationBoundAndDoesNotExposeSteamId() {
        long steamId = 76561198012345678L;
        String first = SteamPeerPrivacy.opaquePeerId(5L, steamId);
        assertEquals(first, SteamPeerPrivacy.opaquePeerId(5L, steamId));
        assertNotEquals(first, SteamPeerPrivacy.opaquePeerId(6L, steamId));
        assertFalse(first.contains(Long.toUnsignedString(steamId)));
        assertTrue(first.matches("p_[A-Za-z0-9_-]{24}"));
        assertEquals("dedicated_2s", SteamPeerPrivacy.dedicatedSessionId(100L));
    }

    @Test
    void rejectsMissingAuthenticationContext() {
        assertThrows(IllegalArgumentException.class,
                () -> SteamPeerPrivacy.opaquePeerId(0L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> SteamPeerPrivacy.opaquePeerId(1L, 0L));
    }
}
