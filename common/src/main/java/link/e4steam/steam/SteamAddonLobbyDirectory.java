package link.e4steam.steam;

import com.codedisaster.steamworks.*;
import link.e4steam.api.lobby.LobbyService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Addon-owned discovery lobbies. All native access is confined to the Steam worker. */
final class SteamAddonLobbyDirectory implements AutoCloseable {
    private static final String OWNER_KEY = "e4steam_addon_directory";
    private static final long TIMEOUT = 15_000L;
    private final Map<String, Handle> handles = new HashMap<>();

    static String namespace(String owner) { return owner.replace(':', '-') + ':'; }

    private Handle handle(String owner) throws IOException {
        Handle value = handles.get(owner);
        if (value != null && value.retired) throw new IOException("Addon lobby scope closing");
        if (value == null) {
            if (handles.size() >= 16) throw new IOException("Addon lobby scope limit");
            value = new Handle(owner);
            handles.put(owner, value);
        }
        return value;
    }

    CompletableFuture<LobbyService.LobbySnapshot> create(String owner, LobbyService.CreateRequest request) throws IOException {
        return handle(owner).create(request);
    }
    CompletableFuture<LobbyService.SearchPage> search(String owner, LobbyService.SearchQuery query) throws IOException {
        return handle(owner).search(query);
    }
    LobbyService.LobbySnapshot update(String owner, LobbyService.LobbyId id, LobbyService.Metadata metadata) throws IOException {
        Handle value = handles.get(owner);
        if (value == null || value.lobby == null || !id.value().equals(value.lobby.id().value()))
            throw new IOException("Not an owned lobby");
        value.write(SteamID.createFromNativeHandle(Long.parseUnsignedLong(id.value())), metadata);
        value.lobby = new LobbyService.LobbySnapshot(id, value.lobby.visibility(), value.lobby.memberLimit(),
                Collections.emptyList(), metadata);
        return value.lobby;
    }
    boolean leave(String owner, LobbyService.LobbyId id) {
        Handle value = handles.get(owner);
        if (value == null || value.lobby == null || !value.lobby.id().equals(id)) return false;
        value.matchmaking.leaveLobby(SteamID.createFromNativeHandle(Long.parseUnsignedLong(id.value())));
        value.lobby = null;
        return true;
    }
    void release(String owner) { Handle value = handles.get(owner); if (value != null) value.retire(); }
    void tick(long now) {
        Iterator<Handle> iterator = handles.values().iterator();
        while (iterator.hasNext()) {
            Handle value = iterator.next();
            value.tick(now);
            if (value.retired && value.creating == null && value.searching == null) {
                value.close();
                iterator.remove();
            }
        }
    }
    boolean keepsRuntimeAlive() {
        return handles.values().stream().anyMatch(value ->
                value.creating != null && !value.creating.isDone()
                || value.searching != null && !value.searching.isDone());
    }
    @Override public void close() { for (Handle value : handles.values()) value.close(); handles.clear(); }

    private static <T> CompletableFuture<T> failed(String message) {
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(new IOException(message));
        return result;
    }

    private static final class Handle implements AutoCloseable {
        private final String owner;
        private final SteamMatchmaking matchmaking;
        private LobbyService.LobbySnapshot lobby;
        private LobbyService.CreateRequest creation;
        private CompletableFuture<LobbyService.LobbySnapshot> creating;
        private CompletableFuture<LobbyService.SearchPage> searching;
        private LobbyService.SearchQuery query;
        private long createDeadline;
        private long searchDeadline;
        private boolean retired;

        Handle(String owner) {
            this.owner = owner;
            matchmaking = new SteamMatchmaking(new SteamMatchmakingCallback() {
                @Override public void onLobbyCreated(SteamResult result, SteamID id) { created(result, id); }
                @Override public void onLobbyMatchList(int count) { found(count); }
            });
        }

        CompletableFuture<LobbyService.LobbySnapshot> create(LobbyService.CreateRequest request) {
            if (lobby != null || creating != null) return failed("Addon lobby already active or pending");
            creation = request;
            creating = new CompletableFuture<>();
            CompletableFuture<LobbyService.LobbySnapshot> result = creating;
            createDeadline = System.currentTimeMillis() + TIMEOUT;
            try {
                // Matches the narrow native compatibility bridge used by the host lobby.
                java.lang.reflect.Field callback = SteamMatchmaking.class.getSuperclass().getDeclaredField("callback");
                callback.setAccessible(true);
                java.lang.reflect.Method create = Class.forName("com.codedisaster.steamworks.SteamMatchmakingNative")
                        .getDeclaredMethod("createLobby", long.class, int.class, int.class);
                create.setAccessible(true);
                int visibility = request.visibility() == LobbyService.Visibility.PUBLIC ? 2
                        : request.visibility() == LobbyService.Visibility.FRIENDS_ONLY ? 1 : 0;
                long call = (Long) create.invoke(null, callback.getLong(matchmaking), visibility, request.memberLimit());
                if (call == 0L) throw new IOException("Steam rejected lobby creation");
            } catch (ReflectiveOperationException | IOException failure) {
                result.completeExceptionally(new IOException("Steam lobby creation failed"));
                creating = null;
                creation = null;
            }
            return result;
        }

        void created(SteamResult status, SteamID id) {
            if (creating == null) return;
            CompletableFuture<LobbyService.LobbySnapshot> result = creating;
            LobbyService.CreateRequest request = creation;
            creating = null;
            creation = null;
            if (result.isDone()) {
                if (status == SteamResult.OK) matchmaking.leaveLobby(id);
                return;
            }
            try {
                if (status != SteamResult.OK || id == null || !id.isValid()) throw new IOException("Steam lobby create rejected");
                write(id, request.metadata());
                if (!matchmaking.setLobbyData(id, OWNER_KEY, owner)) throw new IOException("Steam metadata rejected");
                lobby = new LobbyService.LobbySnapshot(new LobbyService.LobbyId(Long.toUnsignedString(
                        SteamNativeHandle.getNativeHandle(id))), request.visibility(), request.memberLimit(),
                        Collections.emptyList(), request.metadata());
                result.complete(lobby);
            } catch (RuntimeException | IOException failure) {
                if (status == SteamResult.OK && id != null) matchmaking.leaveLobby(id);
                result.completeExceptionally(new IOException("Steam lobby publication failed"));
            }
        }

        void write(SteamID id, LobbyService.Metadata metadata) throws IOException {
            // Replace, rather than accumulate, metadata on each heartbeat.
            if (lobby != null) for (String key : lobby.metadata().values().keySet()) {
                if (!metadata.values().containsKey(key)) matchmaking.deleteLobbyData(id, key);
            }
            for (Map.Entry<String, LobbyService.MetadataValue> entry : metadata.values().entrySet()) {
                if (!entry.getKey().startsWith(namespace(owner))) throw new IOException("Wrong metadata namespace");
                if (!matchmaking.setLobbyData(id, entry.getKey(), entry.getValue().value())) throw new IOException("Metadata update failed");
            }
        }

        CompletableFuture<LobbyService.SearchPage> search(LobbyService.SearchQuery request) {
            if (searching != null) return failed("Steam lobby search pending");
            if (!request.cursor().isEmpty()) return failed("Steam lobby search has no cursor");
            query = request;
            searching = new CompletableFuture<>();
            CompletableFuture<LobbyService.SearchPage> result = searching;
            searchDeadline = System.currentTimeMillis() + TIMEOUT;
            try {
                matchmaking.addRequestLobbyListStringFilter(OWNER_KEY, owner, SteamMatchmaking.LobbyComparison.Equal);
                for (Map.Entry<String, LobbyService.MetadataValue> entry : request.filters().values().entrySet()) {
                    matchmaking.addRequestLobbyListStringFilter(entry.getKey(), entry.getValue().value(), SteamMatchmaking.LobbyComparison.Equal);
                }
                matchmaking.addRequestLobbyListDistanceFilter(SteamMatchmaking.LobbyDistanceFilter.Worldwide);
                matchmaking.addRequestLobbyListResultCountFilter(request.limit());
                if (!matchmaking.requestLobbyList().isValid()) throw new IllegalStateException("Steam search rejected");
            } catch (RuntimeException failure) {
                searching = null;
                result.completeExceptionally(new IOException("Steam lobby search failed"));
            }
            return result;
        }

        void found(int count) {
            if (searching == null) return;
            CompletableFuture<LobbyService.SearchPage> result = searching;
            searching = null;
            if (result.isDone()) return;
            List<LobbyService.LobbySnapshot> values = new ArrayList<>();
            for (int index = 0; index < Math.min(Math.max(count, 0), query.limit()); index++) {
                try {
                    SteamID id = matchmaking.getLobbyByIndex(index);
                    if (!owner.equals(matchmaking.getLobbyData(id, OWNER_KEY))) continue;
                    int keys = matchmaking.getLobbyDataCount(id);
                    if (keys < 0 || keys > 65) continue;
                    Map<String, LobbyService.MetadataValue> metadata = new LinkedHashMap<>();
                    SteamMatchmakingKeyValuePair pair = new SteamMatchmakingKeyValuePair();
                    for (int keyIndex = 0; keyIndex < keys; keyIndex++) {
                        if (matchmaking.getLobbyDataByIndex(id, keyIndex, pair)
                                && pair.getKey().startsWith(namespace(owner))) {
                            metadata.put(pair.getKey(), LobbyService.MetadataValue.text(pair.getValue()));
                        }
                    }
                    values.add(new LobbyService.LobbySnapshot(new LobbyService.LobbyId(Long.toUnsignedString(
                            SteamNativeHandle.getNativeHandle(id))), LobbyService.Visibility.PUBLIC,
                            matchmaking.getLobbyMemberLimit(id), Collections.emptyList(), new LobbyService.Metadata(metadata)));
                } catch (RuntimeException ignored) { /* Discard malformed foreign advertisements individually. */ }
            }
            result.complete(new LobbyService.SearchPage(values, ""));
        }

        void tick(long now) {
            // Retain timed-out callback slots until the original callback arrives.
            // Otherwise a late Steam callback could complete a newer request.
            if (creating != null && now >= createDeadline && !creating.isDone())
                creating.completeExceptionally(new IOException("Steam lobby creation timed out"));
            if (searching != null && now >= searchDeadline && !searching.isDone())
                searching.completeExceptionally(new IOException("Steam lobby search timed out"));
        }
        void retire() {
            retired = true;
            if (creating != null) creating.completeExceptionally(new IOException("Addon stopped"));
            if (searching != null) searching.completeExceptionally(new IOException("Addon stopped"));
            if (lobby != null) matchmaking.leaveLobby(SteamID.createFromNativeHandle(Long.parseUnsignedLong(lobby.id().value())));
            lobby = null;
            // Keep the callback alive to leave a lobby created after cancellation.
        }
        @Override public void close() {
            retire();
            matchmaking.dispose();
        }
    }
}
