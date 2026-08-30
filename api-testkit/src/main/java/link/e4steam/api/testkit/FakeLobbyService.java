package link.e4steam.api.testkit;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Retryability;
import link.e4steam.api.lobby.LobbyService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

import static link.e4steam.api.testkit.TestResults.completed;

/** Deterministic bounded in-memory lobby backend. */
public final class FakeLobbyService implements LobbyService {
    private final AtomicLong ids = new AtomicLong(10_000L);
    private final Map<LobbyId, LobbySnapshot> lobbies = new LinkedHashMap<>();
    @Override public synchronized CompletionStage<ApiResult<LobbySnapshot>> create(CreateRequest request) { if (request == null) throw new NullPointerException("request"); LobbyId id = new LobbyId(Long.toString(ids.incrementAndGet())); LobbySnapshot snapshot = new LobbySnapshot(id, request.visibility(), request.memberLimit(), Collections.<MemberSnapshot>emptyList(), request.metadata()); lobbies.put(id, snapshot); return completed(ApiResult.success(snapshot)); }
    @Override public synchronized CompletionStage<ApiResult<LobbySnapshot>> join(LobbyId lobbyId) { LobbySnapshot value = lobbies.get(lobbyId); return completed(value == null ? missing("lobby.join") : ApiResult.success(value)); }
    @Override public synchronized CompletionStage<ApiResult<Boolean>> leave(LobbyId lobbyId) { return completed(ApiResult.success(Boolean.valueOf(lobbies.remove(lobbyId) != null))); }
    @Override public synchronized CompletionStage<ApiResult<LobbySnapshot>> updateMetadata(LobbyId lobbyId, Metadata metadata) { LobbySnapshot old = lobbies.get(lobbyId); if (old == null) return completed(missing("lobby.metadata")); LobbySnapshot next = new LobbySnapshot(old.id(), old.visibility(), old.memberLimit(), old.members(), metadata); lobbies.put(lobbyId, next); return completed(ApiResult.success(next)); }
    @Override public synchronized CompletionStage<ApiResult<SearchPage>> search(SearchQuery query) { ArrayList<LobbySnapshot> values = new ArrayList<>(lobbies.values()); if (values.size() > query.limit()) values = new ArrayList<>(values.subList(0, query.limit())); return completed(ApiResult.success(new SearchPage(values, ""))); }
    /** Returns current lobby count. */ public synchronized int size() { return lobbies.size(); }
    private static <T> ApiResult<T> missing(String operation) { return ApiResult.failure(new ApiError(ApiErrorCode.UNAVAILABLE, "e4steam:lobby.not_found", Retryability.PERMANENT, operation, "", "testkit")); }
}
