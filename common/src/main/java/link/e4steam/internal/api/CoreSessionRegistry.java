package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Registration;
import link.e4steam.api.identity.IdentityService;
import link.e4steam.api.session.SessionService;
import link.e4steam.api.event.SessionStateEvent;
import link.e4steam.steam.SteamRuntime;
import link.e4steam.internal.dedicated.DedicatedServerController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Bridges generation-safe Steam runtime state into immutable Addon API session projections. */
final class CoreSessionRegistry implements AutoCloseable {
    private final Object lock = new Object();
    private final CoreEventBus events;
    private final Set<SessionResource> resources = new LinkedHashSet<>();
    private long observedGeneration;
    private CompletableFuture<ApiResult<SessionService.SessionSnapshot>> nextReady =
            new CompletableFuture<>();
    private SessionService.SessionSnapshot lastPublished;
    private boolean closed;

    CoreSessionRegistry(CoreEventBus events) {
        this.events = java.util.Objects.requireNonNull(events, "events");
    }

    ApiResult<SessionService.SessionSnapshot> snapshot() {
        SessionService.SessionSnapshot dedicated = dedicatedSnapshot();
        if (dedicated != null) return ApiResult.success(dedicated);
        SteamRuntime.SafeSessionView view = SteamRuntime.get().safeSessionView();
        return view.active() ? ApiResult.success(toSnapshot(view))
                : SafeApiErrors.failure(ApiErrorCode.UNAVAILABLE,
                "session.snapshot", "NoActiveContext");
    }

    CompletionStage<ApiResult<SessionService.PeerPage>> peers(
            SessionService.SessionId id, String cursor, int limit) {
        if (id == null || limit < 1 || limit > ApiLimits.MAX_PAGE_SIZE) {
            return completed(SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT,
                    "session.peers", "Pagination"));
        }
        DedicatedServerController dedicated = DedicatedServerController.current();
        SessionService.SessionId dedicatedId = dedicated == null
                ? null : dedicated.addonSessionId();
        if (dedicatedId != null && dedicatedId.equals(id)) {
            try {
                return completed(ApiResult.success(dedicatedPeerPage(dedicated, cursor, limit)));
            } catch (RuntimeException invalidCursor) {
                return completed(SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT,
                        "session.peers", "Cursor"));
            }
        }
        SteamRuntime.SafeSessionView view = SteamRuntime.get().safeSessionView();
        if (!matches(view, id)) return completed(stale("session.peers"));
        String after = cursor == null ? "" : cursor.trim();
        if (!after.isEmpty()) {
            try { new IdentityService.PeerId(after); }
            catch (RuntimeException failure) {
                return completed(SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT,
                        "session.peers", "Cursor"));
            }
        }
        ArrayList<SteamRuntime.SafePeerIdentity> peers = new ArrayList<>(view.peers());
        peers.sort(Comparator.comparing(SteamRuntime.SafePeerIdentity::opaquePeerId));
        ArrayList<SessionService.PeerSnapshot> page = new ArrayList<>();
        String next = "";
        for (SteamRuntime.SafePeerIdentity peer : peers) {
            if (!after.isEmpty() && peer.opaquePeerId().compareTo(after) <= 0) continue;
            if (page.size() == limit) {
                next = page.get(page.size() - 1).peerId().value();
                break;
            }
            page.add(new SessionService.PeerSnapshot(
                    new IdentityService.PeerId(peer.opaquePeerId()), true));
        }
        return completed(ApiResult.success(new SessionService.PeerPage(page, next)));
    }

    CompletionStage<ApiResult<SessionService.SessionSnapshot>> disconnect(
            SessionService.SessionId id) {
        if (id == null) return completed(SafeApiErrors.failure(
                ApiErrorCode.INVALID_ARGUMENT, "session.disconnect", "Validation"));
        DedicatedServerController dedicated = DedicatedServerController.current();
        SessionService.SessionId dedicatedId = dedicated == null
                ? null : dedicated.addonSessionId();
        if (dedicatedId != null && dedicatedId.equals(id)) {
            return completed(SafeApiErrors.failure(ApiErrorCode.CAPABILITY_DENIED,
                    "session.disconnect", "DedicatedAdminRequired"));
        }
        SteamRuntime.SafeSessionView view = SteamRuntime.get().safeSessionView();
        if (!matches(view, id)) return completed(stale("session.disconnect"));
        SessionService.SessionSnapshot before = toSnapshot(view);
        if (!SteamRuntime.get().disconnectSafeSession(id.generation())) {
            return completed(stale("session.disconnect"));
        }
        closeGeneration(id.generation());
        return completed(ApiResult.success(new SessionService.SessionSnapshot(
                before.id(), before.role(), SessionService.SessionState.CLOSED,
                0, before.capacity(), before.features())));
    }

    CompletionStage<ApiResult<SessionService.SessionSnapshot>> readiness() {
        ApiResult<SessionService.SessionSnapshot> current = snapshot();
        if (current.isSuccess()) return completed(current);
        synchronized (lock) {
            if (closed) return completed(SafeApiErrors.failure(
                    ApiErrorCode.UNAVAILABLE, "session.readiness", "Shutdown"));
            return nextReady.thenApply(result -> result);
        }
    }

    ApiResult<Registration> register(
            SessionService.SessionId id, Registration resource) {
        if (id == null || resource == null) return SafeApiErrors.failure(
                ApiErrorCode.INVALID_ARGUMENT, "session.resource", "Validation");
        SteamRuntime.SafeSessionView view = SteamRuntime.get().safeSessionView();
        if (!matchesAny(view, id)) return stale("session.resource");
        SessionResource owned = new SessionResource(id.generation(), resource);
        synchronized (lock) {
            if (closed || resources.size() >= ApiLimits.MAX_REGISTRATIONS_PER_FAMILY) {
                owned.close();
                return SafeApiErrors.failure(closed ? ApiErrorCode.UNAVAILABLE : ApiErrorCode.QUEUE_FULL,
                        "session.resource", closed ? "Shutdown" : "ResourceLimit");
            }
            resources.add(owned);
        }
        if (!matchesAny(SteamRuntime.get().safeSessionView(), id)) {
            owned.close();
            return stale("session.resource");
        }
        return ApiResult.success(owned);
    }

    IdentityService.LocalIdentity localIdentity() {
        SteamRuntime.SafeMinecraftIdentity identity = SteamRuntime.get().safeLocalMinecraftIdentity();
        return identity == null ? null : new IdentityService.LocalIdentity(
                new IdentityService.MinecraftIdentity(
                        identity.minecraftUuid(), identity.minecraftName(), true));
    }

    IdentityService.RemoteIdentity remoteIdentity(IdentityService.PeerId peerId) {
        if (peerId == null) return null;
        DedicatedServerController dedicated = DedicatedServerController.current();
        DedicatedServerController.DedicatedPeerIdentity dedicatedIdentity = dedicated == null
                ? null : dedicated.addonPeerIdentity(peerId.value());
        if (dedicatedIdentity != null) {
            return new IdentityService.RemoteIdentity(new IdentityService.PeerIdentity(
                    peerId, new IdentityService.MinecraftIdentity(
                    dedicatedIdentity.minecraftUuid(), dedicatedIdentity.minecraftName(), false)));
        }
        SteamRuntime.SafePeerIdentity peer = SteamRuntime.get().safeResolvePeer(peerId.value());
        return peer == null ? null : new IdentityService.RemoteIdentity(
                new IdentityService.PeerIdentity(peerId,
                        new IdentityService.MinecraftIdentity(
                                peer.minecraftUuid(), peer.minecraftName(), false)));
    }

    boolean matches(SessionService.SessionId id, IdentityService.PeerId peerId) {
        if (id == null || peerId == null) return false;
        DedicatedServerController dedicated = DedicatedServerController.current();
        if (dedicated != null && dedicated.matchesAddonPeer(id, peerId)) return true;
        SteamRuntime.SafeSessionView view = SteamRuntime.get().safeSessionView();
        return matches(view, id) && SteamRuntime.get().safeResolvePeer(peerId.value()) != null;
    }

    boolean localIsHost(SessionService.SessionId id) {
        if (id == null) return false;
        DedicatedServerController dedicated = DedicatedServerController.current();
        SessionService.SessionId dedicatedId = dedicated == null
                ? null : dedicated.addonSessionId();
        if (dedicatedId != null && dedicatedId.equals(id)) return true;
        SteamRuntime.SafeSessionView view = SteamRuntime.get().safeSessionView();
        return matches(view, id) && "INTEGRATED_HOST".equals(view.roleCode());
    }

    void refresh() {
        SteamRuntime.SafeSessionView view = SteamRuntime.get().safeSessionView();
        SessionService.SessionSnapshot dedicated = dedicatedSnapshot();
        List<SessionResource> stale = Collections.emptyList();
        CompletableFuture<ApiResult<SessionService.SessionSnapshot>> ready = null;
        SessionService.SessionSnapshot publish = null;
        synchronized (lock) {
            if (closed) return;
            long current = dedicated != null ? dedicated.id().generation()
                    : view.active() ? view.generation() : 0L;
            if (observedGeneration != 0L && observedGeneration != current) {
                stale = removeGeneration(observedGeneration);
                nextReady = new CompletableFuture<>();
            }
            observedGeneration = current;
            if (dedicated != null || view.active()) {
                SessionService.SessionSnapshot snapshot = dedicated != null
                        ? dedicated : toSnapshot(view);
                if (!nextReady.isDone()) ready = nextReady;
                if (!sameState(lastPublished, snapshot)) {
                    lastPublished = snapshot;
                    publish = snapshot;
                }
            } else if (lastPublished != null
                    && lastPublished.state() != SessionService.SessionState.CLOSED) {
                lastPublished = closedSnapshot(lastPublished);
                publish = lastPublished;
            }
        }
        for (SessionResource resource : stale) resource.closeDelegate();
        if (ready != null) ready.complete(ApiResult.success(
                dedicated != null ? dedicated : toSnapshot(view)));
        if (publish != null) events.publish(SessionStateEvent.TYPE,
                new SessionStateEvent(System.currentTimeMillis(), publish), true);
    }

    @Override public void close() {
        List<SessionResource> closing;
        CompletableFuture<ApiResult<SessionService.SessionSnapshot>> readiness;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            closing = new ArrayList<>(resources);
            resources.clear();
            readiness = nextReady;
        }
        for (SessionResource resource : closing) resource.closeDelegate();
        readiness.complete(SafeApiErrors.failure(
                ApiErrorCode.UNAVAILABLE, "session.readiness", "Shutdown"));
    }

    private void closeGeneration(long generation) {
        List<SessionResource> closing;
        synchronized (lock) { closing = removeGeneration(generation); }
        for (SessionResource resource : closing) resource.closeDelegate();
    }

    private List<SessionResource> removeGeneration(long generation) {
        ArrayList<SessionResource> closing = new ArrayList<>();
        java.util.Iterator<SessionResource> iterator = resources.iterator();
        while (iterator.hasNext()) {
            SessionResource resource = iterator.next();
            if (resource.generation == generation) {
                iterator.remove();
                closing.add(resource);
            }
        }
        return closing;
    }

    private static SessionService.SessionSnapshot toSnapshot(
            SteamRuntime.SafeSessionView view) {
        return new SessionService.SessionSnapshot(
                new SessionService.SessionId(view.sessionId(), view.generation()),
                SessionService.SessionRole.valueOf(view.roleCode()),
                SessionService.SessionState.valueOf(view.stateCode()),
                view.peers().size(), view.capacity(),
                new LinkedHashSet<>(java.util.Arrays.asList("e4steam:tcp", "e4steam:udp")));
    }

    private SessionService.SessionSnapshot dedicatedSnapshot() {
        DedicatedServerController dedicated = DedicatedServerController.current();
        if (dedicated == null || !dedicated.accepting()) return null;
        SessionService.SessionId id = dedicated.addonSessionId();
        if (id == null) return null;
        return new SessionService.SessionSnapshot(
                id,
                SessionService.SessionRole.DEDICATED_SERVER,
                SessionService.SessionState.ACTIVE,
                dedicated.addonPeerIds().size(),
                dedicated.maxPeers(),
                new LinkedHashSet<>(java.util.Arrays.asList(
                        "e4steam:tcp", "e4steam:addon-channels")));
    }

    private static SessionService.PeerPage dedicatedPeerPage(
            DedicatedServerController dedicated, String cursor, int limit) {
        String after = cursor == null ? "" : cursor.trim();
        if (!after.isEmpty()) new IdentityService.PeerId(after);
        ArrayList<SessionService.PeerSnapshot> page = new ArrayList<>();
        String next = "";
        for (String value : dedicated.addonPeerIds()) {
            if (!after.isEmpty() && value.compareTo(after) <= 0) continue;
            if (page.size() == limit) {
                next = page.get(page.size() - 1).peerId().value();
                break;
            }
            page.add(new SessionService.PeerSnapshot(
                    new IdentityService.PeerId(value), true));
        }
        return new SessionService.PeerPage(page, next);
    }

    private static SessionService.SessionSnapshot closedSnapshot(
            SessionService.SessionSnapshot previous) {
        return new SessionService.SessionSnapshot(previous.id(), previous.role(),
                SessionService.SessionState.CLOSED, 0, previous.capacity(), previous.features());
    }

    private static boolean sameState(SessionService.SessionSnapshot first,
                                     SessionService.SessionSnapshot second) {
        return first != null && first.id().equals(second.id())
                && first.role() == second.role() && first.state() == second.state()
                && first.peers() == second.peers() && first.capacity() == second.capacity()
                && first.features().equals(second.features());
    }

    private static boolean matches(
            SteamRuntime.SafeSessionView view, SessionService.SessionId id) {
        return view.active() && view.generation() == id.generation()
                && view.sessionId().equals(id.value());
    }

    private static boolean matchesAny(
            SteamRuntime.SafeSessionView view, SessionService.SessionId id) {
        DedicatedServerController dedicated = DedicatedServerController.current();
        SessionService.SessionId dedicatedId = dedicated == null
                ? null : dedicated.addonSessionId();
        return (dedicatedId != null && dedicatedId.equals(id)) || matches(view, id);
    }

    private static <T> ApiResult<T> stale(String operation) {
        return SafeApiErrors.failure(ApiErrorCode.STALE_HANDLE, operation, "SessionGeneration");
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private final class SessionResource extends CoreRegistration {
        private final long generation;
        private final Registration delegate;
        private SessionResource(long generation, Registration delegate) {
            super(null);
            this.generation = generation;
            this.delegate = delegate;
        }
        @Override public void close() {
            if (isClosed()) return;
            super.close();
            synchronized (lock) { resources.remove(this); }
            closeDelegate();
        }
        private void closeDelegate() {
            super.close();
            try { delegate.close(); }
            catch (RuntimeException ignored) {
                // Addon cleanup failures cannot abort core session teardown.
            }
        }
    }
}
