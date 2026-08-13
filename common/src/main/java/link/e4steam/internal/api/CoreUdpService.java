package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ResourceScope;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.udp.UdpService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class CoreUdpService implements UdpService {
    private final AddonId owner; private final CoreCapabilityService capabilities;
    private final CoreContributionRegistry registry; private final ResourceScope resources;
    CoreUdpService(AddonId owner, CoreCapabilityService capabilities,
                   CoreContributionRegistry registry, ResourceScope resources) {
        this.owner = owner; this.capabilities = capabilities; this.registry = registry; this.resources = resources;
    }
    @Override public ApiResult<EndpointHandle> register(EndpointDescriptor descriptor, DatagramHandler handler) {
        if (!capabilities.has(Capabilities.UDP_PROVIDER_REGISTER)) return denied("udp.register");
        if (descriptor == null || handler == null) return SafeApiErrors.failure(
                ApiErrorCode.INVALID_ARGUMENT, "udp.register", "Validation");
        Handle handle = new Handle(descriptor);
        ApiResult<link.e4steam.api.Registration> registration = registry.register("udp",
                descriptor.id().value(), owner, handler, resources, true);
        if (!registration.isSuccess()) return ApiResult.failure(registration.error().get());
        handle.registration = registration.value().get(); resources.own(handle);
        return ApiResult.success(handle);
    }
    private final class Handle extends CoreRegistration implements EndpointHandle {
        private final EndpointDescriptor descriptor; private link.e4steam.api.Registration registration;
        private Handle(EndpointDescriptor descriptor) { super(null); this.descriptor = descriptor; }
        @Override public EndpointDescriptor descriptor() { return descriptor; }
        @Override public boolean ready() { return false; }
        @Override public CompletionStage<ApiResult<Boolean>> send(Datagram datagram) {
            if (!capabilities.has(Capabilities.UDP_PROVIDER_REGISTER)) return completed(denied("udp.send"));
            if (datagram == null || !datagram.endpointId().equals(descriptor.id())
                    || datagram.payload().length > descriptor.maximumDatagramBytes()) return completed(
                    SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT, "udp.send", "Bounds"));
            return completed(SafeApiErrors.failure(ApiErrorCode.UNAVAILABLE, "udp.send", "NotNegotiated"));
        }
        @Override public void close() { if (isClosed()) return; super.close(); if (registration != null) registration.close(); }
    }
    private <T> ApiResult<T> denied(String operation) { return SafeApiErrors.failure(
            ApiErrorCode.CAPABILITY_DENIED, operation, "PolicyDenied"); }
    private static <T> CompletionStage<T> completed(T value) { return CompletableFuture.completedFuture(value); }
}
