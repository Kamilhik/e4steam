package link.e4steam.steam;

import link.e4steam.api.lobby.LobbyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: creates a temporary smoke-test advertisement, never a joinable Minecraft world. */
@EnabledIfEnvironmentVariable(named = "E4STEAM_NATIVE_DIRECTORY_SMOKE", matches = "true")
final class SteamAddonLobbyDirectoryNativeTest {
    @Test void createSearchUpdateAndLeaveUsingInstalledSteam() throws Exception {
        String owner = "test:directory";
        LobbyService.Metadata metadata = new LobbyService.Metadata(Map.of(
                "test-directory:run", LobbyService.MetadataValue.text(UUID.randomUUID().toString())));
        try (SteamLifecycle lifecycle = new SteamLifecycle(new SteamworksApi())) {
            lifecycle.start();
            try (SteamAddonLobbyDirectory directory = new SteamAddonLobbyDirectory()) {
                var created = await(directory.create(owner, new LobbyService.CreateRequest(
                        LobbyService.Visibility.PUBLIC, 2, metadata)), lifecycle, directory);
                assertNotNull(created.id());
                boolean found = false;
                for (int attempt = 0; attempt < 5 && !found; attempt++) {
                    var page = await(directory.search(owner, new LobbyService.SearchQuery(metadata, 10, "")),
                            lifecycle, directory);
                    found = page.results().stream().anyMatch(item -> item.id().equals(created.id()));
                    if (!found) Thread.sleep(1000);
                }
                assertTrue(found, "Created advertisement must be discoverable through Steam");
                assertEquals(created.id(), directory.update(owner, created.id(), metadata).id());
                assertTrue(directory.leave(owner, created.id()));
                assertFalse(directory.leave(owner, created.id()));
            }
        }
    }
    private static <T> T await(CompletableFuture<T> future, SteamLifecycle lifecycle,
                               SteamAddonLobbyDirectory directory) throws Exception {
        long deadline = System.currentTimeMillis() + 18000;
        while (!future.isDone() && System.currentTimeMillis() < deadline) {
            lifecycle.runCallbacks();
            directory.tick(System.currentTimeMillis());
            Thread.sleep(20);
        }
        assertTrue(future.isDone(), "Steam callback timeout");
        return future.get();
    }
}
