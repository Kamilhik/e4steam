package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamSessionRequestPolicyTest {
    @Test
    void acceptsTransportWhileHostingSoOpenCanAuthenticateTheToken() {
        assertTrue(SteamRuntime.shouldAcceptSessionRequest(false, true, false));
    }

    @Test
    void acceptsExistingAndKnownSocialPeers() {
        assertTrue(SteamRuntime.shouldAcceptSessionRequest(true, false, false));
        assertTrue(SteamRuntime.shouldAcceptSessionRequest(false, false, true));
    }

    @Test
    void rejectsUnrelatedTrafficWhenIdle() {
        assertFalse(SteamRuntime.shouldAcceptSessionRequest(false, false, false));
    }

    @Test
    void restartsForgeHandshakesAndAlwaysRestartsDedicatedHandshakes() {
        assertTrue(SteamRuntime.autoRestartsBrokenSession(
                SteamOutboundQueue.Kind.OPEN,
                true
        ));
        assertTrue(SteamRuntime.autoRestartsBrokenSession(
                SteamOutboundQueue.Kind.OPEN_ACK,
                true
        ));
        assertFalse(SteamRuntime.autoRestartsBrokenSession(
                SteamOutboundQueue.Kind.DATA,
                true
        ));
        assertFalse(SteamRuntime.autoRestartsBrokenSession(
                SteamOutboundQueue.Kind.FIN,
                true
        ));
        assertFalse(SteamRuntime.autoRestartsBrokenSession(
                SteamOutboundQueue.Kind.BRIDGE_READY,
                true
        ));
        assertFalse(SteamRuntime.autoRestartsBrokenSession(
                SteamOutboundQueue.Kind.OPEN,
                false
        ));
        assertTrue(SteamRuntime.autoRestartsBrokenSession(
                SteamOutboundQueue.Kind.DEDICATED_OPEN,
                false
        ));
        assertTrue(SteamRuntime.autoRestartsBrokenSession(
                SteamOutboundQueue.Kind.DEDICATED_OPEN_ACK,
                false
        ));
    }
}
