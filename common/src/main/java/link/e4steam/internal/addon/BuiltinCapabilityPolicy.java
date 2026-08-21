package link.e4steam.internal.addon;

import link.e4steam.api.addon.AddonDescriptor;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.capability.CapabilityId;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Known 1.0 capabilities with an optional deny set supplied by local configuration. */
public final class BuiltinCapabilityPolicy implements CapabilityGrantPolicy {
    private static final Set<CapabilityId> KNOWN = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            Capabilities.SESSION_OBSERVE,
            Capabilities.SESSION_CONTROL,
            Capabilities.DEDICATED_OBSERVE,
            Capabilities.DEDICATED_ADMIN,
            Capabilities.DEDICATED_PUBLICATION_PROPOSE,
            Capabilities.IDENTITY_MINECRAFT_READ,
            Capabilities.IDENTITY_STEAM_PROFILE_READ,
            Capabilities.LOBBY_CREATE,
            Capabilities.LOBBY_SEARCH,
            Capabilities.LOBBY_METADATA_WRITE,
            Capabilities.ACCESS_MODE_REGISTER,
            Capabilities.ACCESS_POLICY_EVALUATE,
            Capabilities.NETWORK_CHANNEL_REGISTER,
            Capabilities.UDP_PROVIDER_REGISTER,
            Capabilities.UI_CONTRIBUTE,
            Capabilities.COMMANDS_REGISTER,
            Capabilities.CONFIG_READ,
            Capabilities.CONFIG_WRITE,
            Capabilities.STORAGE_PRIVATE,
            Capabilities.WORLD_SETTINGS_READ,
            Capabilities.WORLD_SETTINGS_PROPOSE,
            Capabilities.MODPACK_INSPECT,
            Capabilities.MODPACK_STAGE,
            Capabilities.SKINS_PROVIDE,
            Capabilities.DIAGNOSTICS_CONTRIBUTE,
            Capabilities.STEAM_PROFILE_READ
    )));

    private final Set<CapabilityId> denied;

    public BuiltinCapabilityPolicy(Set<CapabilityId> denied) {
        if (denied == null) throw new NullPointerException("denied");
        this.denied = Collections.unmodifiableSet(new LinkedHashSet<>(denied));
    }

    @Override public Set<CapabilityId> knownCapabilities() { return KNOWN; }

    @Override public boolean isAllowed(AddonDescriptor descriptor, CapabilityId capability) {
        return KNOWN.contains(capability) && !denied.contains(capability);
    }
}
