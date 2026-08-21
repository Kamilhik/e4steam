package link.e4steam.api;

import java.util.Objects;

/** Sanitized structured error that contains no native object, token or stack trace. */
public final class ApiError {
    private final ApiErrorCode code;
    private final String messageKey;
    private final Retryability retryability;
    private final String operation;
    private final String correlationId;
    private final String causeCategory;

    /** Creates one sanitized API error. */
    public ApiError(
            ApiErrorCode code,
            String messageKey,
            Retryability retryability,
            String operation,
            String correlationId,
            String causeCategory
    ) {
        this.code = Objects.requireNonNull(code, "code");
        this.messageKey = ApiValidation.text(messageKey, "messageKey", 128);
        this.retryability = Objects.requireNonNull(retryability, "retryability");
        this.operation = ApiValidation.text(operation, "operation", 96);
        this.correlationId = optionalSafe(correlationId, 64);
        this.causeCategory = optionalSafe(causeCategory, 64);
    }

    /** Returns the stable error code. */
    public ApiErrorCode code() { return code; }

    /** Returns a namespaced localization key, not a protocol field. */
    public String messageKey() { return messageKey; }

    /** Returns bounded retry guidance. */
    public Retryability retryability() { return retryability; }

    /** Returns the safe operation identifier. */
    public String operation() { return operation; }

    /** Returns an optional non-secret correlation id. */
    public String correlationId() { return correlationId; }

    /** Returns an optional sanitized cause category. */
    public String causeCategory() { return causeCategory; }

    @Override
    public String toString() {
        return "ApiError{code=" + code
                + ", messageKey='" + messageKey + '\''
                + ", retryability=" + retryability
                + ", operation='" + operation + '\''
                + (correlationId.isEmpty() ? "" : ", correlationId='" + correlationId + '\'')
                + (causeCategory.isEmpty() ? "" : ", causeCategory='" + causeCategory + '\'')
                + '}';
    }

    private static String optionalSafe(String value, int maximumLength) {
        if (value == null || value.trim().isEmpty()) return "";
        return ApiValidation.text(value, "optional safe field", maximumLength);
    }
}
