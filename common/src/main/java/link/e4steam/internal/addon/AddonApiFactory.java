package link.e4steam.internal.addon;

import link.e4steam.api.E4steamApi;
import link.e4steam.api.ResourceScope;
import link.e4steam.api.addon.AddonDescriptor;
import link.e4steam.api.capability.CapabilityId;

import java.util.Set;

/** Creates one capability-scoped root API without exposing implementation objects. */
public interface AddonApiFactory {
    E4steamApi create(AddonDescriptor descriptor, Set<CapabilityId> granted, ResourceScope resources);
}
