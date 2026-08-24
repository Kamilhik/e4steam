package link.e4steam.api;

import java.util.Objects;

/** Inclusive lower and exclusive upper bound for supported addon API versions. */
public final class ApiVersionRange {
    private final ApiVersion minimumInclusive;
    private final ApiVersion maximumExclusive;

    /** Creates a non-empty semantic API range. */
    public ApiVersionRange(ApiVersion minimumInclusive, ApiVersion maximumExclusive) {
        this.minimumInclusive = Objects.requireNonNull(minimumInclusive, "minimumInclusive");
        this.maximumExclusive = Objects.requireNonNull(maximumExclusive, "maximumExclusive");
        if (minimumInclusive.compareTo(maximumExclusive) >= 0) {
            throw new IllegalArgumentException("API version range must be non-empty");
        }
    }

    /** Returns the inclusive minimum. */
    public ApiVersion minimumInclusive() { return minimumInclusive; }

    /** Returns the exclusive maximum. */
    public ApiVersion maximumExclusive() { return maximumExclusive; }

    /** Returns whether the supplied API version is inside this range. */
    public boolean contains(ApiVersion version) {
        Objects.requireNonNull(version, "version");
        return version.compareTo(minimumInclusive) >= 0
                && version.compareTo(maximumExclusive) < 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ApiVersionRange)) return false;
        ApiVersionRange range = (ApiVersionRange) other;
        return minimumInclusive.equals(range.minimumInclusive)
                && maximumExclusive.equals(range.maximumExclusive);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minimumInclusive, maximumExclusive);
    }

    @Override
    public String toString() {
        return "[" + minimumInclusive + ", " + maximumExclusive + ")";
    }
}
