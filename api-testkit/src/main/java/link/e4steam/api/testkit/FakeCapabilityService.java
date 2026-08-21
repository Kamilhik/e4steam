package link.e4steam.api.testkit;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Retryability;
import link.e4steam.api.capability.CapabilityId;
import link.e4steam.api.capability.CapabilityService;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable fake capability view that rechecks every use point. */
public final class FakeCapabilityService implements CapabilityService {
    private final Set<CapabilityId> requested;
    private final Set<CapabilityId> granted;

    /** Creates a scoped fake capability service. */
    public FakeCapabilityService(Set<CapabilityId> requested, Set<CapabilityId> granted) {
        if (requested == null || granted == null) throw new NullPointerException("capabilities");
        this.requested = Collections.unmodifiableSet(new LinkedHashSet<>(requested));
        LinkedHashSet<CapabilityId> safeGranted = new LinkedHashSet<>(granted);
        safeGranted.retainAll(this.requested);
        this.granted = Collections.unmodifiableSet(safeGranted);
    }

    @Override
    public Set<CapabilityId> requested() { return requested; }

    @Override
    public Set<CapabilityId> granted() { return granted; }

    @Override
    public boolean has(CapabilityId capability) {
        return granted.contains(capability);
    }

    @Override
    public ApiResult<CapabilityId> require(CapabilityId capability, String operation) {
        if (capability == null) throw new NullPointerException("capability");
        if (has(capability)) return ApiResult.success(capability);
        return ApiResult.failure(new ApiError(
                ApiErrorCode.CAPABILITY_DENIED,
                "e4steam.api.error.capability_denied",
                Retryability.PERMANENT,
                operation,
                "",
                "policy"
        ));
    }
}
