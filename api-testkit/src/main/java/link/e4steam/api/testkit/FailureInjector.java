package link.e4steam.api.testkit;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Retryability;

import java.util.ArrayDeque;
import java.util.Queue;

/** Bounded deterministic queue of typed failures for provider/transport tests. */
public final class FailureInjector {
    private final Queue<ApiError> failures = new ArrayDeque<>();
    /** Enqueues one sanitized failure. */ public synchronized void enqueue(ApiErrorCode code, String operation) { if (failures.size() >= 1_000) throw new IllegalStateException("failure queue full"); failures.add(new ApiError(code, "e4steam:test.injected", Retryability.PERMANENT, operation, "", "testkit")); }
    /** Returns a queued failure or the supplied success. */ public synchronized <T> ApiResult<T> apply(T success) { ApiError failure = failures.poll(); return failure == null ? ApiResult.success(success) : ApiResult.<T>failure(failure); }
    /** Returns queued count. */ public synchronized int size() { return failures.size(); }
}
