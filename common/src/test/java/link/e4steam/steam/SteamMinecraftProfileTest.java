package link.e4steam.steam;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SteamMinecraftProfileTest {
    private static final long STEAM_ID = 76561198000000001L;

    @Test void renamingAndOnlineModeCannotChangeSteamIdentity() {
        GameProfile steve = SteamMinecraftProfile.bind(new GameProfile(UUID.randomUUID(), "Steve"), STEAM_ID);
        GameProfile alex = SteamMinecraftProfile.bind(new GameProfile(UUID.randomUUID(), "Alex"), STEAM_ID);
        assertEquals(steve.getId(), alex.getId());
        assertEquals(SteamMinecraftIdentity.uuid(STEAM_ID), alex.getId());
        assertNotEquals(alex.getId(), SteamMinecraftProfile.bind(alex, STEAM_ID + 1).getId());
        assertEquals("Alex", alex.getName());
    }

    @Test void retainsSignedTexturesWithoutChangingOriginalProfile() {
        GameProfile original = new GameProfile(UUID.randomUUID(), "Steve");
        Property textures = new Property("textures", "encoded-textures", "signature");
        original.getProperties().put("textures", textures);
        GameProfile bound = SteamMinecraftProfile.bind(original, STEAM_ID);
        assertTrue(bound.getProperties().get("textures").contains(textures));
        assertNotEquals(original.getId(), bound.getId());
        bound.getProperties().clear();
        assertTrue(original.getProperties().containsKey("textures"));
    }

    @Test void missingCosmeticsStillProducesAnAuthenticatedProfile() {
        GameProfile original = new GameProfile(UUID.randomUUID(), "Steve");
        assertTrue(SteamMinecraftProfile.bind(original, STEAM_ID).getProperties().isEmpty());
        assertSame(original, SteamMinecraftProfile.bind(original, 0L));
    }
}
