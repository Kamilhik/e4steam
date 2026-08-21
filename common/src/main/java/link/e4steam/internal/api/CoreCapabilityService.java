package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.capability.CapabilityId;
import link.e4steam.api.capability.CapabilityService;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class CoreCapabilityService implements CapabilityService {
    private final Set<CapabilityId> requested;
    private final Set<CapabilityId> granted;

    CoreCapabilityService(Set<CapabilityId> requested, Set<CapabilityId> granted) {
        LinkedHashSet<CapabilityId> requestedCopy = new LinkedHashSet<>(requested);
        LinkedHashSet<CapabilityId> grantedCopy = new LinkedHashSet<>(granted);
        grantedCopy.retainAll(requestedCopy);
        this.requested = Collections.unmodifiableSet(requestedCopy);
        this.granted = Collections.unmodifiableSet(grantedCopy);
    }

    @Override public Set<CapabilityId> requested() { return requested; }
    @Override public Set<CapabilityId> granted() { return granted; }
    @Override public boolean has(CapabilityId capability) { return capability != null && granted.contains(capability); }
    @Override public ApiResult<CapabilityId> require(CapabilityId capability, String operation) {
        if (capability == null) throw new NullPointerException("capability");
        return has(capability) ? ApiResult.success(capability)
                : SafeApiErrors.failure(ApiErrorCode.CAPABILITY_DENIED, operation, "PolicyDenied");
    }
}
