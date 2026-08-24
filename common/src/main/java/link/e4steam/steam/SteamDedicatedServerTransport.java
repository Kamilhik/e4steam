package link.e4steam.steam;

import link.e4steam.api.ApiConstants;
import link.e4steam.api.identity.IdentityService.PeerId;
import link.e4steam.api.session.SessionService.SessionId;
import link.e4steam.internal.dedicated.DedicatedAdmissionGate;
import link.e4steam.internal.dedicated.DedicatedServerController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared bounded framing/bridge layer running above the headless GameServer backend. */
public final class SteamDedicatedServerTransport implements SteamBridgeRuntime, AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger("e4steam");
    private static final int CHANNEL = 480;
    private static final int MAX_PACKETS_PER_TICK = 64;
    private static final int LOOPBACK_CONNECT_TIMEOUT_MILLIS = 1_000;
    private static final long ADMISSION_TIMEOUT_SECONDS = 16L;
    private static final long PENDING_PEER_TTL_MILLIS = 20_000L;
    private static final long REJECT_CLOSE_DELAY_MILLIS = 100L;

    private final SteamGameServerRuntimeBackend backend;
    private final DedicatedServerController controller;
    private final DedicatedAdmissionGate admission;
    private final SteamBridgeRegistry<SteamConnectionBridge, AutoCloseable> bridges;
    private final SteamOutboundQueue<SteamConnectionBridge> outbound =
            new SteamOutboundQueue<>(2048, 1536, 0, 128, 256, 64);
    private final ConcurrentHashMap<Long, Long> pendingPeers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> peersAwaitingClose = new ConcurrentHashMap<>();
    private final Set<Long> activePeers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<SteamConnectionBridge, AutoCloseable> ingress =
            new ConcurrentHashMap<>();
    private final ThreadPoolExecutor admissionExecutor;
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile Thread packetThread;
    private volatile SteamOutboundQueue.Packet<SteamConnectionBridge> deferredPacket;
    private final SteamAddonNetworkRuntime.FrameSender addonSender =
            new SteamAddonNetworkRuntime.FrameSender() {
                @Override public boolean sendHello(
                        SteamConnectionBridge bridge, byte[] packet) {
                    return offerControl(bridge, packet, SteamOutboundQueue.Kind.ADDON_HELLO);
                }
                @Override public boolean sendData(
                        SteamConnectionBridge bridge, byte[] packet, boolean reliable) {
                    return running.get() && !bridge.isClosed()
                            && outbound.offerAddonData(bridge.remoteSteamId(),
                            bridge.connectionId(), packet, !reliable, bridge);
                }
            };

    public SteamDedicatedServerTransport(
            SteamGameServerRuntimeBackend backend,
            DedicatedServerController controller,
            int capacity
    ) {
        this.backend = java.util.Objects.requireNonNull(backend, "backend");
        this.controller = java.util.Objects.requireNonNull(controller, "controller");
        this.bridges = new SteamBridgeRegistry<>(capacity);
        this.admissionExecutor = new ThreadPoolExecutor(
                1, Math.min(4, capacity), 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(16, capacity * 2)),
                daemonFactory("e4steam-dedicated-admission"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.admission = new DedicatedAdmissionGate(
                new DedicatedAdmissionGate.Authenticator() {
                    @Override public java.util.concurrent.CompletionStage<Boolean> authenticate(
                            long steamId, byte[] ticket, long generation) {
                        return SteamDedicatedServerTransport.this.backend.authenticate(
                                steamId, ticket, generation);
                    }
                },
                new DedicatedAdmissionGate.CorePolicy() {
                    @Override public boolean banned(long steamId) {
                        return controller.isBanned(steamId);
                    }
                    @Override public boolean whitelistRequired() {
                        return controller.whitelistRequired();
                    }
                    @Override public boolean whitelisted(long steamId) {
                        return controller.isWhitelisted(steamId);
                    }
                    @Override public int players() { return activePeers.size(); }
                    @Override public int capacity() { return capacity; }
                    @Override public java.util.concurrent.CompletionStage<Boolean> addonPolicy(long steamId) {
                        return CompletableFuture.completedFuture(true);
                    }
                }
        );
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        backend.peerListener(new SteamGameServerRuntimeBackend.PeerListener() {
            @Override public boolean onSessionRequest(long remoteSteamId) {
                long now = System.currentTimeMillis();
                expirePendingPeers(now);
                if (remoteSteamId == 0L || !controller.accepting()
                        || activePeers.contains(remoteSteamId)
                        || peersAwaitingClose.containsKey(remoteSteamId)) return false;
                Long existing = pendingPeers.get(remoteSteamId);
                if (existing != null && existing.longValue() > now) return true;
                if (pendingPeers.size() >= controller.maxPeers() * 2) return false;
                pendingPeers.put(remoteSteamId, now + PENDING_PEER_TTL_MILLIS);
                return true;
            }
            @Override public void onSessionFailed(long remoteSteamId, int reason, String detail) {
                closeRemote(remoteSteamId);
            }
        });
        Thread thread = new Thread(this::runPackets, "e4steam-dedicated-packets");
        thread.setDaemon(true);
        packetThread = thread;
        thread.start();
    }

    /** Privacy-safe active peers for the dedicated Addon API session projection. */
    public java.util.Set<String> safeOpaquePeerIds() {
        long generation = controller.generation();
        if (generation <= 0L) return java.util.Collections.emptySet();
        java.util.TreeSet<String> peers = new java.util.TreeSet<>();
        for (Long steamId : activePeers) {
            if (steamId != null && steamId.longValue() != 0L) {
                peers.add(SteamPeerPrivacy.opaquePeerId(generation, steamId.longValue()));
            }
        }
        return java.util.Collections.unmodifiableSet(peers);
    }

    public DedicatedServerController.DedicatedPeerIdentity safePeerIdentity(String opaquePeerId) {
        long generation = controller.generation();
        if (generation <= 0L || opaquePeerId == null) return null;
        for (Long steamId : activePeers) {
            if (steamId == null || steamId.longValue() == 0L) continue;
            long value = steamId.longValue();
            String opaque = SteamPeerPrivacy.opaquePeerId(generation, value);
            if (opaque.equals(opaquePeerId)) {
                return new DedicatedServerController.DedicatedPeerIdentity(
                        opaque,
                        SteamMinecraftIdentity.uuid(value),
                        SteamMinecraftIdentity.safeName(value));
            }
        }
        return null;
    }

    private void runPackets() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(SteamProtocol.MAX_ACCEPTED_STEAM_PACKET_SIZE);
        while (running.get()) {
            try {
                receive(buffer);
                expirePendingPeers(System.currentTimeMillis());
                closeRejectedPeers(System.currentTimeMillis());
                drainOutbound();
                SteamAddonNetworkRuntime.get().tick();
                Thread.sleep(2L);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException failure) {
                if (running.get()) LOGGER.warn("Dedicated Steam packet loop stopped", failure);
                close();
                controller.transportFailed("DEDICATED_TRANSPORT_FAILED");
                return;
            }
        }
    }

    private void receive(ByteBuffer buffer) throws IOException {
        for (int count = 0; count < MAX_PACKETS_PER_TICK; count++) {
            int size = backend.availablePacketSize(CHANNEL);
            if (size == 0) return;
            if (size < 0 || size > SteamProtocol.MAX_ACCEPTED_STEAM_PACKET_SIZE) {
                throw new IOException("Steam returned an invalid dedicated packet size");
            }
            buffer.clear();
            SteamGameServerRuntimeBackend.ReceivedPacket packet = backend.receive(buffer, CHANNEL);
            if (packet.size() <= 0 || packet.size() > SteamProtocol.MAX_PACKET_SIZE
                    || packet.remoteSteamId() == 0L) continue;
            buffer.flip();
            buffer.limit(packet.size());
            SteamProtocol.Frame frame = SteamProtocol.decode(buffer);
            if (frame != null) dispatch(packet.remoteSteamId(), frame);
        }
    }

    private void dispatch(long remoteSteamId, SteamProtocol.Frame frame) {
        SteamBridgeRegistry.Key key = new SteamBridgeRegistry.Key(remoteSteamId, frame.connectionId());
        switch (frame.type()) {
            case SteamProtocol.DEDICATED_OPEN:
                LOGGER.info("Received a dedicated Steam authentication handshake");
                handleOpen(remoteSteamId, key, frame.payload());
                return;
            case SteamProtocol.ADDON_HELLO:
            case SteamProtocol.ADDON_DATA: {
                SteamConnectionBridge bridge = bridges.get(key);
                if (bridge != null && !bridge.isClosed()) {
                    SteamAddonNetworkRuntime.get().accept(
                            bridge, frame.type(), frame.payload());
                }
                return;
            }
            case SteamProtocol.DATA: {
                SteamConnectionBridge bridge = bridges.get(key);
                if (bridge != null) bridge.acceptSteamData(frame.payload());
                return;
            }
            case SteamProtocol.FIN: {
                SteamConnectionBridge bridge = bridges.get(key);
                if (bridge != null) bridge.acceptRemoteFin();
                return;
            }
            case SteamProtocol.RESET: {
                SteamConnectionBridge bridge = bridges.get(key);
                if (bridge != null) bridge.resetFromRemote();
                return;
            }
            default:
        }
    }

    private void handleOpen(long remoteSteamId, SteamBridgeRegistry.Key key, byte[] payload) {
        Long pendingDeadline = pendingPeers.get(remoteSteamId);
        if (!running.get() || !controller.accepting() || bridges.contains(key)
                || activePeers.contains(remoteSteamId) || pendingDeadline == null
                || pendingDeadline.longValue() <= System.currentTimeMillis()) {
            rejectOpen(remoteSteamId, key.connectionId());
            return;
        }
        SteamProtocol.DedicatedOpen decoded = SteamProtocol.decodeDedicatedOpen(payload);
        if (decoded == null) {
            rejectOpen(remoteSteamId, key.connectionId());
            return;
        }
        byte[] ticket = decoded.takeTicket();
        byte[] nonce = decoded.nonce();
        long generation = decoded.generation();
        decoded.destroy();
        DedicatedAdmissionGate.Request request;
        try {
            request = new DedicatedAdmissionGate.Request(
                    remoteSteamId, generation, ApiConstants.WIRE_PROTOCOL_VERSION, nonce, ticket);
        } catch (RuntimeException invalid) {
            java.util.Arrays.fill(ticket, (byte) 0);
            java.util.Arrays.fill(nonce, (byte) 0);
            rejectOpen(remoteSteamId, key.connectionId());
            return;
        }
        java.util.Arrays.fill(ticket, (byte) 0);
        java.util.Arrays.fill(nonce, (byte) 0);
        try {
            admissionExecutor.execute(new AdmissionTask(remoteSteamId, key, request));
        } catch (java.util.concurrent.RejectedExecutionException full) {
            request.close();
            rejectOpen(remoteSteamId, key.connectionId());
        }
    }

    /** Keeps socket creation and Minecraft hand-off away from Steam's callback executor. */
    private void submitAdmissionResult(
            long remoteSteamId,
            SteamBridgeRegistry.Key key,
            DedicatedAdmissionGate.Result result,
            Throwable failure
    ) {
        try {
            admissionExecutor.execute(() -> {
                pendingPeers.remove(remoteSteamId);
                if (failure != null || result == null || !result.allowed()) {
                    backend.endAuthentication(remoteSteamId);
                    sendStandaloneReset(remoteSteamId, key.connectionId());
                    schedulePeerClose(remoteSteamId);
                    return;
                }
                openMinecraftBridge(key, result);
            });
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            pendingPeers.remove(remoteSteamId);
            backend.endAuthentication(remoteSteamId);
            sendStandaloneReset(remoteSteamId, key.connectionId());
            schedulePeerClose(remoteSteamId);
        }
    }

    private void openMinecraftBridge(
            SteamBridgeRegistry.Key key,
            DedicatedAdmissionGate.Result admitted
    ) {
        Socket socket = new Socket();
        SteamConnectionBridge bridge = null;
        AutoCloseable ingressLease = null;
        boolean handedOff = false;
        try {
            if (!running.get() || !controller.accepting()
                    || admitted.generation() != controller.generation()
                    || !activePeers.add(admitted.internalSteamId())) {
                throw new IOException("Dedicated admission became stale");
            }
            socket.connect(new InetSocketAddress("127.0.0.1", controller.minecraftPort()),
                    LOOPBACK_CONNECT_TIMEOUT_MILLIS);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            ingressLease = controller.registerAuthenticatedIngress(
                    socket.getLocalPort(), admitted.internalSteamId(), admitted.generation());
            bridge = new SteamConnectionBridge(
                    this, admitted.internalSteamId(), key.connectionId(), socket, true, null);
            SteamBridgeRegistry.Registration registration = bridges.register(
                    key, bridge, () -> running.get() && controller.accepting());
            if (registration != SteamBridgeRegistry.Registration.REGISTERED) {
                throw new IOException("Dedicated bridge registration rejected");
            }
            ingress.put(bridge, ingressLease);
            ingressLease = null;
            SessionId addonSession = new SessionId(
                    SteamPeerPrivacy.dedicatedSessionId(admitted.generation()),
                    admitted.generation());
            PeerId addonPeer = new PeerId(SteamPeerPrivacy.opaquePeerId(
                    admitted.generation(), admitted.internalSteamId()));
            SteamAddonNetworkRuntime.get().bridgeReady(
                    bridge, addonSession, addonPeer, true, addonSender, true);
            if (!offerControl(bridge, SteamProtocol.encodeDedicatedOpenAck(
                    bridge.connectionId(), admitted.generation()),
                    SteamOutboundQueue.Kind.DEDICATED_OPEN_ACK)) {
                throw new IOException("Dedicated acknowledgement queue is full");
            }
            bridge.start();
            handedOff = true;
            controller.players(activePeers.size());
            LOGGER.info("Accepted an authenticated dedicated e4steam peer");
        } catch (IOException | RuntimeException failure) {
            activePeers.remove(admitted.internalSteamId());
            backend.endAuthentication(admitted.internalSteamId());
            sendStandaloneReset(admitted.internalSteamId(), key.connectionId());
            schedulePeerClose(admitted.internalSteamId());
            LOGGER.debug("Dedicated peer admission failed [{}]", safeFailure(failure));
        } finally {
            close(ingressLease);
            if (!handedOff) {
                if (bridge != null) bridge.close(false);
                else close(socket);
            }
        }
    }

    private void drainOutbound() throws IOException {
        for (int count = 0; count < MAX_PACKETS_PER_TICK; count++) {
            SteamOutboundQueue.Packet<SteamConnectionBridge> packet = deferredPacket;
            if (packet != null) deferredPacket = null;
            else packet = outbound.poll();
            if (packet == null) return;
            SteamConnectionBridge bridge = packet.bridge();
            if (bridge != null && packet.kind() != SteamOutboundQueue.Kind.RESET
                    && bridge.isClosed()) continue;
            long now = System.currentTimeMillis();
            if (bridge != null && packet.kind() != SteamOutboundQueue.Kind.RESET
                    && !bridge.isOutboundReady(now)) {
                deferredPacket = packet;
                return;
            }
            if (bridge != null && packet.kind() == SteamOutboundQueue.Kind.DATA
                    && !bridge.isOutboundDataReady(now)) {
                deferredPacket = packet;
                return;
            }
            ByteBuffer direct = ByteBuffer.allocateDirect(packet.payload().length);
            direct.put(packet.payload()).flip();
            boolean unreliable = packet.kind() == SteamOutboundQueue.Kind.ADDON_DATAGRAM;
            boolean sent = backend.send(packet.remoteSteamId(), direct, unreliable, CHANNEL);
            if (!sent) {
                if (unreliable) continue;
                if (bridge != null) bridge.close(false);
                else backend.closePeer(packet.remoteSteamId());
                continue;
            }
            if (bridge != null && packet.kind() == SteamOutboundQueue.Kind.FIN) {
                bridge.markFinSubmitted();
            } else if (bridge != null && packet.kind() == SteamOutboundQueue.Kind.RESET) {
                bridge.markResetSubmitted();
            }
        }
    }

    @Override public boolean sendData(SteamConnectionBridge bridge, byte[] payload) {
        return running.get() && !bridge.isClosed() && outbound.offerData(
                bridge.remoteSteamId(), bridge.connectionId(),
                SteamProtocol.encodeData(bridge.connectionId(), payload), bridge);
    }

    @Override public boolean sendFin(SteamConnectionBridge bridge) {
        return offerControl(bridge, SteamProtocol.encodeFin(bridge.connectionId()),
                SteamOutboundQueue.Kind.FIN);
    }

    @Override public boolean sendReset(SteamConnectionBridge bridge) {
        return offerControl(bridge, SteamProtocol.encodeReset(bridge.connectionId()),
                SteamOutboundQueue.Kind.RESET);
    }

    @Override public void closeUdpBridge(SteamConnectionBridge bridge) {
        // Dedicated virtual UDP is negotiated by the separate provider API; no implicit port forwarding.
    }

    @Override public void unregister(SteamConnectionBridge bridge) {
        SteamAddonNetworkRuntime.get().bridgeClosed(bridge);
        SteamBridgeRegistry.Key key = new SteamBridgeRegistry.Key(
                bridge.remoteSteamId(), bridge.connectionId());
        outbound.purge(bridge);
        if (deferredPacket != null && deferredPacket.bridge() == bridge) {
            deferredPacket = null;
        }
        if (!bridges.remove(key, bridge)) return;
        close(ingress.remove(bridge));
        activePeers.remove(bridge.remoteSteamId());
        backend.endAuthentication(bridge.remoteSteamId());
        backend.closePeer(bridge.remoteSteamId());
        controller.players(activePeers.size());
    }

    private boolean offerControl(
            SteamConnectionBridge bridge,
            byte[] payload,
            SteamOutboundQueue.Kind kind
    ) {
        return running.get() && outbound.offerControl(
                bridge.remoteSteamId(), bridge.connectionId(), payload, kind, bridge);
    }

    private void sendStandaloneReset(long steamId, int connectionId) {
        if (!running.get()) return;
        outbound.offerControl(steamId, connectionId,
                SteamProtocol.encodeReset(connectionId), SteamOutboundQueue.Kind.RESET, null);
    }

    private void rejectOpen(long steamId, int connectionId) {
        pendingPeers.remove(steamId);
        backend.endAuthentication(steamId);
        sendStandaloneReset(steamId, connectionId);
        schedulePeerClose(steamId);
    }

    private void expirePendingPeers(long now) {
        pendingPeers.forEach((steamId, deadline) -> {
            if (deadline != null && deadline.longValue() <= now
                    && pendingPeers.remove(steamId, deadline)) {
                backend.closePeer(steamId.longValue());
            }
        });
    }

    private void schedulePeerClose(long steamId) {
        if (steamId != 0L && running.get()) {
            peersAwaitingClose.put(steamId,
                    System.currentTimeMillis() + REJECT_CLOSE_DELAY_MILLIS);
        }
    }

    private void closeRejectedPeers(long now) {
        peersAwaitingClose.forEach((steamId, deadline) -> {
            if (deadline != null && deadline.longValue() <= now
                    && peersAwaitingClose.remove(steamId, deadline)) {
                backend.closePeer(steamId.longValue());
            }
        });
    }

    private void closeRemote(long steamId) {
        pendingPeers.remove(steamId);
        peersAwaitingClose.remove(steamId);
        Collection<SteamConnectionBridge> snapshot = bridges.snapshot();
        for (SteamConnectionBridge bridge : snapshot) {
            if (bridge.remoteSteamId() == steamId) bridge.close(false);
        }
        activePeers.remove(steamId);
        backend.endAuthentication(steamId);
        controller.players(activePeers.size());
    }

    @Override public void close() {
        if (!running.compareAndSet(true, false)) return;
        backend.peerListener(null);
        Thread thread = packetThread;
        packetThread = null;
        if (thread != null && thread != Thread.currentThread()) thread.interrupt();
        for (SteamConnectionBridge bridge : new ArrayList<>(bridges.snapshot())) {
            bridge.close(false);
        }
        deferredPacket = null;
        for (AutoCloseable handle : ingress.values()) close(handle);
        ingress.clear();
        activePeers.clear();
        pendingPeers.clear();
        peersAwaitingClose.clear();
        outbound.clear();
        for (Runnable abandoned : admissionExecutor.shutdownNow()) {
            if (abandoned instanceof AdmissionTask) {
                ((AdmissionTask) abandoned).discard();
            }
        }
        controller.players(0);
    }

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Exception ignored) { }
    }

    private static String safeFailure(Throwable failure) {
        return failure instanceof SecurityException ? "INGRESS_REJECTED" : "BRIDGE_REJECTED";
    }

    private static ThreadFactory daemonFactory(String base) {
        return runnable -> {
            Thread thread = new Thread(runnable, base);
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Owns credential cleanup even if the bounded executor never runs the task. */
    private final class AdmissionTask implements Runnable {
        private final long remoteSteamId;
        private final SteamBridgeRegistry.Key key;
        private final DedicatedAdmissionGate.Request request;
        private final AtomicBoolean claimed = new AtomicBoolean();

        private AdmissionTask(long remoteSteamId, SteamBridgeRegistry.Key key,
                              DedicatedAdmissionGate.Request request) {
            this.remoteSteamId = remoteSteamId;
            this.key = key;
            this.request = request;
        }

        @Override public void run() {
            if (!claimed.compareAndSet(false, true)) return;
            try {
                admission.evaluate(request, controller.generation())
                        .toCompletableFuture()
                        .orTimeout(ADMISSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .whenComplete((result, failure) -> submitAdmissionResult(
                                remoteSteamId, key, result, failure));
            } catch (VirtualMachineError | ThreadDeath fatal) {
                backend.endAuthentication(remoteSteamId);
                throw fatal;
            } catch (Throwable failure) {
                submitAdmissionResult(remoteSteamId, key, null, failure);
            } finally {
                request.close();
            }
        }

        private void discard() {
            if (claimed.compareAndSet(false, true)) request.close();
        }
    }
}
