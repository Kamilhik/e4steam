package link.e4steam.api.testkit;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Retryability;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Shared result helpers for fake services. */
final class TestResults {
    private TestResults() {
    }

    static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    static <T> ApiResult<T> failure(ApiErrorCode code, String key, String operation) {
        return ApiResult.failure(new ApiError(code, "e4steam:" + key, Retryability.PERMANENT, operation, "", "testkit"));
    }
}