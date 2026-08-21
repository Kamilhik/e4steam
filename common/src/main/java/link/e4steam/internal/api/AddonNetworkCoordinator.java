package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.identity.IdentityService.PeerId;
import link.e4steam.api.network.NetworkService.ChannelDescriptor;
import link.e4steam.api.network.NetworkService.ChannelHandle;
import link.e4steam.api.network.NetworkService.ChannelState;
import link.e4steam.api.network.NetworkService.Direction;
import link.e4steam.api.network.NetworkService.MessageContext;
import link.e4steam.api.network.NetworkService.MessageHandler;
import link.e4steam.api.network.NetworkService.Requirement;
import link.e4steam.api.network.NetworkService.SendStatus;
import link.e4steam.api.scheduler.ExecutionContext;
import link.e4steam.api.session.SessionService.SessionId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Generation-safe addon channel negotiation and callback isolation. The Steam
 * adapter deliberately sees only descriptors and defensive byte arrays.
 */
public final class AddonNetworkCoordinator implements AutoCloseable {
    /** Bounded wire adapter supplied by the authenticated transport. */
    public interface Transport {
        boolean send(SessionId sessionId, PeerId peerId, String channelId,
                     int version, byte[] payload, boolean reliable);
    }

    interface SessionAccess {
        boolean matches(SessionId sessionId, PeerId peerId);
        boolean localIsHost(SessionId sessionId);
    }

    /** Safe immutable result of one peer negotiation. */
    public static final class Negotiation {
        private final boolean compatible;
        private final String reasonCode;
        private final Map<String, Integer> channels;

        private Negotiation(boolean compatible, String reasonCode, Map<String, Integer> channels) {
            this.compatible = compatible;
            this.reasonCode = reasonCode;
            this.channels = Collections.unmodifiableMap(new LinkedHashMap<>(channels));
        }

        public boolean compatible() { return compatible; }
        public String reasonCode() { return reasonCode; }
        public Map<String, Integer> channels() { return channels; }
        @Override public String toString() {
            return "Negotiation{compatible=" + compatible + ", channels=" + channels.size() + '}';
        }
    }

    private final Object lock = new Object();
    private final CoreSchedulerService scheduler;
    private final SessionAccess sessions;
    private final Map<String, Endpoint> channels = new LinkedHashMap<>();
    private final Map<PeerKey, PeerState> peers = new HashMap<>();
    private volatile Transport transport;
    private boolean closed;

    AddonNetworkCoordinator(CoreSchedulerService scheduler, CoreSessionRegistry sessions) {
        this(scheduler, new SessionAccess() {
            @Override public boolean matches(SessionId sessionId, PeerId peerId) {
                return sessions.matches(sessionId, peerId);
            }
            @Override public boolean localIsHost(SessionId sessionId) {
                return sessions.localIsHost(sessionId);
            }
        });
    }

    AddonNetworkCoordinator(CoreSchedulerService scheduler, SessionAccess sessions) {
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.sessions = java.util.Objects.requireNonNull(sessions, "sessions");
    }

    ApiResult<ChannelHandle> register(AddonId owner, ChannelDescriptor descriptor,
                                      MessageHandler handler, CoreCapabilityService capabilities) {
        if (owner == null || descriptor == null || handler == null || capabilities == null) {
            return SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT,
                    "network.register", "Validation");
        }
        Endpoint endpoint;
        synchronized (lock) {
            if (closed) return SafeApiErrors.failure(ApiErrorCode.UNAVAILABLE,
                    "network.register", "Shutdown");
            if (channels.size() >= SteamLimits.MAX_CHANNELS) {
                return SafeApiErrors.failure(ApiErrorCode.QUEUE_FULL,
                        "network.register", "RegistrationLimit");
            }
            String id = descriptor.id().value();
            if (channels.containsKey(id)) return SafeApiErrors.failure(
                    ApiErrorCode.INVALID_ARGUMENT, "network.register", "DuplicateId");
            endpoint = new Endpoint(owner, descriptor, handler, capabilities);
            channels.put(id, endpoint);
        }
        return ApiResult.success(endpoint.handle);
    }

    /** Returns deterministic descriptors for the authenticated wire hello. */
    public List<ChannelDescriptor> localDescriptors() {
        synchronized (lock) {
            List<ChannelDescriptor> result = new ArrayList<>();
            for (Endpoint endpoint : channels.values()) {
                if (!endpoint.handle.isClosed()) result.add(endpoint.descriptor);
            }
            result.sort(java.util.Comparator.comparing(value -> value.id().value()));
            return Collections.unmodifiableList(result);
        }
    }

    /** Returns whether gameplay must wait for addon negotiation. */
    public boolean hasRequiredChannels() {
        synchronized (lock) {
            for (Endpoint endpoint : channels.values()) {
                if (!endpoint.handle.isClosed()
                        && endpoint.descriptor.requirement() == Requirement.REQUIRED) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Returns whether any addon channel participates in the current handshake. */
    public boolean hasChannels() {
        synchronized (lock) {
            for (Endpoint endpoint : channels.values()) {
                if (!endpoint.handle.isClosed()) return true;
            }
            return false;
        }
    }

    /** Negotiates only after the caller has authenticated and resolved the peer. */
    public Negotiation negotiate(SessionId sessionId, PeerId peerId,
                                 List<ChannelDescriptor> remote, boolean authenticated) {
        if (!authenticated || !current(sessionId, peerId) || remote == null
                || remote.size() > SteamLimits.MAX_CHANNELS) {
            return new Negotiation(false, authenticated ? "stale-session" : "unauthenticated",
                    Collections.emptyMap());
        }
        Map<String, ChannelDescriptor> remoteById = new HashMap<>();
        for (ChannelDescriptor descriptor : remote) {
            if (descriptor == null || remoteById.put(descriptor.id().value(), descriptor) != null) {
                return new Negotiation(false, "invalid-channel-list", Collections.emptyMap());
            }
        }
        LinkedHashMap<String, Integer> selected = new LinkedHashMap<>();
        LinkedHashMap<String, NegotiatedChannel> negotiated = new LinkedHashMap<>();
        synchronized (lock) {
            if (closed) return new Negotiation(false, "runtime-closed", selected);
            for (Endpoint endpoint : channels.values()) {
                if (endpoint.handle.isClosed()) continue;
                ChannelDescriptor local = endpoint.descriptor;
                ChannelDescriptor offered = remoteById.remove(local.id().value());
                int version = offered == null ? 0 : compatibleVersion(local, offered);
                if (version == 0) {
                    if (local.requirement() == Requirement.REQUIRED
                            || offered != null && offered.requirement() == Requirement.REQUIRED) {
                        return rejectPeer(sessionId, peerId, "required-channel-incompatible");
                    }
                    continue;
                }
                selected.put(local.id().value(), version);
                negotiated.put(local.id().value(), new NegotiatedChannel(
                        version,
                        Math.min(local.maximumMessageBytes(), offered.maximumMessageBytes()),
                        Math.min(local.bytesPerSecond(), offered.bytesPerSecond()),
                        Math.min(local.queueMessages(), offered.queueMessages())));
            }
            for (ChannelDescriptor offered : remoteById.values()) {
                if (offered.requirement() == Requirement.REQUIRED) {
                    return rejectPeer(sessionId, peerId, "required-channel-missing");
                }
            }
            PeerState state = new PeerState(sessionId, peerId, negotiated);
            peers.put(new PeerKey(sessionId, peerId), state);
            refreshHandleStates();
        }
        return new Negotiation(true, "ok", selected);
    }

    /** Receives only a fully reassembled, authenticated, generation-bound message. */
    public ApiResult<Boolean> receive(SessionId sessionId, PeerId peerId, String channelId,
                                      int version, byte[] payload, boolean authenticated,
                                      boolean senderIsHost) {
        if (!authenticated || channelId == null || payload == null) {
            return SafeApiErrors.failure(ApiErrorCode.SECURITY_REJECTION,
                    "network.receive", "Unauthenticated");
        }
        final Endpoint endpoint;
        final PeerState peer;
        final byte[] defensive = payload.clone();
        synchronized (lock) {
            if (closed || !current(sessionId, peerId)) return SafeApiErrors.failure(
                    ApiErrorCode.STALE_HANDLE, "network.receive", "SessionGeneration");
            endpoint = channels.get(channelId);
            peer = peers.get(new PeerKey(sessionId, peerId));
            NegotiatedChannel negotiated = peer == null ? null : peer.channels.get(channelId);
            if (endpoint == null || endpoint.handle.isClosed() || negotiated == null
                    || negotiated.version != version) return SafeApiErrors.failure(
                    ApiErrorCode.SECURITY_REJECTION, "network.receive", "NotNegotiated");
            if (defensive.length < 1 || defensive.length > negotiated.maximumMessageBytes) {
                return SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT,
                        "network.receive", "Bounds");
            }
            if (!directionAllowsReceive(endpoint.descriptor.direction(), senderIsHost)) {
                return SafeApiErrors.failure(ApiErrorCode.SECURITY_REJECTION,
                        "network.receive", "Direction");
            }
            if (!peer.allowInbound(endpoint.owner.value(), channelId, defensive.length,
                    negotiated.bytesPerSecond, System.nanoTime())) {
                return SafeApiErrors.failure(ApiErrorCode.RATE_LIMITED,
                        "network.receive", "ChannelBudget");
            }
            if (!peer.beginInbound(endpoint.owner.value(), channelId,
                    negotiated.queueMessages)) {
                return SafeApiErrors.failure(ApiErrorCode.QUEUE_FULL,
                        "network.receive", "InboundQueue");
            }
        }
        ApiResult<link.e4steam.api.scheduler.TaskHandle> scheduled = scheduler.execute(
                ExecutionContext.ADDON_WORKER,
                () -> invokeHandler(endpoint, peer, channelId, version, defensive),
                Duration.ofSeconds(2));
        if (!scheduled.isSuccess()) {
            synchronized (lock) {
                peer.endInbound(endpoint.owner.value(), channelId);
            }
            return scheduled.error().map(ApiResult::<Boolean>failure).orElseGet(
                    () -> SafeApiErrors.failure(ApiErrorCode.QUEUE_FULL,
                            "network.receive", "WorkerQueue"));
        }
        return ApiResult.success(true);
    }

    public void transport(Transport transport) { this.transport = transport; }

    public void closeSession(long generation) {
        synchronized (lock) {
            peers.entrySet().removeIf(entry -> entry.getKey().generation == generation);
            refreshHandleStates();
        }
    }

    /** Removes one disconnected peer without invalidating the other peers in a host session. */
    public void closePeer(SessionId sessionId, PeerId peerId) {
        if (sessionId == null || peerId == null) return;
        synchronized (lock) {
            peers.remove(new PeerKey(sessionId, peerId));
            refreshHandleStates();
        }
    }

    @Override public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            peers.clear();
            for (Endpoint endpoint : channels.values()) endpoint.handle.closeInternal();
            channels.clear();
        }
    }

    private void invokeHandler(Endpoint endpoint, PeerState peer, String channelId,
                               int version, byte[] payload) {
        boolean handedOff = false;
        try {
            CompletionStage<ApiResult<Boolean>> stage = endpoint.handler.onMessage(
                    new MessageContext(peer.sessionId, peer.peerId, version), payload.clone());
            if (stage == null) return;
            handedOff = true;
            stage.toCompletableFuture()
                    .orTimeout(2L, java.util.concurrent.TimeUnit.SECONDS)
                    .whenComplete((result, failure) -> {
                        synchronized (lock) {
                            peer.endInbound(endpoint.owner.value(), channelId);
                        }
                    });
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable ignored) {
            // Handler failures are isolated to the addon channel.
        } finally {
            if (!handedOff) synchronized (lock) {
                peer.endInbound(endpoint.owner.value(), channelId);
            }
        }
    }

    private CompletionStage<ApiResult<SendStatus>> send(Endpoint endpoint,
                                                        SessionId sessionId,
                                                        PeerId peerId,
                                                        byte[] payload) {
        if (payload == null || payload.length < 1
                || payload.length > endpoint.descriptor.maximumMessageBytes()) {
            return completed(SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT,
                    "network.send", "Bounds"));
        }
        final PeerState peer;
        final int version;
        final byte[] copy = payload.clone();
        synchronized (lock) {
            if (closed || endpoint.handle.isClosed()) return completed(
                    ApiResult.success(SendStatus.CLOSED));
            if (!current(sessionId, peerId)) return completed(
                    ApiResult.success(SendStatus.STALE_SESSION));
            peer = peers.get(new PeerKey(sessionId, peerId));
            NegotiatedChannel selected = peer == null ? null
                    : peer.channels.get(endpoint.descriptor.id().value());
            if (selected == null) return completed(ApiResult.success(SendStatus.UNAVAILABLE));
            version = selected.version;
            if (copy.length > selected.maximumMessageBytes) {
                return completed(SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT,
                        "network.send", "NegotiatedBounds"));
            }
            if (!directionAllowsSend(endpoint.descriptor.direction(),
                    sessions.localIsHost(sessionId))) {
                return completed(ApiResult.success(SendStatus.UNAVAILABLE));
            }
            if (!peer.allowOutbound(endpoint.owner.value(), endpoint.descriptor.id().value(),
                    copy.length, selected.bytesPerSecond, System.nanoTime())) {
                return completed(ApiResult.success(SendStatus.RATE_LIMITED));
            }
            if (!peer.beginOutbound(endpoint.owner.value(), endpoint.descriptor.id().value(),
                    selected.queueMessages)) {
                return completed(ApiResult.success(SendStatus.QUEUE_FULL));
            }
        }
        Transport currentTransport = transport;
        boolean reliable = endpoint.descriptor.delivery()
                != link.e4steam.api.network.NetworkService.Delivery.UNRELIABLE;
        if (currentTransport == null) {
            synchronized (lock) {
                peer.endOutbound(endpoint.owner.value(), endpoint.descriptor.id().value());
            }
            return completed(ApiResult.success(SendStatus.UNAVAILABLE));
        }
        boolean accepted = currentTransport.send(sessionId, peerId,
                endpoint.descriptor.id().value(), version, copy, reliable);
        synchronized (lock) {
            peer.endOutbound(endpoint.owner.value(), endpoint.descriptor.id().value());
        }
        return completed(ApiResult.success(accepted ? SendStatus.ACCEPTED : SendStatus.QUEUE_FULL));
    }

    private void unregister(Endpoint endpoint) {
        synchronized (lock) {
            channels.remove(endpoint.descriptor.id().value(), endpoint);
            for (PeerState peer : peers.values()) {
                peer.channels.remove(endpoint.descriptor.id().value());
            }
            refreshHandleStates();
        }
    }

    private boolean current(SessionId sessionId, PeerId peerId) {
        if (sessionId == null || peerId == null) return false;
        return sessions.matches(sessionId, peerId);
    }

    private Negotiation rejectPeer(SessionId sessionId, PeerId peerId, String reason) {
        peers.remove(new PeerKey(sessionId, peerId));
        refreshHandleStates();
        return new Negotiation(false, reason, Collections.emptyMap());
    }

    private void refreshHandleStates() {
        for (Endpoint endpoint : channels.values()) {
            boolean available = false;
            for (PeerState peer : peers.values()) {
                if (peer.channels.containsKey(endpoint.descriptor.id().value())) {
                    available = true;
                    break;
                }
            }
            endpoint.handle.state = available ? ChannelState.AVAILABLE : ChannelState.REGISTERED;
        }
    }

    private static int compatibleVersion(ChannelDescriptor local, ChannelDescriptor remote) {
        if (!local.schemaId().equals(remote.schemaId())
                || local.delivery() != remote.delivery()
                || local.direction() != remote.direction()) return 0;
        int minimum = Math.max(local.minimumVersion(), remote.minimumVersion());
        int maximum = Math.min(local.maximumVersion(), remote.maximumVersion());
        return maximum >= minimum ? maximum : 0;
    }

    private static boolean directionAllowsReceive(Direction direction, boolean senderIsHost) {
        return direction == Direction.BIDIRECTIONAL
                || senderIsHost && direction == Direction.HOST_TO_CLIENT
                || !senderIsHost && direction == Direction.CLIENT_TO_HOST;
    }

    private static boolean directionAllowsSend(Direction direction, boolean localIsHost) {
        return direction == Direction.BIDIRECTIONAL
                || localIsHost && direction == Direction.HOST_TO_CLIENT
                || !localIsHost && direction == Direction.CLIENT_TO_HOST;
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private final class Endpoint {
        private final AddonId owner;
        private final ChannelDescriptor descriptor;
        private final MessageHandler handler;
        private final CoreCapabilityService capabilities;
        private final Handle handle = new Handle();

        private Endpoint(AddonId owner, ChannelDescriptor descriptor,
                         MessageHandler handler, CoreCapabilityService capabilities) {
            this.owner = owner;
            this.descriptor = descriptor;
            this.handler = handler;
            this.capabilities = capabilities;
        }

        private final class Handle extends CoreRegistration implements ChannelHandle {
            private volatile ChannelState state = ChannelState.REGISTERED;
            private Handle() { super(null); }
            @Override public ChannelDescriptor descriptor() { return descriptor; }
            @Override public ChannelState state() {
                return isClosed() ? ChannelState.CLOSED : state;
            }
            @Override public CompletionStage<ApiResult<SendStatus>> send(
                    SessionId sessionId, PeerId peerId, byte[] payload) {
                if (!capabilities.has(link.e4steam.api.capability.Capabilities.NETWORK_CHANNEL_REGISTER)) {
                    return completed(SafeApiErrors.failure(ApiErrorCode.CAPABILITY_DENIED,
                            "network.send", "PolicyDenied"));
                }
                return AddonNetworkCoordinator.this.send(Endpoint.this,
                        sessionId, peerId, payload);
            }
            @Override public void close() {
                if (isClosed()) return;
                super.close();
                unregister(Endpoint.this);
            }
            private void closeInternal() { super.close(); state = ChannelState.CLOSED; }
        }
    }

    private static final class PeerKey {
        private final long generation;
        private final String session;
        private final String peer;
        private PeerKey(SessionId sessionId, PeerId peerId) {
            this.generation = sessionId.generation();
            this.session = sessionId.value();
            this.peer = peerId.value();
        }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PeerKey)) return false;
            PeerKey value = (PeerKey) other;
            return generation == value.generation && session.equals(value.session)
                    && peer.equals(value.peer);
        }
        @Override public int hashCode() {
            int result = session.hashCode();
            result = 31 * result + peer.hashCode();
            return 31 * result + (int) (generation ^ generation >>> 32);
        }
    }

    private static final class PeerState {
        private final SessionId sessionId;
        private final PeerId peerId;
        private final Map<String, NegotiatedChannel> channels;
        private final Budget inbound = new Budget();
        private final Budget outbound = new Budget();
        private final Map<String, Integer> inboundQueued = new HashMap<>();
        private final Map<String, Integer> outboundQueued = new HashMap<>();

        private PeerState(SessionId sessionId, PeerId peerId,
                          Map<String, NegotiatedChannel> channels) {
            this.sessionId = sessionId;
            this.peerId = peerId;
            this.channels = new LinkedHashMap<>(channels);
        }
        private boolean allowInbound(String owner, String id, int bytes, int channelMaximum, long now) {
            return inbound.allow("*", bytes, SteamLimits.GLOBAL_BYTES_PER_SECOND, now)
                    && inbound.allow("addon:" + owner, bytes, SteamLimits.ADDON_BYTES_PER_SECOND, now)
                    && inbound.allow("channel:" + id, bytes, channelMaximum, now);
        }
        private boolean allowOutbound(String owner, String id, int bytes, int channelMaximum, long now) {
            return outbound.allow("*", bytes, SteamLimits.GLOBAL_BYTES_PER_SECOND, now)
                    && outbound.allow("addon:" + owner, bytes, SteamLimits.ADDON_BYTES_PER_SECOND, now)
                    && outbound.allow("channel:" + id, bytes, channelMaximum, now);
        }
        private boolean beginInbound(String owner, String id, int maximum) {
            return incrementAll(inboundQueued, owner, id, maximum);
        }
        private boolean beginOutbound(String owner, String id, int maximum) {
            return incrementAll(outboundQueued, owner, id, maximum);
        }
        private void endInbound(String owner, String id) {
            decrementAll(inboundQueued, owner, id);
        }
        private void endOutbound(String owner, String id) {
            decrementAll(outboundQueued, owner, id);
        }
        private static boolean incrementAll(Map<String, Integer> values, String owner,
                                            String id, int channelMaximum) {
            if (!increment(values, "*", SteamLimits.GLOBAL_QUEUED_MESSAGES)) return false;
            if (!increment(values, "addon:" + owner, SteamLimits.ADDON_QUEUED_MESSAGES)) {
                decrement(values, "*");
                return false;
            }
            if (!increment(values, "channel:" + id, channelMaximum)) {
                decrement(values, "addon:" + owner);
                decrement(values, "*");
                return false;
            }
            return true;
        }
        private static void decrementAll(Map<String, Integer> values, String owner, String id) {
            decrement(values, "channel:" + id);
            decrement(values, "addon:" + owner);
            decrement(values, "*");
        }
        private static boolean increment(Map<String, Integer> values, String id, int maximum) {
            int next = values.getOrDefault(id, 0) + 1;
            if (next > maximum) return false;
            values.put(id, next);
            return true;
        }
        private static void decrement(Map<String, Integer> values, String id) {
            int next = values.getOrDefault(id, 0) - 1;
            if (next <= 0) values.remove(id); else values.put(id, next);
        }
    }

    private static final class Budget {
        private static final long WINDOW_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
        private final Map<String, Window> windows = new HashMap<>();
        private boolean allow(String id, int bytes, int maximum, long now) {
            Window window = windows.computeIfAbsent(id, ignored -> new Window(now));
            if (now - window.started >= WINDOW_NANOS || now < window.started) {
                window.started = now;
                window.bytes = 0;
            }
            if (bytes > maximum - window.bytes) return false;
            window.bytes += bytes;
            return true;
        }
    }

    private static final class Window {
        private long started;
        private int bytes;
        private Window(long started) { this.started = started; }
    }

    private static final class SteamLimits {
        private static final int MAX_CHANNELS = 32;
        private static final int GLOBAL_BYTES_PER_SECOND = 4 * 1_048_576;
        private static final int ADDON_BYTES_PER_SECOND = 2 * 1_048_576;
        private static final int GLOBAL_QUEUED_MESSAGES = 256;
        private static final int ADDON_QUEUED_MESSAGES = 64;
    }

    private static final class NegotiatedChannel {
        private final int version;
        private final int maximumMessageBytes;
        private final int bytesPerSecond;
        private final int queueMessages;
        private NegotiatedChannel(int version, int maximumMessageBytes,
                                  int bytesPerSecond, int queueMessages) {
            this.version = version;
            this.maximumMessageBytes = maximumMessageBytes;
            this.bytesPerSecond = bytesPerSecond;
            this.queueMessages = queueMessages;
        }
    }
}
