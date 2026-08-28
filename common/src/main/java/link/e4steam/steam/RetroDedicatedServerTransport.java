package link.e4steam.steam;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java 8 dedicated bridge for retro loaders. It deliberately has no client,
 * overlay, friends-list or addon dependency and accepts Minecraft ingress only
 * after a Steam GameServer auth proof succeeds.
 */
public final class RetroDedicatedServerTransport
        implements SteamBridgeRuntime, AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger("e4steam");
    private static final int CHANNEL = 480;
    private static final int MAX_PACKETS_PER_TICK = 64;
    private static final int LOOPBACK_CONNECT_TIMEOUT_MILLIS = 1_000;
    private static final long AUTH_TIMEOUT_SECONDS = 16L;
    private static final long PENDING_TTL_MILLIS = 20_000L;
    private static final long PROOF_TTL_MILLIS = 120_000L;
    private static final long REJECT_CLOSE_DELAY_MILLIS = 100L;

    private final SteamGameServerRuntimeBackend backend;
    private final Host host;
    private final int capacity;
    private final SteamBridgeRegistry<SteamConnectionBridge, AutoCloseable> bridges;
    private final SteamOutboundQueue<SteamConnectionBridge> outbound =
            new SteamOutboundQueue<SteamConnectionBridge>(2048, 1536, 0, 0, 256, 64);
    private final ConcurrentHashMap<Long, Long> pending =
            new ConcurrentHashMap<Long, Long>();
    private final ConcurrentHashMap<Long, Long> delayedClose =
            new ConcurrentHashMap<Long, Long>();
    private final ConcurrentHashMap<ProofKey, Long> proofs =
            new ConcurrentHashMap<ProofKey, Long>();
    private final Set<Long> active = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<SteamConnectionBridge, AutoCloseable> ingress =
            new ConcurrentHashMap<SteamConnectionBridge, AutoCloseable>();
    private final ThreadPoolExecutor admissions;
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile Thread packetThread;
    private volatile SteamOutboundQueue.Packet<SteamConnectionBridge> deferred;

    public RetroDedicatedServerTransport(
            SteamGameServerRuntimeBackend backend,
            Host host,
            int capacity
    ) {
        if (backend == null || host == null || capacity < 1 || capacity > 64) {
            throw new IllegalArgumentException("Invalid retro dedicated transport");
        }
        this.backend = backend;
        this.host = host;
        this.capacity = capacity;
        this.bridges = new SteamBridgeRegistry<SteamConnectionBridge, AutoCloseable>(capacity);
        this.admissions = new ThreadPoolExecutor(
                1, Math.min(4, capacity), 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(Math.max(16, capacity * 2)),
                daemonFactory("e4steam-retro-dedicated-admission"),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        backend.peerListener(new SteamGameServerRuntimeBackend.PeerListener() {
            @Override public boolean onSessionRequest(long remoteSteamId) {
                long now = System.currentTimeMillis();
                expire(now);
                if (remoteSteamId == 0L || !host.accepting()
                        || active.contains(Long.valueOf(remoteSteamId))
                        || delayedClose.containsKey(Long.valueOf(remoteSteamId))) return false;
                Long previous = pending.get(Long.valueOf(remoteSteamId));
                if (previous != null && previous.longValue() > now) return true;
                if (pending.size() >= capacity * 2) return false;
                pending.put(Long.valueOf(remoteSteamId), Long.valueOf(now + PENDING_TTL_MILLIS));
                return true;
            }

            @Override public void onSessionFailed(long remoteSteamId, int reason, String detail) {
                closeRemote(remoteSteamId);
            }
        });
        Thread worker = new Thread(new Runnable() {
            @Override public void run() { runPackets(); }
        }, "e4steam-retro-dedicated-packets");
        worker.setDaemon(true);
        packetThread = worker;
        worker.start();
    }

    private void runPackets() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(
                SteamProtocol.MAX_ACCEPTED_STEAM_PACKET_SIZE);
        while (running.get()) {
            try {
                receive(buffer);
                long now = System.currentTimeMillis();
                expire(now);
                closeRejected(now);
                drainOutbound();
                Thread.sleep(2L);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException failure) {
                if (running.get()) LOGGER.warn("Retro dedicated Steam packet loop stopped");
                host.transportFailed("DEDICATED_TRANSPORT_FAILED");
                return;
            }
        }
    }

    private void receive(ByteBuffer buffer) throws IOException {
        for (int count = 0; count < MAX_PACKETS_PER_TICK; count++) {
            int size = backend.availablePacketSize(CHANNEL);
            if (size == 0) return;
            if (size < 0 || size > SteamProtocol.MAX_ACCEPTED_STEAM_PACKET_SIZE) {
                throw new IOException("Invalid dedicated Steam packet size");
            }
            buffer.clear();
            SteamGameServerRuntimeBackend.ReceivedPacket packet =
                    backend.receive(buffer, CHANNEL);
            if (packet.size() <= 0 || packet.size() > SteamProtocol.MAX_PACKET_SIZE
                    || packet.remoteSteamId() == 0L) continue;
            buffer.flip();
            buffer.limit(packet.size());
            SteamProtocol.Frame frame = SteamProtocol.decode(buffer);
            if (frame != null) dispatch(packet.remoteSteamId(), frame);
        }
    }

    private void dispatch(long remoteSteamId, SteamProtocol.Frame frame) {
        SteamBridgeRegistry.Key key = new SteamBridgeRegistry.Key(
                remoteSteamId, frame.connectionId());
        switch (frame.type()) {
            case SteamProtocol.DEDICATED_OPEN:
                handleOpen(remoteSteamId, key, frame.payload());
                return;
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
                // Retro dedicated mode does not negotiate addon channels.
        }
    }

    private void handleOpen(
            final long remoteSteamId,
            final SteamBridgeRegistry.Key key,
            byte[] payload
    ) {
        Long deadline = pending.get(Long.valueOf(remoteSteamId));
        if (!running.get() || !host.accepting() || deadline == null
                || deadline.longValue() <= System.currentTimeMillis()
                || bridges.contains(key) || active.contains(Long.valueOf(remoteSteamId))) {
            reject(remoteSteamId, key.connectionId());
            return;
        }
        SteamProtocol.DedicatedOpen decoded = SteamProtocol.decodeDedicatedOpen(payload);
        if (decoded == null || decoded.generation() != host.generation()) {
            if (decoded != null) decoded.destroy();
            reject(remoteSteamId, key.connectionId());
            return;
        }
        final long generation = decoded.generation();
        final byte[] ticket = decoded.takeTicket();
        byte[] nonce = decoded.nonce();
        decoded.destroy();
        final ProofKey proof = new ProofKey(remoteSteamId, generation, nonce);
        Arrays.fill(nonce, (byte) 0);
        long proofDeadline = System.currentTimeMillis() + PROOF_TTL_MILLIS;
        if (proofs.size() >= capacity * 8
                || proofs.putIfAbsent(proof, Long.valueOf(proofDeadline)) != null) {
            Arrays.fill(ticket, (byte) 0);
            reject(remoteSteamId, key.connectionId());
            return;
        }
        try {
            admissions.execute(new Runnable() {
                @Override public void run() {
                    authenticateAndOpen(remoteSteamId, key, generation, ticket);
                }
            });
        } catch (RuntimeException full) {
            Arrays.fill(ticket, (byte) 0);
            reject(remoteSteamId, key.connectionId());
        }
    }

    private void authenticateAndOpen(
            long remoteSteamId,
            SteamBridgeRegistry.Key key,
            long generation,
            byte[] ticket
    ) {
        boolean authenticated = false;
        try {
            authenticated = Boolean.TRUE.equals(backend.authenticate(
                    remoteSteamId, ticket, generation
            ).toCompletableFuture().get(AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ignored) {
        } catch (TimeoutException ignored) {
        } finally {
            Arrays.fill(ticket, (byte) 0);
        }
        pending.remove(Long.valueOf(remoteSteamId));
        if (!authenticated || !running.get() || !host.accepting()
                || generation != host.generation() || !host.allows(remoteSteamId)
                || active.size() >= capacity) {
            backend.endAuthentication(remoteSteamId);
            reject(remoteSteamId, key.connectionId());
            return;
        }
        openMinecraftBridge(remoteSteamId, key, generation);
    }

    private void openMinecraftBridge(
            long remoteSteamId,
            SteamBridgeRegistry.Key key,
            long generation
    ) {
        Socket socket = new Socket();
        SteamConnectionBridge bridge = null;
        AutoCloseable ingressLease = null;
        boolean handedOff = false;
        try {
            if (!active.add(Long.valueOf(remoteSteamId))) {
                throw new IOException("Dedicated peer is already active");
            }
            InetAddress address = host.minecraftAddress();
            socket.connect(new InetSocketAddress(address, host.minecraftPort()),
                    LOOPBACK_CONNECT_TIMEOUT_MILLIS);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            ingressLease = host.registerIngress(
                    socket.getLocalPort(), remoteSteamId, generation);
            bridge = new SteamConnectionBridge(
                    this, remoteSteamId, key.connectionId(), socket, true, null);
            SteamBridgeRegistry.Registration registration = bridges.register(
                    key, bridge, new java.util.function.BooleanSupplier() {
                        @Override public boolean getAsBoolean() {
                            return running.get() && host.accepting();
                        }
                    });
            if (registration != SteamBridgeRegistry.Registration.REGISTERED) {
                throw new IOException("Dedicated bridge registration rejected");
            }
            ingress.put(bridge, ingressLease);
            ingressLease = null;
            if (!outbound.offerControl(remoteSteamId, key.connectionId(),
                    SteamProtocol.encodeDedicatedOpenAck(key.connectionId(), generation),
                    SteamOutboundQueue.Kind.DEDICATED_OPEN_ACK, bridge)) {
                throw new IOException("Dedicated acknowledgement queue is full");
            }
            bridge.start();
            handedOff = true;
            host.players(active.size());
            LOGGER.info("Accepted an authenticated retro dedicated e4steam peer");
        } catch (IOException | RuntimeException failure) {
            active.remove(Long.valueOf(remoteSteamId));
            backend.endAuthentication(remoteSteamId);
            reject(remoteSteamId, key.connectionId());
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
            SteamOutboundQueue.Packet<SteamConnectionBridge> packet = deferred;
            if (packet != null) deferred = null;
            else packet = outbound.poll();
            if (packet == null) return;
            SteamConnectionBridge bridge = packet.bridge();
            if (bridge != null && packet.kind() != SteamOutboundQueue.Kind.RESET
                    && bridge.isClosed()) continue;
            long now = System.currentTimeMillis();
            if (bridge != null && packet.kind() != SteamOutboundQueue.Kind.RESET
                    && !bridge.isOutboundReady(now)) {
                deferred = packet;
                return;
            }
            if (bridge != null && packet.kind() == SteamOutboundQueue.Kind.DATA
                    && !bridge.isOutboundDataReady(now)) {
                deferred = packet;
                return;
            }
            ByteBuffer direct = ByteBuffer.allocateDirect(packet.payload().length);
            direct.put(packet.payload()).flip();
            if (!backend.send(packet.remoteSteamId(), direct, false, CHANNEL)) {
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
        return running.get() && outbound.offerControl(
                bridge.remoteSteamId(), bridge.connectionId(),
                SteamProtocol.encodeFin(bridge.connectionId()),
                SteamOutboundQueue.Kind.FIN, bridge);
    }

    @Override public boolean sendReset(SteamConnectionBridge bridge) {
        return running.get() && outbound.offerControl(
                bridge.remoteSteamId(), bridge.connectionId(),
                SteamProtocol.encodeReset(bridge.connectionId()),
                SteamOutboundQueue.Kind.RESET, bridge);
    }

    @Override public void closeUdpBridge(SteamConnectionBridge bridge) {
        // Dedicated retro mode forwards the Minecraft TCP stream only.
    }

    @Override public void unregister(SteamConnectionBridge bridge) {
        SteamBridgeRegistry.Key key = new SteamBridgeRegistry.Key(
                bridge.remoteSteamId(), bridge.connectionId());
        outbound.purge(bridge);
        if (deferred != null && deferred.bridge() == bridge) deferred = null;
        if (!bridges.remove(key, bridge)) return;
        close(ingress.remove(bridge));
        active.remove(Long.valueOf(bridge.remoteSteamId()));
        backend.endAuthentication(bridge.remoteSteamId());
        backend.closePeer(bridge.remoteSteamId());
        host.players(active.size());
    }

    private void reject(long steamId, int connectionId) {
        pending.remove(Long.valueOf(steamId));
        backend.endAuthentication(steamId);
        if (running.get()) {
            outbound.offerControl(steamId, connectionId,
                    SteamProtocol.encodeReset(connectionId),
                    SteamOutboundQueue.Kind.RESET, null);
            delayedClose.put(Long.valueOf(steamId),
                    Long.valueOf(System.currentTimeMillis() + REJECT_CLOSE_DELAY_MILLIS));
        }
    }

    private void expire(long now) {
        for (java.util.Map.Entry<Long, Long> entry : pending.entrySet()) {
            if (entry.getValue().longValue() <= now
                    && pending.remove(entry.getKey(), entry.getValue())) {
                backend.closePeer(entry.getKey().longValue());
            }
        }
        for (java.util.Map.Entry<ProofKey, Long> entry : proofs.entrySet()) {
            if (entry.getValue().longValue() <= now) {
                proofs.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private void closeRejected(long now) {
        for (java.util.Map.Entry<Long, Long> entry : delayedClose.entrySet()) {
            if (entry.getValue().longValue() <= now
                    && delayedClose.remove(entry.getKey(), entry.getValue())) {
                backend.closePeer(entry.getKey().longValue());
            }
        }
    }

    private void closeRemote(long steamId) {
        pending.remove(Long.valueOf(steamId));
        delayedClose.remove(Long.valueOf(steamId));
        Collection<SteamConnectionBridge> snapshot = bridges.snapshot();
        for (SteamConnectionBridge bridge : snapshot) {
            if (bridge.remoteSteamId() == steamId) bridge.close(false);
        }
        active.remove(Long.valueOf(steamId));
        backend.endAuthentication(steamId);
        host.players(active.size());
    }

    @Override public void close() {
        if (!running.compareAndSet(true, false)) return;
        backend.peerListener(null);
        Thread worker = packetThread;
        packetThread = null;
        if (worker != null && worker != Thread.currentThread()) worker.interrupt();
        for (SteamConnectionBridge bridge : new ArrayList<SteamConnectionBridge>(
                bridges.snapshot())) bridge.close(false);
        deferred = null;
        for (AutoCloseable lease : ingress.values()) close(lease);
        ingress.clear();
        active.clear();
        pending.clear();
        delayedClose.clear();
        proofs.clear();
        outbound.clear();
        admissions.shutdownNow();
        host.players(0);
    }

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Exception ignored) { }
    }

    private static ThreadFactory daemonFactory(final String name) {
        return new ThreadFactory() {
            @Override public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, name);
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    public interface Host {
        boolean accepting();
        long generation();
        int minecraftPort();
        InetAddress minecraftAddress();
        int maxPeers();
        boolean allows(long steamId);
        AutoCloseable registerIngress(int localPort, long steamId, long generation);
        void transportFailed(String category);
        void players(int count);
    }

    private static final class ProofKey {
        private final long steamId;
        private final long generation;
        private final byte[] nonce;
        private final int hash;

        private ProofKey(long steamId, long generation, byte[] nonce) {
            this.steamId = steamId;
            this.generation = generation;
            this.nonce = nonce.clone();
            int result = Long.valueOf(steamId).hashCode();
            result = 31 * result + Long.valueOf(generation).hashCode();
            this.hash = 31 * result + Arrays.hashCode(this.nonce);
        }

        @Override public int hashCode() { return hash; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ProofKey)) return false;
            ProofKey that = (ProofKey) other;
            return steamId == that.steamId && generation == that.generation
                    && Arrays.equals(nonce, that.nonce);
        }
    }
}
