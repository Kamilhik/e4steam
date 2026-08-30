package link.e4steam.api.addon;

import link.e4steam.api.ApiValidation;

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
        this.value = ApiValidation.identifier(value, "addonId", PATTERN);
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
