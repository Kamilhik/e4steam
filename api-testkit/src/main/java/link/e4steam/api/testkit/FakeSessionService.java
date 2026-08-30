package link.e4steam.api.testkit;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Registration;
import link.e4steam.api.Retryability;
import link.e4steam.api.session.SessionService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static link.e4steam.api.testkit.TestResults.completed;

/** Mutable generation-safe fake session and peer inventory. */
public final class FakeSessionService implements SessionService {
    private volatile SessionSnapshot snapshot;
    private final List<PeerSnapshot> peers = new ArrayList<>();

    /** Creates a fake with one current generation. */ public FakeSessionService(SessionSnapshot snapshot) { if (snapshot == null) throw new NullPointerException("snapshot"); this.snapshot = snapshot; }
    /** Replaces generation/state. */ public synchronized void update(SessionSnapshot snapshot) { if (snapshot == null) throw new NullPointerException("snapshot"); this.snapshot = snapshot; }
    /** Adds one fake peer. */ public synchronized void addPeer(PeerSnapshot peer) { if (peer == null) throw new NullPointerException("peer"); peers.add(peer); }

    @Override public ApiResult<SessionSnapshot> snapshot() { return ApiResult.success(snapshot); }
    @Override public CompletionStage<ApiResult<PeerPage>> peers(SessionId sessionId, String cursor, int limit) { if (!current(sessionId)) return completed(stale("session.peers")); if (limit < 1 || limit > 100) return completed(invalid("session.peers")); synchronized (this) { return completed(ApiResult.success(new PeerPage(new ArrayList<>(peers.subList(0, Math.min(limit, peers.size()))), ""))); } }
    @Override public CompletionStage<ApiResult<SessionSnapshot>> disconnect(SessionId sessionId, String safeReasonCode) { if (!current(sessionId)) return completed(stale("session.disconnect")); SessionSnapshot current = snapshot; snapshot = new SessionSnapshot(current.id(), current.role(), SessionState.CLOSED, current.peers(), current.capacity(), current.features()); return completed(ApiResult.success(snapshot)); }
    @Override public CompletionStage<ApiResult<SessionSnapshot>> readiness() { return completed(ApiResult.success(snapshot)); }
    @Override public ApiResult<Registration> registerResource(SessionId sessionId, Registration resource) { if (resource == null) throw new NullPointerException("resource"); return current(sessionId) ? ApiResult.success(resource) : stale("session.resource"); }
    private boolean current(SessionId id) { return id != null && snapshot.id().equals(id) && snapshot.state() != SessionState.CLOSED; }
    private static <T> ApiResult<T> stale(String operation) { return ApiResult.failure(new ApiError(ApiErrorCode.STALE_HANDLE, "e4steam:session.stale", Retryability.PERMANENT, operation, "", "testkit")); }
    private static <T> ApiResult<T> invalid(String operation) { return ApiResult.failure(new ApiError(ApiErrorCode.INVALID_ARGUMENT, "e4steam:invalid_argument", Retryability.PERMANENT, operation, "", "testkit")); }
}
