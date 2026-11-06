package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class SteamNamedAddressTest {
    private final SteamNamedAddress.Identity owner = new SteamNamedAddress.Identity(76561198000000000L, UUID.randomUUID());

    @Test void normalizeAndSeparateWireAddresses() {
        assertEquals("kamilchik", SteamNamedAddress.normalize(" Kamilchik.steam "));
        assertEquals("my-server", SteamNamedAddress.tryParse("MY-SERVER.steam").orElseThrow());
        for (String invalid : List.of("ab", "-name", "name-", "имя", "a.b", "s-abc", "d-abcd", "a".repeat(33)))
            assertThrows(IllegalArgumentException.class, () -> SteamNamedAddress.normalize(invalid));
        assertTrue(SteamNamedAddress.tryParse("example.com").isEmpty());
        assertTrue(SteamNamedAddress.tryParse("s-abc-def.steam").isEmpty());
    }

    @Test void selectsOnlyOneCompatibleWorld() throws Exception {
        var target = new SteamNamedAddress.Target(123, owner, "1.21.1");
        assertEquals(target, SteamNamedAddress.choose(List.of(target), null, "1.21.1"));
        assertThrows(IOException.class, () -> SteamNamedAddress.choose(List.of(), null, "1.21.1"));
        assertEquals(target, SteamNamedAddress.choose(List.of(target, target), null, "1.21.1"));
        var secondSession = new SteamNamedAddress.Target(456, owner, "1.21.1");
        assertThrows(IOException.class, () -> SteamNamedAddress.choose(List.of(target, secondSession), null, "1.21.1"));
        assertThrows(IOException.class, () -> SteamNamedAddress.choose(List.of(target), null, "1.20.1"));
    }

    @Test void nonMemberSearchKeepsCandidateUntilOwnerCanBeVerifiedAfterJoining() {
        assertTrue(SteamNamedAddress.acceptSearchOwner(0, owner.owner()));
        assertTrue(SteamNamedAddress.acceptSearchOwner(owner.owner(), owner.owner()));
        assertFalse(SteamNamedAddress.acceptSearchOwner(owner.owner() + 1, owner.owner()));
        assertFalse(SteamNamedAddress.acceptSearchOwner(0, 0));
    }

    @Test void minecraftSeesStableNameWhileWireEndpointChanges() {
        SteamNamedAddress.rememberDisplay("old-session-token", "Kamilchik");
        SteamNamedAddress.rememberDisplay("new-session-token", "Kamilchik");
        assertEquals("kamilchik.steam", SteamNamedAddress.displayFor("old-session-token"));
        assertEquals("kamilchik.steam", SteamNamedAddress.displayFor("new-session-token"));
        assertEquals("unrelated", SteamNamedAddress.displayFor("unrelated"));
    }

    @Test void pinsOwnerAndWorldAcrossRestartButRejectsAnImpostor() throws Exception {
        var original = new SteamNamedAddress.Target(100, owner, "1.21.1");
        var restarted = new SteamNamedAddress.Target(200, owner, "1.21.1");
        var impostor = new SteamNamedAddress.Target(300,
                new SteamNamedAddress.Identity(owner.owner() + 1, owner.world()), "1.21.1");
        var differentWorld = new SteamNamedAddress.Target(400,
                new SteamNamedAddress.Identity(owner.owner(), UUID.randomUUID()), "1.21.1");
        assertThrows(IOException.class, () -> SteamNamedAddress.choose(List.of(original, impostor), null, "1.21.1"));
        assertEquals(restarted, SteamNamedAddress.choose(List.of(restarted, impostor, differentWorld), owner, "1.21.1"));
        assertThrows(IOException.class, () -> SteamNamedAddress.choose(List.of(impostor, differentWorld), owner, "1.21.1"));
    }
}
