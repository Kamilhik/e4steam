package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamKnownPeerSessionGateTest {
    private static final long REMOTE_ID = 76561198000000001L;

    @Test
    void closedGenerationCannotBeRevivedBeforeItsNativeSessionDrains() {
        SteamKnownPeerSessionGate gate = new SteamKnownPeerSessionGate(3_000L);

        assertTrue(gate.mayProactivelyAccept(REMOTE_ID, false, false, true, 1_000L));
        gate.defer(REMOTE_ID, 1_000L);
        assertFalse(gate.mayProactivelyAccept(REMOTE_ID, false, false, true, 3_999L));
        assertTrue(gate.mayProactivelyAccept(REMOTE_ID, false, false, true, 4_000L));
    }

    @Test
    void repeatedCloseOnlyExtendsTheQuarantine() {
        SteamKnownPeerSessionGate gate = new SteamKnownPeerSessionGate(3_000L);

        gate.defer(REMOTE_ID, 1_000L);
        gate.defer(REMOTE_ID, 2_000L);
        assertFalse(gate.mayProactivelyAccept(REMOTE_ID, false, false, true, 4_999L));
        assertTrue(gate.mayProactivelyAccept(REMOTE_ID, false, false, true, 5_000L));
    }

    @Test
    void realSessionRequestBypassesTheCompatibilityQuarantine() {
        SteamKnownPeerSessionGate gate = new SteamKnownPeerSessionGate(3_000L);

        gate.defer(REMOTE_ID, 1_000L);
        gate.observeNewSession(REMOTE_ID);
        assertTrue(gate.mayProactivelyAccept(REMOTE_ID, false, false, true, 1_001L));
    }

    @Test
    void activePendingAndFullStatesRemainExclusive() {
        SteamKnownPeerSessionGate gate = new SteamKnownPeerSessionGate(3_000L);

        assertFalse(gate.mayProactivelyAccept(REMOTE_ID, true, false, true, 10L));
        assertFalse(gate.mayProactivelyAccept(REMOTE_ID, false, true, true, 10L));
        assertFalse(gate.mayProactivelyAccept(REMOTE_ID, false, false, false, 10L));
    }

    @Test
    void runtimeResetClearsOldGenerationState() {
        SteamKnownPeerSessionGate gate = new SteamKnownPeerSessionGate(3_000L);

        gate.defer(REMOTE_ID, 1_000L);
        gate.clear();
        assertTrue(gate.mayProactivelyAccept(REMOTE_ID, false, false, true, 1_001L));
    }
}
