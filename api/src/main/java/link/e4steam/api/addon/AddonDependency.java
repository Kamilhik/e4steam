package link.e4steam.api.addon;

import link.e4steam.api.ApiVersionRange;

import java.util.Objects;

/** Immutable required or optional dependency on another discovered addon. */
public final class AddonDependency {
    private final AddonId addonId;
    private final ApiVersionRange supportedVersions;
    private final boolean required;

    /** Creates one dependency declaration. */
    public AddonDependency(
            AddonId addonId,
            ApiVersionRange supportedVersions,
            boolean required
    ) {
        this.addonId = Objects.requireNonNull(addonId, "addonId");
        this.supportedVersions = Objects.requireNonNull(supportedVersions, "supportedVersions");
        this.required = required;
    }

    /** Returns the dependency id. */
    public AddonId addonId() { return addonId; }

    /** Returns the accepted dependency version range. */
    public ApiVersionRange supportedVersions() { return supportedVersions; }

    /** Returns whether absence or failure disables the depending addon. */
    public boolean required() { return required; }
}
