package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.directory.PublicDirectoryService;
import link.e4steam.internal.dedicated.DedicatedServerController;
import link.e4steam.steam.SteamClientApiBridge;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Production boundary for opaque public targets. No native handle or endpoint token crosses it. */
final class CorePublicDirectoryService implements PublicDirectoryService {
    private static final int MAX_OPERATIONS = 32;
    private static final int MAX_CONSUMED_HANDLES = 1024;
    private static final String INTEGRATED_PREFIX = "iw_";
    private static final String DEDICATED_PREFIX = "ds_";

    private final CoreCapabilityService capabilities;
    private final SecureRandom random = new SecureRandom();
    private final Map<JoinOperationId, JoinOperation> operations = new LinkedHashMap<>();
    private final Map<JoinOperationId, Long> integratedTargets = new LinkedHashMap<>();
    private final Set<JoinOperationId> dedicatedOperations = new LinkedHashSet<>();
    private final Set<String> consumedHandles = new LinkedHashSet<>();

    CorePublicDirectoryService(CoreCapabilityService capabilities) {
        this.capabilities = capabilities;
    }

    @Override public ApiResult<DirectoryAvailability> availability() {
        if (!capabilities.has(Capabilities.DIRECTORY_JOIN)
                && !capabilities.has(Capabilities.DIRECTORY_PUBLICATION)) {
            return denied("directory.availability");
        }
        String status = SteamClientApiBridge.statusCode();
        if ("RUNNING".equals(status)) {
            return ApiResult.success(DirectoryAvailability.AVAILABLE);
        }
        DedicatedServerController dedicated = DedicatedServerController.current();
        if (dedicated != null && dedicated.accepting()) {
            return ApiResult.success(DirectoryAvailability.AVAILABLE);
        }
        if ("FAILED".equals(status)) {
            return ApiResult.success(DirectoryAvailability.STEAM_UNAVAILABLE);
        }
        return ApiResult.success(DirectoryAvailability.NO_ACTIVE_SESSION);
    }

    @Override public CompletionStage<ApiResult<PublicationTarget>> publicationTarget(
            PublicationTargetRequest request
    ) {
        if (!capabilities.has(Capabilities.DIRECTORY_PUBLICATION)) {
            return completed(denied("directory.publication-target"));
        }
        if (request == null) {
            return completed(invalid("directory.publication-target"));
        }
        if (request.kind() == TargetKind.DEDICATED_SERVER) {
            DedicatedServerController controller = DedicatedServerController.current();
            if (controller == null || !controller.accepting()) {
                return completed(unavailable("directory.publication-target", "NoActiveDedicatedServer"));
            }
            String descriptor = controller.descriptor();
            if (descriptor.isEmpty()) {
                return completed(unavailable("directory.publication-target", "NoActiveDedicatedTarget"));
            }
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    descriptor.getBytes(StandardCharsets.US_ASCII));
            return completed(ApiResult.success(new PublicationTarget(
                    TargetKind.DEDICATED_SERVER,
                    new OpaqueTargetRef(DEDICATED_PREFIX + encoded),
                    controller.generation()
            )));
        }
        CompletableFuture<ApiResult<PublicationTarget>> result = new CompletableFuture<>();
        SteamClientApiBridge.publicHostLobbyTarget().whenComplete((target, failure) -> {
            if (failure != null || target == null || target.lobbyId() == 0L || target.generation() <= 0L) {
                result.complete(unavailable("directory.publication-target", "NoActivePublicWorld"));
                return;
            }
            result.complete(ApiResult.success(new PublicationTarget(
                    TargetKind.INTEGRATED_WORLD,
                    new OpaqueTargetRef(INTEGRATED_PREFIX + Long.toUnsignedString(target.lobbyId())),
                    target.generation()
            )));
        });
        return result;
    }

    @Override public CompletionStage<ApiResult<AttestationReceipt>> attest(AttestationChallenge challenge) {
        if (!capabilities.has(Capabilities.DIRECTORY_ATTESTATION)) {
            return completed(denied("directory.attest"));
        }
        if (challenge == null || challenge.expiresAtEpochMillis() <= System.currentTimeMillis()) {
            return completed(invalid("directory.attest"));
        }
        if (DevelopmentRegistryAttestation.enabledFor(challenge)) {
            String ownerRef = developmentOwnerRef(challenge.action());
            if (ownerRef == null) {
                return completed(unavailable("directory.attest", "NoActivePublisherIdentity"));
            }
            return DevelopmentRegistryAttestation.attest(challenge, ownerRef)
                    .handle((receipt, failure) -> {
                        if (failure == null) return ApiResult.success(receipt);
                        Throwable cause = failure;
                        while (cause instanceof java.util.concurrent.CompletionException
                                && cause.getCause() != null) cause = cause.getCause();
                        return SafeApiErrors.<AttestationReceipt>failure(
                                cause instanceof SecurityException
                                        ? ApiErrorCode.SECURITY_REJECTION : ApiErrorCode.UNAVAILABLE,
                                "directory.attest",
                                cause instanceof SecurityException
                                        ? "DevelopmentAttestationRejected"
                                        : "DevelopmentRegistryUnavailable");
                    });
        }
        // The Steam Web API proof must be delivered directly from core to a
        // registry verifier. Until that verifier is configured, fail closed;
        // never substitute a caller-supplied SteamID or leak the raw ticket.
        return completed(SafeApiErrors.failure(ApiErrorCode.UNSUPPORTED,
                "directory.attest", "RegistryVerifierUnavailable"));
    }

    private String developmentOwnerRef(AttestationAction action) {
        if (action == AttestationAction.PUBLISH_INTEGRATED) {
            SteamClientApiBridge.MinecraftIdentity identity = SteamClientApiBridge.localIdentity();
            return identity == null ? null : DevelopmentRegistryAttestation.opaqueOwner(
                    "user", identity.minecraftUuid().toString());
        }
        if (action == AttestationAction.PUBLISH_DEDICATED) {
            DedicatedServerController controller = DedicatedServerController.current();
            if (controller == null || !controller.accepting() || controller.descriptor().isEmpty()) {
                return null;
            }
            return DevelopmentRegistryAttestation.opaqueOwner("server", controller.descriptor());
        }
        return null;
    }

    @Override public CompletionStage<ApiResult<JoinOperation>> join(JoinRequest request) {
        if (!capabilities.has(Capabilities.DIRECTORY_JOIN)) {
            return completed(denied("directory.join"));
        }
        if (request == null) {
            return completed(invalid("directory.join"));
        }
        final JoinOperation started;
        synchronized (this) {
            evictFinishedOperations();
            if (operations.size() >= MAX_OPERATIONS) {
                return completed(SafeApiErrors.failure(ApiErrorCode.QUEUE_FULL,
                        "directory.join", "OperationLimit"));
            }
            if (!consumedHandles.add(request.handle().value())) {
                return completed(SafeApiErrors.failure(ApiErrorCode.SECURITY_REJECTION,
                        "directory.join", "JoinHandleReplay"));
            }
            trimConsumedHandles();
            JoinOperationId id = new JoinOperationId(randomId());
            started = new JoinOperation(id, JoinState.RESOLVING, "");
            operations.put(id, started);
        }

        String target = request.target().value();
        if (target.startsWith(INTEGRATED_PREFIX)) {
            long lobbyId;
            try {
                lobbyId = Long.parseUnsignedLong(target.substring(INTEGRATED_PREFIX.length()));
                if (lobbyId == 0L) throw new NumberFormatException("zero lobby");
            } catch (NumberFormatException failure) {
                return completed(fail(started.id(), JoinState.STALE, "invalid-target"));
            }
            synchronized (this) {
                integratedTargets.put(started.id(), lobbyId);
            }
            update(started.id(), JoinState.JOINING_STEAM_TARGET, "");
            CompletableFuture<ApiResult<JoinOperation>> result = new CompletableFuture<>();
            SteamClientApiBridge.joinPublicLobby(lobbyId).whenComplete((accepted, failure) -> {
                if (failure != null || !Boolean.TRUE.equals(accepted)) {
                    result.complete(fail(started.id(), JoinState.FAILED, "steam-join-rejected"));
                } else {
                    result.complete(refresh(started.id()));
                }
            });
            return result;
        }

        if (target.startsWith(DEDICATED_PREFIX)) {
            String descriptor;
            try {
                byte[] decoded = Base64.getUrlDecoder().decode(
                        target.substring(DEDICATED_PREFIX.length()));
                descriptor = new String(decoded, StandardCharsets.US_ASCII);
            } catch (IllegalArgumentException failure) {
                return completed(fail(started.id(), JoinState.STALE, "invalid-target"));
            }
            if (!SteamClientApiBridge.acceptDirectSteamInvite(
                    descriptor, "Public e4steam server")) {
                return completed(fail(started.id(), JoinState.STALE, "invalid-target"));
            }
            synchronized (this) {
                dedicatedOperations.add(started.id());
            }
            update(started.id(), JoinState.CONNECTING_MINECRAFT, "");
            return completed(success(started.id(), JoinState.CONNECTING_MINECRAFT, ""));
        }
        return completed(fail(started.id(), JoinState.STALE, "unknown-target"));
    }

    @Override public CompletionStage<ApiResult<JoinOperation>> cancel(JoinOperationId operationId) {
        if (!capabilities.has(Capabilities.DIRECTORY_JOIN)) {
            return completed(denied("directory.cancel"));
        }
        if (operationId == null) return completed(invalid("directory.cancel"));
        JoinOperation existing;
        synchronized (this) { existing = operations.get(operationId); }
        if (existing == null) return completed(SafeApiErrors.failure(ApiErrorCode.STALE_HANDLE,
                "directory.cancel", "UnknownOperation"));
        if (terminal(existing.state())) {
            return completed(ApiResult.success(existing));
        }
        SteamClientApiBridge.cancelGuestJoin();
        return completed(success(operationId, JoinState.CANCELLED, ""));
    }

    @Override public ApiResult<JoinOperation> joinSnapshot(JoinOperationId operationId) {
        if (!capabilities.has(Capabilities.DIRECTORY_JOIN)) return denied("directory.join-snapshot");
        if (operationId == null) return invalid("directory.join-snapshot");
        JoinOperation operation;
        synchronized (this) { operation = operations.get(operationId); }
        return operation == null
                ? SafeApiErrors.failure(ApiErrorCode.STALE_HANDLE,
                "directory.join-snapshot", "UnknownOperation")
                : refresh(operationId);
    }

    private synchronized ApiResult<JoinOperation> success(JoinOperationId id, JoinState state,
                                                           String detail) {
        JoinOperation previous = operations.get(id);
        if (previous != null && terminal(previous.state())) {
            return ApiResult.success(previous);
        }
        JoinOperation operation = new JoinOperation(id, state, detail);
        operations.put(id, operation);
        return ApiResult.success(operation);
    }

    private ApiResult<JoinOperation> fail(JoinOperationId id, JoinState state, String detail) {
        return success(id, state, detail);
    }

    private synchronized void update(JoinOperationId id, JoinState state, String detail) {
        success(id, state, detail);
    }

    private ApiResult<JoinOperation> refresh(JoinOperationId id) {
        JoinOperation current;
        Long lobbyId;
        boolean dedicated;
        synchronized (this) {
            current = operations.get(id);
            lobbyId = integratedTargets.get(id);
            dedicated = dedicatedOperations.contains(id);
        }
        if (current == null) {
            return SafeApiErrors.failure(ApiErrorCode.STALE_HANDLE,
                    "directory.join-snapshot", "UnknownOperation");
        }
        if (terminal(current.state())) return ApiResult.success(current);
        if (lobbyId != null) {
            SteamClientApiBridge.PublicJoinSnapshot snapshot =
                    SteamClientApiBridge.publicJoinSnapshot(lobbyId);
            JoinState state = joinState(snapshot.stateCode());
            return state == null || state == JoinState.IDLE
                    ? ApiResult.success(current)
                    : success(id, state, snapshot.detailCode());
        }
        if (dedicated) {
            SteamClientApiBridge.SessionView session = SteamClientApiBridge.sessionView();
            if (session.active() && "DEDICATED_SERVER_CLIENT".equals(session.roleCode())
                    && "ACTIVE".equals(session.stateCode())) {
                return success(id, JoinState.ACTIVE, "");
            }
            if ("FAILED".equals(SteamClientApiBridge.statusCode())) {
                return fail(id, JoinState.FAILED, "steam-unavailable");
            }
        }
        return ApiResult.success(current);
    }

    private synchronized void evictFinishedOperations() {
        while (operations.size() >= MAX_OPERATIONS) {
            JoinOperationId finished = null;
            for (Map.Entry<JoinOperationId, JoinOperation> entry : operations.entrySet()) {
                if (terminal(entry.getValue().state())) {
                    finished = entry.getKey();
                    break;
                }
            }
            if (finished == null) return;
            operations.remove(finished);
            integratedTargets.remove(finished);
            dedicatedOperations.remove(finished);
        }
    }

    private static JoinState joinState(String stateCode) {
        if (stateCode == null || stateCode.isEmpty()) return null;
        try { return JoinState.valueOf(stateCode); }
        catch (IllegalArgumentException ignored) { return JoinState.FAILED; }
    }

    private static boolean terminal(JoinState state) {
        return state == JoinState.ACTIVE || state == JoinState.CANCELLED
                || state == JoinState.STALE || state == JoinState.FULL
                || state == JoinState.INCOMPATIBLE || state == JoinState.FAILED;
    }

    private void trimConsumedHandles() {
        while (consumedHandles.size() > MAX_CONSUMED_HANDLES) {
            String oldest = consumedHandles.iterator().next();
            consumedHandles.remove(oldest);
        }
    }

    private String randomId() {
        byte[] value = new byte[16];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static <T> ApiResult<T> denied(String operation) {
        return SafeApiErrors.failure(ApiErrorCode.CAPABILITY_DENIED, operation, "PolicyDenied");
    }
    private static <T> ApiResult<T> invalid(String operation) {
        return SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT, operation, "Validation");
    }
    private static <T> ApiResult<T> unavailable(String operation, String category) {
        return SafeApiErrors.failure(ApiErrorCode.UNAVAILABLE, operation, category);
    }
    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }
}
