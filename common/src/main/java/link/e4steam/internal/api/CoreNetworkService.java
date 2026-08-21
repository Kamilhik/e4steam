package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ResourceScope;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.network.NetworkService;

/** Scoped capability gate for the shared authenticated channel coordinator. */
final class CoreNetworkService implements NetworkService {
    private final AddonId owner;
    private final CoreCapabilityService capabilities;
    private final CoreContributionRegistry registry;
    private final ResourceScope resources;
    private final AddonNetworkCoordinator coordinator;

    CoreNetworkService(AddonId owner, CoreCapabilityService capabilities,
                       CoreContributionRegistry registry, ResourceScope resources,
                       AddonNetworkCoordinator coordinator) {
        this.owner = owner;
        this.capabilities = capabilities;
        this.registry = registry;
        this.resources = resources;
        this.coordinator = coordinator;
    }

    @Override public ApiResult<ChannelHandle> register(
            ChannelDescriptor descriptor, MessageHandler handler) {
        if (!capabilities.has(Capabilities.NETWORK_CHANNEL_REGISTER)) {
            return SafeApiErrors.failure(ApiErrorCode.CAPABILITY_DENIED,
                    "network.register", "PolicyDenied");
        }
        if (descriptor == null || handler == null) return SafeApiErrors.failure(
                ApiErrorCode.INVALID_ARGUMENT, "network.register", "Validation");
        ApiResult<link.e4steam.api.Registration> reservation = registry.register(
                "network", descriptor.id().value(), owner, descriptor, resources, true);
        if (!reservation.isSuccess()) return ApiResult.failure(reservation.error().get());
        ApiResult<ChannelHandle> registered = coordinator.register(
                owner, descriptor, handler, capabilities);
        if (!registered.isSuccess()) {
            reservation.value().get().close();
            return registered;
        }
        BoundHandle handle = new BoundHandle(registered.value().get(),
                reservation.value().get());
        resources.own(handle);
        return ApiResult.success(handle);
    }

    @Override public boolean registrationsFrozen() { return registry.isFrozen(); }

    private static final class BoundHandle extends CoreRegistration implements ChannelHandle {
        private final ChannelHandle delegate;
        private final link.e4steam.api.Registration reservation;
        private BoundHandle(ChannelHandle delegate, link.e4steam.api.Registration reservation) {
            super(null);
            this.delegate = delegate;
            this.reservation = reservation;
        }
        @Override public ChannelDescriptor descriptor() { return delegate.descriptor(); }
        @Override public ChannelState state() {
            return isClosed() ? ChannelState.CLOSED : delegate.state();
        }
        @Override public java.util.concurrent.CompletionStage<
                ApiResult<SendStatus>> send(
                link.e4steam.api.session.SessionService.SessionId sessionId,
                link.e4steam.api.identity.IdentityService.PeerId peerId,
                byte[] payload) {
            return isClosed() ? java.util.concurrent.CompletableFuture.completedFuture(
                    ApiResult.success(SendStatus.CLOSED))
                    : delegate.send(sessionId, peerId, payload);
        }
        @Override public void close() {
            if (isClosed()) return;
            super.close();
            delegate.close();
            reservation.close();
        }
    }
}
