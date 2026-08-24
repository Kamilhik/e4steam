package link.e4steam.internal.addon;

import link.e4steam.api.addon.AddonDescriptor;
import link.e4steam.api.addon.E4steamAddon;
import link.e4steam.api.addon.E4steamAddonEntrypoint;

import java.util.Objects;

/** One entry point already discovered by a normal mod loader. */
public final class AddonCandidate {
    private final AddonDescriptor descriptor;
    private final E4steamAddon addon;
    private final String sourceModId;
    private final boolean enabled;

    public AddonCandidate(AddonDescriptor descriptor, E4steamAddon addon, String sourceModId, boolean enabled) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.addon = Objects.requireNonNull(addon, "addon");
        this.sourceModId = safeSource(sourceModId);
        this.enabled = enabled;
    }

    public static AddonCandidate fromEntrypoint(E4steamAddonEntrypoint entrypoint, String sourceModId) {
        Objects.requireNonNull(entrypoint, "entrypoint");
        return new AddonCandidate(entrypoint.descriptor(), entrypoint, sourceModId, true);
    }

    public AddonDescriptor descriptor() { return descriptor; }
    public E4steamAddon addon() { return addon; }
    public String sourceModId() { return sourceModId; }
    public boolean enabled() { return enabled; }

    private static String safeSource(String value) {
        if (value == null) throw new NullPointerException("sourceModId");
        String checked = value.trim();
        if (!checked.matches("[a-z][a-z0-9_.-]{0,95}")) {
            throw new IllegalArgumentException("Invalid source mod id");
        }
        return checked;
    }
}
