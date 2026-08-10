package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamMinecraftIdentityTest {
    @Test
    void sameSteamIdAlwaysProducesSameUuid() {
        long steamId = 7_656_119_980_000_001L;

        UUID first = SteamMinecraftIdentity.uuid(steamId);
        UUID second = SteamMinecraftIdentity.uuid(steamId);

        assertEquals(first, second);
    }

    @Test
    void differentSteamIdsProduceDifferentUuids() {
        assertNotEquals(
                SteamMinecraftIdentity.uuid(7_656_119_980_000_001L),
                SteamMinecraftIdentity.uuid(7_656_119_980_000_002L)
        );
    }

    @Test
    void safeNameIsDerivedOnlyFromSteamIdAndFitsMinecraftRules() {
        String name = SteamMinecraftIdentity.safeName(-1L);

        assertEquals("s_3w5e11264sgsf", name);
        assertTrue(name.length() <= 16);
        assertTrue(name.matches("[a-z0-9_]+"));
    }

    @Test
    void authenticatedGuestCannotReceiveOwnerBypass() {
        assertFalse(SteamMinecraftIdentity.allowSingleplayerOwnerBypass(42L, true));
        assertTrue(SteamMinecraftIdentity.allowSingleplayerOwnerBypass(0L, true));
        assertFalse(SteamMinecraftIdentity.allowSingleplayerOwnerBypass(0L, false));
    }

    @Test
    void unauthenticatedSteamIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SteamMinecraftIdentity.uuid(0));
        assertThrows(IllegalArgumentException.class, () -> SteamMinecraftIdentity.safeName(0));
    }
}
