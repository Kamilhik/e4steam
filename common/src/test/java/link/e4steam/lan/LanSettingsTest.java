package link.e4steam.lan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LanSettingsTest {
    @AfterEach void reset() { LanSettings.closed(); LanSettings.install(LanSettings.defaults()); }

    @Test void capacityReservesSlotsForOwnerAndOrdinaryLanPlayers() {
        Object server = new Object();
        LanSettings.activate(server, LanSettings.defaults().withMaxPlayers(12));
        LanSettings.nonSteamPlayers(server, 4);
        assertEquals(12, LanSettings.steamLobbyCapacity());
        assertEquals(8, LanSettings.steamGuestLimit());
        LanSettings.nonSteamPlayers(new Object(), 12);
        assertEquals(8, LanSettings.steamGuestLimit());
        LanSettings.activate(server, LanSettings.defaults().withMaxPlayers(2));
        assertEquals(0, LanSettings.steamGuestLimit());
    }

    @Test void editingPreferencesDoesNotChangeAnActiveWorldUntilApplied() {
        Object server = new Object();
        LanSettings.activate(server, LanSettings.defaults());
        LanSettings.install(LanSettings.defaults().withMaxPlayers(32));
        assertEquals(8, LanSettings.active(server).maxPlayers());
        assertEquals(7, LanSettings.steamGuestLimit());
        LanSettings.closed();
        assertNull(LanSettings.active(server));
    }

    @Test void invalidPortsAndCapacityNeverReachTheServer() {
        for (int value : new int[]{-1, 0, 65536})
            assertThrows(IllegalArgumentException.class, () -> LanSettings.defaults().withPort(value));
        for (int value : new int[]{0, 1, 65, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> LanSettings.defaults().withMaxPlayers(value));
    }

    @Test void replacementWorldDoesNotInheritPreviousWorldOccupancy() {
        Object first = new Object();
        LanSettings.activate(first, LanSettings.defaults());
        LanSettings.nonSteamPlayers(first, 7);
        assertEquals(1, LanSettings.steamGuestLimit());
        LanSettings.activate(new Object(), LanSettings.defaults());
        assertEquals(7, LanSettings.steamGuestLimit());
    }
}
