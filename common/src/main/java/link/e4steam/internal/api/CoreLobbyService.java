package link.e4steam.internal.api;

import link.e4steam.api.*;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.capability.CapabilityId;
import link.e4steam.api.lobby.LobbyService;
import link.e4steam.api.scheduler.ExecutionContext;
import link.e4steam.steam.SteamClientApiBridge;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** Scoped generic Steam lobby backend. No public-server UX or automatic search lives in core. */
final class CoreLobbyService implements LobbyService, Registration {
    private final String owner;
    private final CoreCapabilityService capabilities;
    private final CoreSchedulerService scheduler;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Semaphore pending = new Semaphore(4);

    CoreLobbyService(AddonId owner, CoreCapabilityService capabilities,
                     CoreSchedulerService scheduler, ResourceScope resources) {
        this.owner = owner.value();
        this.capabilities = capabilities;
        this.scheduler = scheduler;
        resources.own(this);
    }

    static boolean ownsMetadata(String owner, Metadata metadata) {
        if (metadata == null) return false;
        String prefix = owner.replace(':', '-') + ':';
        return metadata.values().keySet().stream().allMatch(key -> key.startsWith(prefix));
    }
    @Override public CompletionStage<ApiResult<LobbySnapshot>> create(CreateRequest request) {
        if (request == null || !ownsMetadata(owner, request.metadata())) return invalid();
        return call(Capabilities.LOBBY_CREATE, service -> service.create(request));
    }
    @Override public CompletionStage<ApiResult<LobbySnapshot>> updateMetadata(LobbyId id, Metadata metadata) {
        if (id == null || !ownsMetadata(owner, metadata)) return invalid();
        return call(Capabilities.LOBBY_METADATA_WRITE, service -> service.updateMetadata(id, metadata));
    }
    @Override public CompletionStage<ApiResult<SearchPage>> search(SearchQuery query) {
        if (query == null || !ownsMetadata(owner, query.filters()) || !query.cursor().isEmpty()) return invalid();
        return call(Capabilities.LOBBY_SEARCH, service -> service.search(query));
    }
    @Override public CompletionStage<ApiResult<Boolean>> leave(LobbyId id) {
        if (id == null) return invalid();
        return call(Capabilities.LOBBY_CREATE, service -> service.leave(id));
    }
    @Override public CompletionStage<ApiResult<LobbySnapshot>> join(LobbyId id) {
        return CompletableFuture.completedFuture(error(ApiErrorCode.UNSUPPORTED, "UseDirectoryJoin"));
    }
    private <T> CompletionStage<ApiResult<T>> invalid() {
        return CompletableFuture.completedFuture(error(ApiErrorCode.INVALID_ARGUMENT, "LobbyRequest"));
    }
    private <T> CompletionStage<ApiResult<T>> call(CapabilityId capability,
            Function<LobbyService, CompletionStage<ApiResult<T>>> action) {
        if (!capabilities.has(capability)) return CompletableFuture.completedFuture(error(ApiErrorCode.CAPABILITY_DENIED, "LobbyCapability"));
        if (closed.get()) return CompletableFuture.completedFuture(error(ApiErrorCode.UNAVAILABLE, "AddonStopped"));
        LobbyService service = SteamClientApiBridge.addonLobbies(owner);
        if (service == null) return CompletableFuture.completedFuture(error(ApiErrorCode.UNAVAILABLE, "SteamUnavailable"));
        if (!pending.tryAcquire()) return CompletableFuture.completedFuture(error(ApiErrorCode.QUEUE_FULL, "LobbyQueue"));
        CompletableFuture<ApiResult<T>> result = new CompletableFuture<>();
        result.completeOnTimeout(error(ApiErrorCode.UNAVAILABLE, "LobbyTimeout"), 20, TimeUnit.SECONDS);
        result.whenComplete((ignored, failure) -> pending.release());
        try {
            action.apply(service).whenComplete((value, failure) -> {
                // Do not execute addon continuations inside native Steam callbacks.
                ApiResult<?> scheduled = scheduler.execute(ExecutionContext.ADDON_WORKER, () -> {
                    result.complete(closed.get() ? error(ApiErrorCode.CANCELLED, "AddonStopped")
                            : failure != null || value == null ? error(ApiErrorCode.UNAVAILABLE, "SteamLobbyOperation") : value);
                }, Duration.ofSeconds(2));
                if (!scheduled.isSuccess()) {
                    // Complete on the timeout executor, never on a Steam callback thread.
                    result.completeOnTimeout(error(ApiErrorCode.QUEUE_FULL, "AddonWorkerQueue"), 1, TimeUnit.MILLISECONDS);
                }
            });
        } catch (RuntimeException | LinkageError failure) {
            result.complete(error(ApiErrorCode.UNAVAILABLE, "SteamLobbyOperation"));
        }
        return result;
    }
    private static <T> ApiResult<T> error(ApiErrorCode code, String category) {
        return SafeApiErrors.failure(code, "lobby.operation", category);
    }
    @Override public void close() {
        if (closed.compareAndSet(false, true)) SteamClientApiBridge.releaseAddonLobbies(owner);
    }
    @Override public boolean isClosed() { return closed.get(); }
}
