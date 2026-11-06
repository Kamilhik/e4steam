package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SteamDedicatedAdvertisementTest {
    private static final long NOW = 1_800_000_000_000L;
    private String tags(String name) {
        return SteamDedicatedAdvertisement.encode(90296076391571483L, 1L, name,
                "26.3", "fabric", "0.3.5", "0.1.1", 4, 2, 8, NOW);
    }
    @Test void masterTagsAloneContainEverythingNeededForAuthenticatedSteamJoin() {
        var listing = SteamDedicatedAdvertisement.decode(tags("My public world"), NOW);
        assertEquals("My public world", listing.name());
        assertEquals("26.3", listing.minecraftVersion());
        assertEquals("fabric", listing.loader());
        assertEquals(2, listing.players()); assertEquals(8, listing.capacity());
        String descriptor = new String(java.util.Base64.getUrlDecoder().decode(listing.target().value().substring(3)),
                java.nio.charset.StandardCharsets.US_ASCII);
        var target = SteamDedicatedAddress.tryParse(descriptor).orElseThrow();
        assertEquals(90296076391571483L, target.steamId()); assertEquals(1L, target.generation());
    }
    @Test void longRussianNamesRemainValidUtf8WithinSteamTagLimit() {
        String value = tags("Общий сервер с очень длинным русским названием и ещё несколькими словами");
        assertTrue(value.length() <= 127);
        String name = SteamDedicatedAdvertisement.decode(value,NOW).name();
        assertTrue("Общий сервер с очень длинным русским названием и ещё несколькими словами".startsWith(name));
        assertFalse(name.contains("\ufffd"));
    }
    @Test void staleFutureMalformedAndDuplicateAdvertisementsAreRejected() {
        String valid = tags("test");
        assertThrows(IllegalArgumentException.class, () -> SteamDedicatedAdvertisement.decode(valid,NOW+91_000L));
        assertThrows(IllegalArgumentException.class, () -> SteamDedicatedAdvertisement.decode(valid,NOW-31_000L));
        assertThrows(IllegalArgumentException.class, () -> SteamDedicatedAdvertisement.decode(valid+",p3",NOW));
        assertThrows(IllegalArgumentException.class, () -> SteamDedicatedAdvertisement.decode(valid.replace(",p2",",p9"),NOW));
        assertThrows(IllegalArgumentException.class, () -> SteamDedicatedAdvertisement.decode(valid.replace(",g1",",g0"),NOW));
        assertThrows(IllegalArgumentException.class, () -> SteamDedicatedAdvertisement.decode("e4s1,n",NOW));
    }
}
