package link.e4steam.api.addon;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiVersion;
import link.e4steam.api.ApiVersionRange;
import link.e4steam.api.capability.CapabilityId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable validated metadata supplied by a normal mod-loader addon. */
public final class AddonDescriptor {
    private final AddonId id;
    private final String displayName;
    private final ApiVersion version;
    private final ApiVersionRange apiRange;
    private final List<AddonDependency> dependencies;
    private final Set<CapabilityId> requestedCapabilities;
    private final Set<CapabilityId> requiredCapabilities;

    /** Creates one validated addon descriptor with defensive copies. */
    public AddonDescriptor(
            AddonId id,
            String displayName,
            ApiVersion version,
            ApiVersionRange apiRange,
            List<AddonDependency> dependencies,
            Set<CapabilityId> requestedCapabilities
    ) {
        this(id, displayName, version, apiRange, dependencies,
                requestedCapabilities, Collections.<CapabilityId>emptySet());
    }

    /**
     * Creates one validated descriptor and marks a subset of requested capabilities as mandatory.
     * Unknown optional capabilities are ignored, while an unavailable mandatory capability causes
     * controlled rejection before addon code runs.
     */
    public AddonDescriptor(
            AddonId id,
            String displayName,
            ApiVersion version,
            ApiVersionRange apiRange,
            List<AddonDependency> dependencies,
            Set<CapabilityId> requestedCapabilities,
            Set<CapabilityId> requiredCapabilities
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = validateName(displayName);
        this.version = Objects.requireNonNull(version, "version");
        this.apiRange = Objects.requireNonNull(apiRange, "apiRange");
        this.dependencies = immutableDependencies(dependencies);
        this.requestedCapabilities = immutableCapabilities(requestedCapabilities);
        this.requiredCapabilities = immutableCapabilities(requiredCapabilities);
        if (!this.requestedCapabilities.containsAll(this.requiredCapabilities)) {
            throw new IllegalArgumentException("Required capabilities must also be requested");
        }
        for (AddonDependency dependency : this.dependencies) {
            if (dependency.addonId().equals(id)) {
                throw new IllegalArgumentException("Addon cannot depend on itself");
            }
        }
    }

    /** Returns the unique addon id. */
    public AddonId id() { return id; }

    /** Returns the bounded human-readable name. */
    public String displayName() { return displayName; }

    /** Returns the addon version. */
    public ApiVersion version() { return version; }

    /** Returns the supported e4steam Java API range. */
    public ApiVersionRange apiRange() { return apiRange; }

    /** Returns dependencies in deterministic declaration order. */
    public List<AddonDependency> dependencies() { return dependencies; }

    /** Returns immutable requested capabilities. */
    public Set<CapabilityId> requestedCapabilities() { return requestedCapabilities; }

    /** Returns the immutable mandatory subset of requested capabilities. */
    public Set<CapabilityId> requiredCapabilities() { return requiredCapabilities; }

    @Override
    public String toString() {
        return "AddonDescriptor{id=" + id + ", version=" + version + '}';
    }

    private static String validateName(String name) {
        if (name == null) throw new NullPointerException("displayName");
        String checked = name.trim();
        if (checked.isEmpty() || checked.length() > ApiLimits.MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("Addon display name has an invalid length");
        }
        return checked;
    }

    private static List<AddonDependency> immutableDependencies(List<AddonDependency> dependencies) {
        if (dependencies == null) throw new NullPointerException("dependencies");
        if (dependencies.size() > ApiLimits.MAX_ADDON_DEPENDENCIES) {
            throw new IllegalArgumentException("Too many addon dependencies");
        }
        ArrayList<AddonDependency> copy = new ArrayList<>(dependencies.size());
        LinkedHashSet<AddonId> ids = new LinkedHashSet<>();
        for (AddonDependency dependency : dependencies) {
            Objects.requireNonNull(dependency, "dependency");
            if (!ids.add(dependency.addonId())) {
                throw new IllegalArgumentException("Duplicate addon dependency");
            }
            copy.add(dependency);
        }
        return Collections.unmodifiableList(copy);
    }

    private static Set<CapabilityId> immutableCapabilities(Set<CapabilityId> capabilities) {
        if (capabilities == null) throw new NullPointerException("requestedCapabilities");
        if (capabilities.size() > ApiLimits.MAX_REQUESTED_CAPABILITIES) {
            throw new IllegalArgumentException("Too many requested capabilities");
        }
        LinkedHashSet<CapabilityId> copy = new LinkedHashSet<>();
        for (CapabilityId capability : capabilities) {
            copy.add(Objects.requireNonNull(capability, "capability"));
        }
        return Collections.unmodifiableSet(copy);
    }
}
