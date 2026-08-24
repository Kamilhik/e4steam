package link.e4steam.internal.addon;

import link.e4steam.api.addon.AddonDescriptor;
import link.e4steam.api.capability.CapabilityId;

import java.util.Set;

/** Core/user policy used before initialization; operations still recheck grants at use time. */
public interface CapabilityGrantPolicy {
    Set<CapabilityId> knownCapabilities();
    boolean isAllowed(AddonDescriptor descriptor, CapabilityId capability);
}
