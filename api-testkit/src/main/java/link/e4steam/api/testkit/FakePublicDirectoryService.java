package link.e4steam.api.testkit;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Retryability;
import link.e4steam.api.directory.PublicDirectoryService;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic public-directory bridge with replay and cancellation behavior. */
public final class FakePublicDirectoryService implements PublicDirectoryService {
    private final DirectoryOrigin origin;
    private final Map<TargetKind, PublicationTarget> targets = new LinkedHashMap<>();
    private final Map<JoinOperationId, JoinOperation> operations = new LinkedHashMap<>();
    private final Set<String> consumedHandles = new LinkedHashSet<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private DirectoryAvailability availability = DirectoryAvailability.AVAILABLE;

    /** Creates an available fake bound to a non-production test origin. */
    public FakePublicDirectoryService() {
        this(new DirectoryOrigin("https://registry.invalid"));
    }

    /** Creates an available fake bound to the supplied origin. */
    public FakePublicDirectoryService(DirectoryOrigin origin) {
        this.origin = java.util.Objects.requireNonNull(origin, "origin");
        targets.put(TargetKind.INTEGRATED_WORLD, new PublicationTarget(
                TargetKind.INTEGRATED_WORLD, new OpaqueTargetRef("iw_12345678901234567"), 1L));
        targets.put(TargetKind.DEDICATED_SERVER, new PublicationTarget(
                TargetKind.DEDICATED_SERVER, new OpaqueTargetRef("ds_ZC0xMjM0NTY3ODkw"), 1L));
    }

    /** Changes the reported availability. */
    public synchronized void availability(DirectoryAvailability value) {
        availability = java.util.Objects.requireNonNull(value, "availability");
    }

    /** Replaces a deterministic target. */
    public synchronized void target(PublicationTarget value) {
        targets.put(value.kind(), java.util.Objects.requireNonNull(value, "target"));
    }

    @Override public synchronized ApiResult<DirectoryAvailability> availability() {
        return ApiResult.success(availability);
    }

    @Override public synchronized CompletionStage<ApiResult<PublicationTarget>> publicationTarget(
            PublicationTargetRequest request
    ) {
        if (request == null || !origin.equals(request.origin())) return completed(invalid("directory.target"));
        PublicationTarget target = targets.get(request.kind());
        return completed(target == null ? unavailable("directory.target") : ApiResult.success(target));
    }

    @Override public CompletionStage<ApiResult<AttestationReceipt>> attest(
            AttestationChallenge challenge
    ) {
        if (challenge == null || !origin.equals(challenge.origin())
                || challenge.expiresAtEpochMillis() <= System.currentTimeMillis()) {
            return completed(invalid("directory.attest"));
        }
        return completed(ApiResult.success(new AttestationReceipt(
                "owner_123456789012", "receipt_123456789012", challenge.expiresAtEpochMillis())));
    }

    @Override public synchronized CompletionStage<ApiResult<JoinOperation>> join(JoinRequest request) {
        if (request == null) return completed(invalid("directory.join"));
        if (!consumedHandles.add(request.handle().value())) {
            return completed(security("directory.join"));
        }
        JoinOperationId id = new JoinOperationId(String.format(java.util.Locale.ROOT,
                "operation_%012d", sequence.incrementAndGet()));
        JoinOperation operation = new JoinOperation(id, JoinState.JOINING_STEAM_TARGET, "");
        operations.put(id, operation);
        return completed(ApiResult.success(operation));
    }

    @Override public synchronized CompletionStage<ApiResult<JoinOperation>> cancel(
            JoinOperationId operationId
    ) {
        if (operationId == null) return completed(invalid("directory.cancel"));
        JoinOperation existing = operations.get(operationId);
        if (existing == null) return completed(stale("directory.cancel"));
        JoinOperation cancelled = new JoinOperation(operationId, JoinState.CANCELLED, "");
        operations.put(operationId, cancelled);
        return completed(ApiResult.success(cancelled));
    }

    @Override public synchronized ApiResult<JoinOperation> joinSnapshot(JoinOperationId operationId) {
        if (operationId == null) return invalid("directory.snapshot");
        JoinOperation operation = operations.get(operationId);
        return operation == null ? stale("directory.snapshot") : ApiResult.success(operation);
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }
    private static <T> ApiResult<T> invalid(String operation) {
        return failure(ApiErrorCode.INVALID_ARGUMENT, operation, Retryability.PERMANENT);
    }
    private static <T> ApiResult<T> unavailable(String operation) {
        return failure(ApiErrorCode.UNAVAILABLE, operation, Retryability.AFTER_STATE_CHANGE);
    }
    private static <T> ApiResult<T> security(String operation) {
        return failure(ApiErrorCode.SECURITY_REJECTION, operation, Retryability.PERMANENT);
    }
    private static <T> ApiResult<T> stale(String operation) {
        return failure(ApiErrorCode.STALE_HANDLE, operation, Retryability.PERMANENT);
    }
    private static <T> ApiResult<T> failure(ApiErrorCode code, String operation,
                                             Retryability retryability) {
        return ApiResult.failure(new ApiError(code, "e4steam:test", retryability,
                operation, "", "testkit"));
    }
}
