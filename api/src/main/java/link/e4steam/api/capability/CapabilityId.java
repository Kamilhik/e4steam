package link.e4steam.api.capability;

import link.e4steam.api.ApiLimits;

import java.util.Objects;
import java.util.regex.Pattern;

/** Validated dotted identifier for one least-privilege addon capability. */
public final class CapabilityId implements Comparable<CapabilityId> {
    private static final Pattern PATTERN = Pattern.compile(
            "^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+$"
    );
    private final String value;

    /** Creates and validates one capability id. */
    public CapabilityId(String value) {
        if (value == null) throw new NullPointerException("value");
        String checked = value.trim();
        if (checked.isEmpty()
                || checked.length() > ApiLimits.MAX_IDENTIFIER_LENGTH
                || !PATTERN.matcher(checked).matches()) {
            throw new IllegalArgumentException("Invalid capability id");
        }
        this.value = checked;
    }

    /** Returns the canonical dotted identifier. */
    public String value() { return value; }

    @Override
    public int compareTo(CapabilityId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CapabilityId
                && value.equals(((CapabilityId) other).value);
    }

    @Override
    public int hashCode() { return value.hashCode(); }

    @Override
    public String toString() { return value; }
}
