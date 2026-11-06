package link.e4steam.steam;

import com.codedisaster.steamworks.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in read-only inspection. Never reads endpoint tokens. */
@EnabledIfEnvironmentVariable(named = "E4STEAM_NATIVE_NAMES_LOOKUP", matches = "true")
final class SteamNamedWorldLookupNativeTest {
    @Test void inspectLiveNameDirectory() throws Exception {
        try (SteamLifecycle lifecycle = new SteamLifecycle(new SteamworksApi())) {
            lifecycle.start();
            AtomicInteger count = new AtomicInteger(-1);
            SteamMatchmaking matchmaking = new SteamMatchmaking(new SteamMatchmakingCallback() {
                @Override public void onLobbyMatchList(int found) { count.set(found); }
            });
            try {
                matchmaking.addRequestLobbyListStringFilter("e4steam_name_protocol", "1", SteamMatchmaking.LobbyComparison.Equal);
                matchmaking.addRequestLobbyListDistanceFilter(SteamMatchmaking.LobbyDistanceFilter.Worldwide);
                matchmaking.addRequestLobbyListFilterSlotsAvailable(0);
                matchmaking.addRequestLobbyListResultCountFilter(100);
                assertTrue(matchmaking.requestLobbyList().isValid());
                long deadline = System.currentTimeMillis() + 20_000;
                while (count.get() < 0 && System.currentTimeMillis() < deadline) {
                    lifecycle.runCallbacks();
                    Thread.sleep(20);
                }
                assertTrue(count.get() >= 0, "Steam lookup timeout");
                System.out.println("NAME_DIRECTORY_COUNT=" + count.get());
                for (int index = 0; index < Math.min(100, count.get()); index++) {
                    SteamID lobby = matchmaking.getLobbyByIndex(index);
                    System.out.println("NAME=" + matchmaking.getLobbyData(lobby, "e4steam_world_name")
                            + " OWNER=" + SteamNativeHandle.getNativeHandle(matchmaking.getLobbyOwner(lobby))
                            + " CLAIMED_OWNER=" + matchmaking.getLobbyData(lobby, "owner")
                            + " WORLD=" + matchmaking.getLobbyData(lobby, "e4steam_world_id")
                            + " MC=" + matchmaking.getLobbyData(lobby, "minecraft")
                            + " UPDATED=" + matchmaking.getLobbyData(lobby, "updated")
                            + " LOCAL_NOW=" + System.currentTimeMillis());
                }
            } finally { matchmaking.dispose(); }
        }
    }
}
