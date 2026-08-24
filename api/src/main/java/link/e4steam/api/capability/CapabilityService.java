package link.e4steam.api.capability;

import link.e4steam.api.ApiResult;

import java.util.Set;

/** Scoped view of capabilities requested and granted to one addon. */
public interface CapabilityService {
    /** Returns an immutable set declared by addon metadata. */
    Set<CapabilityId> requested();

    /** Returns an immutable set granted by core policy. */
    Set<CapabilityId> granted();

    /** Returns whether a capability is currently granted. */
    boolean has(CapabilityId capability);

    /** Returns success or a typed denial for a capability use point. */
    ApiResult<CapabilityId> require(CapabilityId capability, String operation);
}
