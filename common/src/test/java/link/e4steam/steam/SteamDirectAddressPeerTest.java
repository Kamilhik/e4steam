package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamDirectAddressPeerTest {
    @Test
    void onlyFriendsOnlyWorldsPreAcceptDirectAddressPeers() {
        assertTrue(SteamLobbyManager.preAcceptsFriendsForDirectAddress(
                SteamAccessMode.FRIENDS_ONLY
        ));
        assertFalse(SteamLobbyManager.preAcceptsFriendsForDirectAddress(
                SteamAccessMode.INVITE_ONLY
        ));
    }
}
