package link.e4steam.steam;

import com.codedisaster.steamworks.*;
import link.e4steam.MinecraftVersion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Public name advertisements point to access-controlled host lobbies, never to invite tokens. */
final class SteamNamedWorldDirectory implements AutoCloseable {
    static final String NAME = "e4steam_world_name";
    static final String WORLD = "e4steam_world_id";
    private static final String MARKER = "e4steam_name_protocol";
    private final SteamRuntime runtime;
    private final SteamMatchmaking matchmaking;
    private SteamSession host;
    private String name;
    private UUID world;
    private long targetLobby;
    private SteamID advertisement;
    private CompletableFuture<String> creating;
    private long createDeadline;
    private long heartbeatAt;
    private CompletableFuture<List<SteamNamedAddress.Target>> searching;
    private String searchedName;
    private long searchDeadline;

    SteamNamedWorldDirectory(SteamRuntime runtime) {
        this.runtime = runtime;
        matchmaking = new SteamMatchmaking(new SteamMatchmakingCallback() {
            @Override public void onLobbyCreated(SteamResult status, SteamID id) { created(status, id); }
            @Override public void onLobbyMatchList(int count) { found(count); }
        });
    }

    CompletableFuture<String> publish(SteamSession owner, String selectedName, UUID selectedWorld, long lobby) throws IOException {
        if (creating != null) throw new IOException("Публикация адреса ещё выполняется.");
        if (host == owner && selectedName.equals(name) && selectedWorld.equals(world) && advertisement != null)
            return CompletableFuture.completedFuture(name + ".steam");
        clear();
        host = owner;
        name = selectedName;
        world = selectedWorld;
        targetLobby = lobby;
        SteamID target = SteamID.createFromNativeHandle(lobby);
        if (!matchmaking.setLobbyData(target, NAME, name) || !matchmaking.setLobbyData(target, WORLD, world.toString())) {
            clear();
            throw new IOException("Steam не сохранил адрес мира.");
        }
        creating = new CompletableFuture<>();
        createDeadline = System.currentTimeMillis() + 20_000;
        CompletableFuture<String> result = creating;
        try {
            // Same native compatibility boundary used by the core host lobby.
            java.lang.reflect.Field callback = SteamMatchmaking.class.getSuperclass().getDeclaredField("callback");
            callback.setAccessible(true);
            java.lang.reflect.Method method = Class.forName("com.codedisaster.steamworks.SteamMatchmakingNative")
                    .getDeclaredMethod("createLobby", long.class, int.class, int.class);
            method.setAccessible(true);
            if ((Long) method.invoke(null, callback.getLong(matchmaking), 2, 250) == 0)
                throw new IOException("Steam отклонил публикацию адреса.");
        } catch (ReflectiveOperationException | IOException failure) {
            creating = null;
            clear();
            result.completeExceptionally(new IOException("Не удалось опубликовать адрес в Steam."));
        }
        return result;
    }

    private void created(SteamResult status, SteamID id) {
        CompletableFuture<String> result = creating;
        creating = null;
        if (result == null || result.isDone() || host == null || host.state != SteamSession.State.STARTED) {
            if (status == SteamResult.OK) matchmaking.leaveLobby(id);
            clear();
            return;
        }
        if (status != SteamResult.OK) {
            result.completeExceptionally(new IOException("Steam не создал объявление адреса."));
            clear();
            return;
        }
        advertisement = id;
        if (!writeAdvertisement()) {
            result.completeExceptionally(new IOException("Steam не сохранил объявление адреса."));
            clear();
            return;
        }
        result.complete(name + ".steam");
    }

    private boolean writeAdvertisement() {
        heartbeatAt = System.currentTimeMillis() + 20_000;
        return matchmaking.setLobbyData(advertisement, MARKER, "1")
                && matchmaking.setLobbyData(advertisement, NAME, name)
                && matchmaking.setLobbyData(advertisement, WORLD, world.toString())
                && matchmaking.setLobbyData(advertisement, "owner", runtime.steamId())
                && matchmaking.setLobbyData(advertisement, "target", Long.toUnsignedString(targetLobby))
                && matchmaking.setLobbyData(advertisement, "minecraft", MinecraftVersion.current())
                && matchmaking.setLobbyData(advertisement, "updated", Long.toString(System.currentTimeMillis()));
    }

    CompletableFuture<List<SteamNamedAddress.Target>> search(String selectedName) throws IOException {
        if (searching != null) throw new IOException("Поиск адреса уже выполняется. Повторите подключение.");
        searchedName = selectedName;
        searching = new CompletableFuture<>();
        searchDeadline = System.currentTimeMillis() + 20_000;
        matchmaking.addRequestLobbyListStringFilter(MARKER, "1", SteamMatchmaking.LobbyComparison.Equal);
        matchmaking.addRequestLobbyListStringFilter(NAME, selectedName, SteamMatchmaking.LobbyComparison.Equal);
        matchmaking.addRequestLobbyListDistanceFilter(SteamMatchmaking.LobbyDistanceFilter.Worldwide);
        matchmaking.addRequestLobbyListFilterSlotsAvailable(0);
        matchmaking.addRequestLobbyListResultCountFilter(50);
        if (!matchmaking.requestLobbyList().isValid()) {
            searching.completeExceptionally(new IOException("Steam отклонил поиск адреса."));
            CompletableFuture<List<SteamNamedAddress.Target>> result = searching;
            searching = null;
            return result;
        }
        return searching;
    }

    private void found(int count) {
        CompletableFuture<List<SteamNamedAddress.Target>> result = searching;
        searching = null;
        if (result == null || result.isDone()) return;
        if (count >= 50) {
            result.completeExceptionally(new IOException("Слишком много миров с этим именем. Выберите другое имя."));
            return;
        }
        List<SteamNamedAddress.Target> targets = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int index = 0; index < count; index++) {
            try {
                SteamID lobby = matchmaking.getLobbyByIndex(index);
                if (!searchedName.equals(matchmaking.getLobbyData(lobby, NAME))) continue;
                long owner = Long.parseUnsignedLong(matchmaking.getLobbyData(lobby, "owner"));
                long knownOwner = SteamNativeHandle.getNativeHandle(matchmaking.getLobbyOwner(lobby));
                // GetLobbyOwner returns nil for non-members. Search metadata is only
                // a candidate: SteamLobbyManager verifies the actual owner after JoinLobby.
                if (!SteamNamedAddress.acceptSearchOwner(knownOwner, owner)) continue;
                long updated = Long.parseLong(matchmaking.getLobbyData(lobby, "updated"));
                if (updated < now - 90_000 || updated > now + 30_000) continue;
                long target = Long.parseUnsignedLong(matchmaking.getLobbyData(lobby, "target"));
                if (target == 0) continue;
                targets.add(new SteamNamedAddress.Target(target, new SteamNamedAddress.Identity(owner,
                        UUID.fromString(matchmaking.getLobbyData(lobby, WORLD))), matchmaking.getLobbyData(lobby, "minecraft")));
            } catch (RuntimeException malformed) { /* Ignore malformed foreign advertisements. */ }
        }
        result.complete(targets);
    }

    void stop(SteamSession owner) {
        if (host != owner) return;
        if (creating != null) creating.completeExceptionally(new IOException("Мир закрыт."));
        clear();
    }

    private void clear() {
        if (advertisement != null) matchmaking.leaveLobby(advertisement);
        if (targetLobby != 0) {
            SteamID target = SteamID.createFromNativeHandle(targetLobby);
            matchmaking.deleteLobbyData(target, NAME);
            matchmaking.deleteLobbyData(target, WORLD);
        }
        advertisement = null;
        host = null;
        name = null;
        world = null;
        targetLobby = 0;
    }

    void tick(long now) {
        if (host != null && host.state != SteamSession.State.STARTED) stop(host);
        if (creating != null && now >= createDeadline && !creating.isDone())
            creating.completeExceptionally(new IOException("Steam не ответил на публикацию адреса."));
        if (searching != null && now >= searchDeadline && !searching.isDone())
            searching.completeExceptionally(new IOException("Steam не ответил на поиск адреса."));
        if (advertisement != null && now >= heartbeatAt && !writeAdvertisement()) clear();
    }

    @Override public void close() {
        if (creating != null) creating.completeExceptionally(new IOException("Steam остановлен."));
        if (searching != null) searching.completeExceptionally(new IOException("Steam остановлен."));
        clear();
        matchmaking.dispose();
    }
}
