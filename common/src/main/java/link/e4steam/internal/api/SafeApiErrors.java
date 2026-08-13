package link.e4steam.internal.api;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Retryability;

/** Sanitized error construction shared by production service adapters. */
final class SafeApiErrors {
    private SafeApiErrors() { }

    static <T> ApiResult<T> failure(ApiErrorCode code, String operation, String category) {
        Retryability retry = code == ApiErrorCode.UNAVAILABLE || code == ApiErrorCode.QUEUE_FULL
                || code == ApiErrorCode.TRANSPORT_UNAVAILABLE
                ? Retryability.AFTER_STATE_CHANGE : Retryability.PERMANENT;
        return ApiResult.failure(new ApiError(code, "e4steam.api.error." + code.name().toLowerCase(),
                retry, operation, "", safeCategory(category)));
    }

    static String safeCategory(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_$.-]{1,64}")) return "Unavailable";
        return value;
    }
}
