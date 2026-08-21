package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamDedicatedAddressTest {
    @Test
    void descriptorRoundTripsUnsignedSteamIdentity() {
        SteamDedicatedAddress expected = new SteamDedicatedAddress(-1L, 91_337L);
        assertEquals(expected, SteamDedicatedAddress.tryParse(expected.descriptor()).orElseThrow());
    }

    @Test
    void descriptorRejectsCredentialsAndInvalidGenerations() {
        assertFalse(SteamDedicatedAddress.tryParse("d-0-1.steam").isPresent());
        assertFalse(SteamDedicatedAddress.tryParse("d-1-0.steam").isPresent());
        assertFalse(SteamDedicatedAddress.tryParse("d-1-1-secret.steam").isPresent());
        assertFalse(SteamDedicatedAddress.tryParse("https://d-1-1.steam").isPresent());
        assertTrue(SteamDedicatedAddress.tryParse("D-1-Z.STEAM.").isPresent());
    }
}
