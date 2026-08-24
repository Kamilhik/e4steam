package link.e4steam.api.addon;

import link.e4steam.api.ApiError;

import java.util.Objects;

/** Sanitized structured addon failure without throwable or credential data. */
public final class AddonFailure {
    private final AddonId addonId;
    private final ApiError error;

    /** Creates one addon failure snapshot. */
    public AddonFailure(AddonId addonId, ApiError error) {
        this.addonId = Objects.requireNonNull(addonId, "addonId");
        this.error = Objects.requireNonNull(error, "error");
    }

    /** Returns the failing addon id. */
    public AddonId addonId() { return addonId; }

    /** Returns the sanitized typed error. */
    public ApiError error() { return error; }

    @Override
    public String toString() {
        return "AddonFailure{addonId=" + addonId + ", error=" + error + '}';
    }
}
