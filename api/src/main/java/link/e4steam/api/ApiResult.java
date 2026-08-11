package link.e4steam.api;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Immutable success-or-error result used instead of implementation exceptions. */
public final class ApiResult<T> {
    private final T value;
    private final ApiError error;

    private ApiResult(T value, ApiError error) {
        this.value = value;
        this.error = error;
    }

    /** Creates a successful result. */
    public static <T> ApiResult<T> success(T value) {
        return new ApiResult<>(Objects.requireNonNull(value, "value"), null);
    }

    /** Creates a failed result. */
    public static <T> ApiResult<T> failure(ApiError error) {
        return new ApiResult<>(null, Objects.requireNonNull(error, "error"));
    }

    /** Returns whether this result is successful. */
    public boolean isSuccess() { return error == null; }

    /** Returns the successful value if present. */
    public Optional<T> value() { return Optional.ofNullable(value); }

    /** Returns the sanitized error if present. */
    public Optional<ApiError> error() { return Optional.ofNullable(error); }

    /** Maps a successful value without altering a failure. */
    public <R> ApiResult<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return isSuccess() ? ApiResult.success(mapper.apply(value)) : ApiResult.failure(error);
    }

    @Override
    public String toString() {
        return isSuccess() ? "ApiResult{success}" : "ApiResult{" + error + '}';
    }
}
