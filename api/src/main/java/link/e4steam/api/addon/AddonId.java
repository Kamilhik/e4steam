package link.e4steam.api.addon;

import link.e4steam.api.ApiLimits;

import java.util.Objects;
import java.util.regex.Pattern;

/** Validated namespaced identifier that uniquely owns addon resources. */
public final class AddonId implements Comparable<AddonId> {
    private static final Pattern PATTERN = Pattern.compile(
            "^[a-z][a-z0-9_.-]{0,31}:[a-z][a-z0-9_./-]{0,63}$"
    );
    private final String value;

    /** Creates and validates one namespaced addon id. */
    public AddonId(String value) {
        if (value == null) throw new NullPointerException("value");
        String checked = value.trim();
        if (checked.isEmpty()
                || checked.length() > ApiLimits.MAX_IDENTIFIER_LENGTH
                || !PATTERN.matcher(checked).matches()) {
            throw new IllegalArgumentException("Invalid namespaced addon id");
        }
        this.value = checked;
    }

    /** Returns the canonical namespaced value. */
    public String value() { return value; }

    @Override
    public int compareTo(AddonId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AddonId && value.equals(((AddonId) other).value);
    }

    @Override
    public int hashCode() { return value.hashCode(); }

    @Override
    public String toString() { return value; }
}
