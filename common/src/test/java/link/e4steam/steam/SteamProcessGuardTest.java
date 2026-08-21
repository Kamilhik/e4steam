package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamProcessGuardTest {
    @Test
    void clientAndGameServerContextsCannotOverlap() throws Exception {
        SteamProcessGuard.Lease client =
                SteamProcessGuard.acquire(SteamProcessGuard.Context.CLIENT);
        try {
            assertEquals(SteamProcessGuard.Context.CLIENT, SteamProcessGuard.activeContext());
            assertThrows(IOException.class, () -> SteamProcessGuard.acquire(
                    SteamProcessGuard.Context.GAME_SERVER));
        } finally {
            client.close();
        }
        assertNull(SteamProcessGuard.activeContext());
        SteamProcessGuard.Lease server =
                SteamProcessGuard.acquire(SteamProcessGuard.Context.GAME_SERVER);
        try {
            assertTrue(server.generation() > client.generation());
        } finally {
            server.close();
        }
    }

    @Test
    void sameContextLeasesShareOneGenerationAndReleaseByReferenceCount() throws Exception {
        SteamProcessGuard.Lease first =
                SteamProcessGuard.acquire(SteamProcessGuard.Context.CLIENT);
        SteamProcessGuard.Lease second =
                SteamProcessGuard.acquire(SteamProcessGuard.Context.CLIENT);
        try {
            assertEquals(first.generation(), second.generation());
            first.close();
            assertEquals(SteamProcessGuard.Context.CLIENT, SteamProcessGuard.activeContext());
            assertThrows(IOException.class, () -> SteamProcessGuard.acquire(
                    SteamProcessGuard.Context.GAME_SERVER));
        } finally {
            second.close();
        }
        assertNull(SteamProcessGuard.activeContext());
    }
}
