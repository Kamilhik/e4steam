package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SteamGuestJoinStateTest {
    @Test
    void canceledInvitationCannotBeClaimedOrConnected() {
        SteamGuestJoinState state = new SteamGuestJoinState(100);
        state.waitForConfirmation();
        state.cancel();
        assertFalse(state.claim());
        assertFalse(state.beginConnect(200));
        assertEquals(SteamGuestJoinState.Phase.CANCELED, state.phase());
    }

    @Test
    void connectingInvitationExpiresButConfirmationDoesNot() {
        SteamGuestJoinState state = new SteamGuestJoinState(100);
        state.waitForConfirmation();
        assertFalse(state.expired(Long.MAX_VALUE - 1));
        assertTrue(state.beginConnect(200));
        assertFalse(state.expired(199));
        assertTrue(state.expired(200));
    }

    @Test
    void lostLobbyCannotBecomeConnected() {
        SteamGuestJoinState state = new SteamGuestJoinState(100);
        state.waitForConfirmation();
        assertTrue(state.beginConnect(200));
        state.loseLobby();
        state.connected();
        assertEquals(SteamGuestJoinState.Phase.LOST, state.phase());
    }

    @Test
    void closingWorldWhileConnectingCancelsTheJoin() {
        SteamGuestJoinState state = new SteamGuestJoinState(100);
        state.waitForConfirmation();
        assertTrue(state.beginConnect(200));
        state.cancel();
        state.connected();
        assertEquals(SteamGuestJoinState.Phase.CANCELED, state.phase());
    }

    @Test
    void successfulJoinMovesToConnected() {
        SteamGuestJoinState state = new SteamGuestJoinState(100);
        state.waitForConfirmation();
        assertTrue(state.claim());
        assertTrue(state.beginConnect(200));
        state.connected();
        assertTrue(state.isConnected());
    }
    @Test
    void lateLoginFromCanceledAttemptDoesNotBlockNextInvitation() {
        SteamGuestJoinState previous = new SteamGuestJoinState(100);
        previous.waitForConfirmation();
        assertTrue(previous.beginConnect(200));
        previous.cancel();

        SteamGuestJoinState retry = new SteamGuestJoinState(300);
        retry.waitForConfirmation();
        assertTrue(retry.claim());
        assertTrue(retry.beginConnect(400));
        previous.connected();
        assertFalse(previous.isConnected());
        assertEquals(SteamGuestJoinState.Phase.CONNECTING, retry.phase());
        assertTrue(retry.expired(400));
        retry.connected();
        assertTrue(retry.isConnected());
        assertFalse(retry.expired(Long.MAX_VALUE - 1));
    }
    @Test
    void openingBridgeKeepsMinecraftLoginDeadline() {
        SteamGuestJoinState state = new SteamGuestJoinState(100);
        state.waitForConfirmation();
        assertTrue(state.beginConnect(200));
        Object bridge = new Object();
        state.bridgeOpened(bridge);
        assertTrue(state.ownsBridge(bridge));
        assertFalse(state.isConnected());
        assertTrue(state.expired(200));
    }

    @Test
    void canceledBridgeCannotBeMistakenForRetryBridge() {
        Object oldBridge = new Object();
        SteamGuestJoinState previous = new SteamGuestJoinState(100);
        previous.waitForConfirmation();
        previous.beginConnect(200);
        previous.bridgeOpened(oldBridge);
        previous.cancel();
        assertFalse(previous.ownsBridge(oldBridge));
        previous.bridgeOpened(oldBridge);
        assertFalse(previous.ownsBridge(oldBridge));

        SteamGuestJoinState retry = new SteamGuestJoinState(300);
        retry.waitForConfirmation();
        retry.beginConnect(400);
        Object newBridge = new Object();
        retry.bridgeOpened(newBridge);
        assertFalse(retry.ownsBridge(oldBridge));
        assertTrue(retry.ownsBridge(newBridge));
    }
}
