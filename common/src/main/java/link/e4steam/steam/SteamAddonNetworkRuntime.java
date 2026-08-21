package link.e4steam.steam;

import link.e4steam.api.identity.IdentityService.PeerId;
import link.e4steam.api.network.NetworkService.ChannelDescriptor;
import link.e4steam.api.session.SessionService.SessionId;
import link.e4steam.internal.api.AddonNetworkCoordinator;
import link.e4steam.internal.api.CoreApiPlatform;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Authenticated bridge adapter for addon negotiation, fragmentation and replay
 * protection. It owns no Steam credentials and exposes no native objects.
 */
public final class SteamAddonNetworkRuntime implements AddonNetworkCoordinator.Transport {
    interface FrameSender {
        boolean sendHello(SteamConnectionBridge bridge, byte[] packet);
        boolean sendData(SteamConnectionBridge bridge, byte[] packet, boolean reliable);
    }

    private static final SteamAddonNetworkRuntime INSTANCE = new SteamAddonNetworkRuntime();
    private static final int MAX_ASSEMBLIES_PER_BRIDGE = 16;
    private static final long ASSEMBLY_TIMEOUT_NANOS =
            java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    private static final long REQUIRED_HELLO_TIMEOUT_NANOS =
            java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    private static final long OPTIONAL_HELLO_GRACE_NANOS =
            java.util.concurrent.TimeUnit.SECONDS.toNanos(1);

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<SteamConnectionBridge, BridgeState> bridges =
            new ConcurrentHashMap<>();

    static SteamAddonNetworkRuntime get() { return INSTANCE; }

    /** Installs the modern Addon API adapter without making it a retro dependency. */
    public static AutoCloseable installHooks() {
        return SteamAddonHooks.install(new SteamAddonHooks.Delegate() {
            @Override public void bridgeReady(SteamConnectionBridge bridge) {
                INSTANCE.bridgeReady(bridge);
            }
            @Override public void bridgeClosed(SteamConnectionBridge bridge) {
                INSTANCE.bridgeClosed(bridge);
            }
            @Override public void tick() { INSTANCE.tick(); }
            @Override public void accept(
                    SteamConnectionBridge bridge, byte frameType, byte[] payload) {
                INSTANCE.accept(bridge, frameType, payload);
            }
        });
    }

    void bridgeReady(SteamConnectionBridge bridge) {
        AddonNetworkCoordinator coordinator = coordinator();
        if (bridge == null || bridge.isClosed() || coordinator == null) return;
        BridgeIdentity identity = clientIdentity(bridge);
        if (identity == null) return;
        bridgeReady(bridge, identity.sessionId, identity.peerId, bridge.isHostSide(),
                new FrameSender() {
                    @Override public boolean sendHello(
                            SteamConnectionBridge target, byte[] packet) {
                        return SteamClientApiBridge.sendAddonHello(target, packet);
                    }
                    @Override public boolean sendData(
                            SteamConnectionBridge target, byte[] packet, boolean reliable) {
                        return SteamClientApiBridge.sendAddonFrame(target, packet, reliable);
                    }
                }, true);
    }

    void bridgeReady(SteamConnectionBridge bridge, SessionId sessionId, PeerId peerId,
                     boolean localIsHost, FrameSender sender) {
        bridgeReady(bridge, sessionId, peerId, localIsHost, sender, true);
    }

    void bridgeReady(SteamConnectionBridge bridge, SessionId sessionId, PeerId peerId,
                     boolean localIsHost, FrameSender sender, boolean gateUntilHello) {
        AddonNetworkCoordinator coordinator = coordinator();
        if (bridge == null || bridge.isClosed() || sessionId == null || peerId == null
                || sender == null || coordinator == null) return;
        BridgeState state = bridges.computeIfAbsent(bridge,
                ignored -> newState(sessionId, peerId, localIsHost, sender));
        if (!sessionId.equals(state.sessionId) || !peerId.equals(state.peerId)) {
            bridge.close(true);
            return;
        }
        coordinator.transport(this);
        state.requiredLocal = coordinator.hasRequiredChannels();
        state.gateUntilHello = gateUntilHello && coordinator.hasChannels();
        if (state.requiredLocal || state.gateUntilHello) bridge.requireAddonNegotiation();
        sendHello(bridge, state, coordinator);
    }

    void bridgeClosed(SteamConnectionBridge bridge) {
        BridgeState state = bridges.remove(bridge);
        if (state == null) return;
        AddonNetworkCoordinator coordinator = coordinator();
        if (coordinator != null && state.sessionId != null && state.peerId != null) {
            coordinator.closePeer(state.sessionId, state.peerId);
        }
        state.destroy();
    }

    void tick() {
        AddonNetworkCoordinator coordinator = coordinator();
        if (coordinator == null) return;
        long now = System.nanoTime();
        for (Map.Entry<SteamConnectionBridge, BridgeState> entry : bridges.entrySet()) {
            SteamConnectionBridge bridge = entry.getKey();
            BridgeState state = entry.getValue();
            if (bridge.isClosed()) {
                bridgeClosed(bridge);
                continue;
            }
            if (!state.helloSent) sendHello(bridge, state, coordinator);
            if (state.requiredLocal && !state.negotiated
                    && now - state.createdAtNanos >= REQUIRED_HELLO_TIMEOUT_NANOS) {
                bridge.close(true);
            } else if (state.gateUntilHello && !state.negotiated
                    && now - state.createdAtNanos >= OPTIONAL_HELLO_GRACE_NANOS) {
                state.gateUntilHello = false;
                bridge.markAddonNegotiated();
            }
        }
    }

    void accept(SteamConnectionBridge bridge, byte frameType, byte[] payload) {
        AddonNetworkCoordinator coordinator = coordinator();
        if (bridge == null || bridge.isClosed() || coordinator == null) return;
        BridgeState state = bridges.get(bridge);
        if (state == null) {
            bridgeReady(bridge);
            state = bridges.get(bridge);
        }
        if (state == null || state.sessionId == null || state.peerId == null) return;
        BridgeIdentity identity = new BridgeIdentity(state.sessionId, state.peerId);
        if (frameType == SteamProtocol.ADDON_HELLO) {
            acceptHello(bridge, state, identity, payload, coordinator);
        } else if (frameType == SteamProtocol.ADDON_DATA) {
            acceptData(state, identity, payload, coordinator);
        }
    }

    @Override public boolean send(SessionId sessionId, PeerId peerId, String channelId,
                                  int version, byte[] payload, boolean reliable) {
        SteamConnectionBridge bridge = find(sessionId, peerId);
        BridgeState state = bridge == null ? null : bridges.get(bridge);
        if (bridge == null || state == null || !state.negotiated
                || state.remoteNonce == null || !sessionId.equals(state.sessionId)
                || !peerId.equals(state.peerId)) return false;
        long sequence = state.outboundSequence.incrementAndGet();
        List<byte[]> fragments;
        try {
            fragments = SteamAddonProtocol.encodeData(
                    state.remoteNonce, channelId, version, sequence, payload);
        } catch (RuntimeException invalid) {
            return false;
        }
        for (byte[] fragment : fragments) {
            if (!state.sender.sendData(bridge,
                    SteamProtocol.encodeAddonData(bridge.connectionId(), fragment), reliable)) {
                return false;
            }
        }
        return true;
    }

    private void acceptHello(SteamConnectionBridge bridge, BridgeState state,
                             BridgeIdentity identity, byte[] payload,
                             AddonNetworkCoordinator coordinator) {
        SteamAddonProtocol.Hello hello = SteamAddonProtocol.decodeHello(payload);
        if (hello == null) return;
        List<ChannelDescriptor> remote = new ArrayList<>();
        for (SteamAddonProtocol.ChannelOffer offer : hello.channels()) {
            remote.add(offer.descriptor());
        }
        AddonNetworkCoordinator.Negotiation result = coordinator.negotiate(
                identity.sessionId, identity.peerId, remote, true);
        if (!result.compatible()) {
            bridge.close(true);
            return;
        }
        synchronized (state) {
            state.remoteNonce = hello.nonce();
            state.negotiated = true;
        }
        bridge.markAddonNegotiated();
        if (!state.helloSent) sendHello(bridge, state, coordinator);
    }

    private void acceptData(BridgeState state, BridgeIdentity identity, byte[] payload,
                            AddonNetworkCoordinator coordinator) {
        SteamAddonProtocol.Fragment fragment = SteamAddonProtocol.decodeData(payload);
        if (fragment == null) return;
        byte[] localNonce;
        synchronized (state) {
            if (!state.negotiated || state.localNonce == null) return;
            localNonce = state.localNonce.clone();
        }
        if (!java.security.MessageDigest.isEqual(localNonce, fragment.bindingNonce())) return;
        byte[] complete = state.accept(fragment, System.nanoTime());
        if (complete == null) return;
        coordinator.receive(identity.sessionId, identity.peerId, fragment.channelId(),
                fragment.version(), complete, true, !state.localIsHost);
    }

    private void sendHello(SteamConnectionBridge bridge, BridgeState state,
                           AddonNetworkCoordinator coordinator) {
        byte[] hello;
        synchronized (state) {
            if (state.helloSent || bridge.isClosed()) return;
            hello = SteamAddonProtocol.encodeHello(state.localNonce,
                    coordinator.localDescriptors());
            state.helloSent = true;
        }
        if (!state.sender.sendHello(bridge,
                SteamProtocol.encodeAddonHello(bridge.connectionId(), hello))) {
            synchronized (state) { state.helloSent = false; }
        }
    }

    private BridgeState newState(SessionId sessionId, PeerId peerId,
                                 boolean localIsHost, FrameSender sender) {
        byte[] nonce = new byte[SteamAddonProtocol.NONCE_SIZE];
        random.nextBytes(nonce);
        return new BridgeState(nonce, sessionId, peerId, localIsHost, sender);
    }

    private SteamConnectionBridge find(SessionId sessionId, PeerId peerId) {
        for (Map.Entry<SteamConnectionBridge, BridgeState> entry : bridges.entrySet()) {
            BridgeState state = entry.getValue();
            if (sessionId.equals(state.sessionId) && peerId.equals(state.peerId)
                    && !entry.getKey().isClosed()) return entry.getKey();
        }
        return null;
    }

    private static BridgeIdentity clientIdentity(SteamConnectionBridge bridge) {
        SteamClientApiBridge.SessionView view = SteamClientApiBridge.sessionView();
        if (!view.active()) return null;
        String opaque = SteamClientApiBridge.opaquePeerId(bridge.remoteSteamId());
        if (opaque == null) return null;
        return new BridgeIdentity(new SessionId(view.sessionId(), view.generation()),
                new PeerId(opaque));
    }

    private static AddonNetworkCoordinator coordinator() {
        CoreApiPlatform platform = CoreApiPlatform.current();
        return platform == null ? null : platform.addonNetwork();
    }

    private static final class BridgeIdentity {
        private final SessionId sessionId;
        private final PeerId peerId;
        private BridgeIdentity(SessionId sessionId, PeerId peerId) {
            this.sessionId = sessionId;
            this.peerId = peerId;
        }
    }

    private static final class BridgeState {
        private byte[] localNonce;
        private byte[] remoteNonce;
        private SessionId sessionId;
        private PeerId peerId;
        private boolean localIsHost;
        private final FrameSender sender;
        private boolean helloSent;
        private boolean negotiated;
        private boolean requiredLocal;
        private boolean gateUntilHello;
        private final long createdAtNanos = System.nanoTime();
        private final AtomicLong outboundSequence = new AtomicLong();
        private long highestCompletedSequence;
        private final LinkedHashMap<Long, Assembly> assemblies = new LinkedHashMap<>();

        private BridgeState(byte[] localNonce, SessionId sessionId,
                            PeerId peerId, boolean localIsHost, FrameSender sender) {
            this.localNonce = localNonce;
            this.sessionId = sessionId;
            this.peerId = peerId;
            this.localIsHost = localIsHost;
            this.sender = sender;
        }

        private synchronized byte[] accept(SteamAddonProtocol.Fragment fragment, long now) {
            assemblies.entrySet().removeIf(entry -> now - entry.getValue().createdAt
                    >= ASSEMBLY_TIMEOUT_NANOS);
            if (fragment.sequence() <= highestCompletedSequence) return null;
            Assembly assembly = assemblies.get(fragment.sequence());
            if (assembly == null) {
                if (assemblies.size() >= MAX_ASSEMBLIES_PER_BRIDGE) return null;
                assembly = new Assembly(fragment, now);
                assemblies.put(fragment.sequence(), assembly);
            }
            byte[] completed = assembly.add(fragment);
            if (completed == null) return null;
            assemblies.remove(fragment.sequence());
            highestCompletedSequence = fragment.sequence();
            assemblies.entrySet().removeIf(entry -> entry.getKey() <= highestCompletedSequence);
            return completed;
        }

        private synchronized void destroy() {
            if (localNonce != null) Arrays.fill(localNonce, (byte) 0);
            if (remoteNonce != null) Arrays.fill(remoteNonce, (byte) 0);
            localNonce = null;
            remoteNonce = null;
            assemblies.clear();
        }
    }

    private static final class Assembly {
        private final String channelId;
        private final int version;
        private final int count;
        private final int totalLength;
        private final long createdAt;
        private final byte[][] fragments;
        private int received;

        private Assembly(SteamAddonProtocol.Fragment first, long createdAt) {
            this.channelId = first.channelId();
            this.version = first.version();
            this.count = first.count();
            this.totalLength = first.totalLength();
            this.createdAt = createdAt;
            this.fragments = new byte[count][];
        }

        private byte[] add(SteamAddonProtocol.Fragment fragment) {
            if (!channelId.equals(fragment.channelId()) || version != fragment.version()
                    || count != fragment.count() || totalLength != fragment.totalLength()) return null;
            if (fragments[fragment.index()] == null) {
                fragments[fragment.index()] = fragment.payload();
                received++;
            }
            if (received != count) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream(totalLength);
            for (byte[] part : fragments) output.write(part, 0, part.length);
            byte[] result = output.toByteArray();
            return result.length == totalLength ? result : null;
        }
    }
}
