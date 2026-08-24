package link.e4steam.api.testkit;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Retryability;
import link.e4steam.api.identity.IdentityService.PeerId;
import link.e4steam.api.network.NetworkService;
import link.e4steam.api.session.SessionService.SessionId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Deterministic authenticated addon-channel loopback with bounds and failure injection. */
public final class NetworkLoopbackHarness implements NetworkService, AutoCloseable {
    private final Map<ChannelId, LoopbackHandle> channels = new LinkedHashMap<>();
    private boolean frozen;
    private boolean closed;
    private boolean dropNext;

    @Override
    public synchronized ApiResult<ChannelHandle> register(ChannelDescriptor descriptor, MessageHandler handler) {
        if (descriptor == null || handler == null) throw new NullPointerException("channel");
        if (closed) return failure(ApiErrorCode.UNAVAILABLE, "network.closed", "network.register");
        if (frozen) return failure(ApiErrorCode.UNAVAILABLE, "network.frozen", "network.register");
        if (channels.size() >= ApiLimits.MAX_REGISTRATIONS_PER_FAMILY) {
            return failure(ApiErrorCode.QUEUE_FULL, "network.registration_full", "network.register");
        }
        if (channels.containsKey(descriptor.id())) {
            return failure(ApiErrorCode.INVALID_ARGUMENT, "network.duplicate_channel", "network.register");
        }
        LoopbackHandle handle = new LoopbackHandle(descriptor, handler);
        channels.put(descriptor.id(), handle);
        return ApiResult.<ChannelHandle>success(handle);
    }

    @Override
    public synchronized boolean registrationsFrozen() { return frozen; }

    /** Freezes registration and marks every existing channel available. */
    public synchronized void negotiate() {
        frozen = true;
        for (LoopbackHandle handle : channels.values()) handle.state = ChannelState.AVAILABLE;
    }

    /** Drops the next accepted payload as an unreliable-network test. */
    public synchronized void dropNext() { dropNext = true; }

    /** Returns registration count. */
    public synchronized int channelCount() { return channels.size(); }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        for (LoopbackHandle handle : channels.values()) handle.close();
        channels.clear();
    }

    private final class LoopbackHandle implements ChannelHandle {
        private final ChannelDescriptor descriptor;
        private final MessageHandler handler;
        private volatile ChannelState state = ChannelState.REGISTERED;
        private volatile boolean handleClosed;

        private LoopbackHandle(ChannelDescriptor descriptor, MessageHandler handler) {
            this.descriptor = descriptor;
            this.handler = handler;
        }

        @Override public ChannelDescriptor descriptor() { return descriptor; }
        @Override public ChannelState state() { return state; }

        @Override
        public CompletionStage<ApiResult<SendStatus>> send(SessionId sessionId, PeerId peerId, byte[] payload) {
            if (sessionId == null || peerId == null || payload == null) throw new NullPointerException("message");
            if (handleClosed) return completed(ApiResult.success(SendStatus.CLOSED));
            if (state != ChannelState.AVAILABLE) return completed(ApiResult.success(SendStatus.UNAVAILABLE));
            if (payload.length > descriptor.maximumMessageBytes()) {
                return completed(failure(ApiErrorCode.INVALID_ARGUMENT, "network.message_too_large", "network.send"));
            }
            byte[] copy = payload.clone();
            synchronized (NetworkLoopbackHarness.this) {
                if (dropNext) {
                    dropNext = false;
                    return completed(ApiResult.success(SendStatus.ACCEPTED));
                }
            }
            try {
                CompletionStage<ApiResult<Boolean>> handled = handler.onMessage(
                        new MessageContext(sessionId, peerId, descriptor.maximumVersion()), copy);
                if (handled == null) return completed(failure(ApiErrorCode.ADDON_FAILURE, "network.null_handler_result", "network.handler"));
                CompletableFuture<ApiResult<SendStatus>> result = new CompletableFuture<>();
                handled.whenComplete((value, throwable) -> {
                    if (throwable != null || value == null || !value.isSuccess()) {
                        result.complete(failure(ApiErrorCode.ADDON_FAILURE, "network.handler_failed", "network.handler"));
                    } else {
                        result.complete(ApiResult.success(SendStatus.ACCEPTED));
                    }
                });
                return result;
            } catch (RuntimeException exception) {
                return completed(failure(ApiErrorCode.ADDON_FAILURE, "network.handler_failed", "network.handler"));
            }
        }

        @Override public synchronized boolean isClosed() { return handleClosed; }
        @Override public synchronized void close() { if (!handleClosed) { handleClosed = true; state = ChannelState.CLOSED; synchronized (NetworkLoopbackHarness.this) { channels.remove(descriptor.id()); } } }
    }

    private static <T> CompletionStage<T> completed(T value) { return CompletableFuture.completedFuture(value); }
    private static <T> ApiResult<T> failure(ApiErrorCode code, String key, String operation) {
        return ApiResult.failure(new ApiError(code, "e4steam:" + key, Retryability.PERMANENT, operation, "", "testkit"));
    }
}
