package link.e4steam.steam;

import com.codedisaster.steamworks.SteamNativeHandle;
import com.codedisaster.steamworks.SteamUser;
import com.codedisaster.steamworks.SteamUserCallback;
import link.e4steam.api.lobby.LobbyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

/** Real Steam publication and lookup; no Minecraft listener or user world is opened. */
@EnabledIfEnvironmentVariable(named = "E4STEAM_NATIVE_NAMES_SMOKE", matches = "true")
final class SteamNamedWorldDirectoryNativeTest {
    @Test void publishesFindsRenamesAndCloses() throws Exception {
        try (SteamLifecycle lifecycle = new SteamLifecycle(new SteamworksApi())) {
            lifecycle.start();
            SteamRuntime runtime = SteamRuntime.get();
            var idField = SteamRuntime.class.getDeclaredField("localSteamId");
            idField.setAccessible(true);
            SteamUser user = new SteamUser(new SteamUserCallback() { });
            long owner = SteamNativeHandle.getNativeHandle(user.getSteamID());
            idField.setLong(runtime, owner);
            try (SteamLobbyManager social = new SteamLobbyManager(runtime);
                 SteamNamedWorldDirectory names = new SteamNamedWorldDirectory(runtime);
                 SteamAddonLobbyDirectory directory = new SteamAddonLobbyDirectory()) {
                var target = await(directory.create("test:names", new LobbyService.CreateRequest(
                        LobbyService.Visibility.PUBLIC, 2, new LobbyService.Metadata(Map.of()))), lifecycle, names, directory);
                SteamSession host = new SteamSession(25565);
                host.state = SteamSession.State.STARTED;
                String name = "test-" + UUID.randomUUID().toString().substring(0, 8);
                UUID world = UUID.randomUUID();
                long targetId = Long.parseUnsignedLong(target.id().value());
                assertEquals(name + ".steam", await(names.publish(host, name, world, targetId), lifecycle, names, directory));
                boolean found = false;
                for (int attempt = 0; attempt < 6 && !found; attempt++) {
                    var matches = await(names.search(name), lifecycle, names, directory);
                    found = matches.stream().anyMatch(value -> value.lobby() == targetId
                            && value.identity().owner() == owner && value.identity().world().equals(world));
                    if (!found) Thread.sleep(1000);
                }
                assertTrue(found, "Published name must resolve through the real Steam directory");
                assertEquals(name + ".steam", await(names.publish(host, name, world, targetId), lifecycle, names, directory));
                names.stop(host);
                assertEquals(name + "-new.steam", await(names.publish(host, name + "-new", world, targetId), lifecycle, names, directory));
                host.state = SteamSession.State.STOPPED;
                names.tick(System.currentTimeMillis());
                directory.leave("test:names", target.id());
            } finally {
                idField.setLong(runtime, 0);
                user.dispose();
            }
        }
    }

    private static <T> T await(CompletableFuture<T> future, SteamLifecycle lifecycle,
                               SteamNamedWorldDirectory names, SteamAddonLobbyDirectory directory) throws Exception {
        long deadline = System.currentTimeMillis() + 25_000;
        while (!future.isDone() && System.currentTimeMillis() < deadline) {
            lifecycle.runCallbacks();
            names.tick(System.currentTimeMillis());
            directory.tick(System.currentTimeMillis());
            Thread.sleep(20);
        }
        assertTrue(future.isDone(), "Steam callback timeout");
        return future.get();
    }
}
