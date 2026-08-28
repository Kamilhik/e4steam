package link.e4steam.steam;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class RetroDedicatedServerTransportTest {
    private final List<AutoCloseable> closeables = new ArrayList<AutoCloseable>();

    @After public void closeResources() {
        for (int index = closeables.size() - 1; index >= 0; index--) {
            try { closeables.get(index).close(); } catch (Exception ignored) { }
        }
        closeables.clear();
    }

    @Test public void authenticatedOpenCreatesOneGenerationBoundLoopbackBridge()
            throws Exception {
        ServerSocket minecraft = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        closeables.add(minecraft);
        ExecutorService acceptor = Executors.newSingleThreadExecutor();
        closeables.add(new AutoCloseable() {
            @Override public void close() { acceptor.shutdownNow(); }
        });
        Future<Socket> accepted = acceptor.submit(() -> minecraft.accept());

        FakeBackend backend = new FakeBackend();
        FakeHost host = new FakeHost(minecraft.getLocalPort(), 73L);
        RetroDedicatedServerTransport transport =
                new RetroDedicatedServerTransport(backend, host, 2);
        closeables.add(transport);
        transport.start();

        long steamId = 76561198000000001L;
        assertTrue(backend.request(steamId));
        backend.enqueue(steamId, SteamProtocol.encodeDedicatedOpen(
                41, host.generation(), nonce(1), new byte[] { 4, 5, 6 }
        ));

        assertTrue(await(new Condition() {
            @Override public boolean value() {
                return host.players.get() == 1
                        && host.ingressSteamId == steamId
                        && backend.countFrames(SteamProtocol.DEDICATED_OPEN_ACK) == 1;
            }
        }, 3_000L));
        Socket bridge = accepted.get(2, TimeUnit.SECONDS);
        closeables.add(bridge);
        assertTrue(bridge.getInetAddress().isLoopbackAddress());
        assertEquals(host.generation(), host.ingressGeneration);
        assertTrue(host.ingressPort > 0);
        assertEquals(1, backend.authentications.get());

        transport.close();
        assertTrue(await(new Condition() {
            @Override public boolean value() {
                return host.players.get() == 0 && host.closedIngress.get() == 1;
            }
        }, 2_000L));
    }

    @Test public void replayedProofIsRejectedBeforeASecondSteamAuthentication()
            throws Exception {
        FakeBackend backend = new FakeBackend();
        backend.authenticationResult = false;
        FakeHost host = new FakeHost(1, 91L);
        RetroDedicatedServerTransport transport =
                new RetroDedicatedServerTransport(backend, host, 1);
        closeables.add(transport);
        transport.start();

        final long steamId = 76561198000000002L;
        final byte[] open = SteamProtocol.encodeDedicatedOpen(
                9, host.generation(), nonce(7), new byte[] { 8, 9 }
        );
        assertTrue(backend.request(steamId));
        backend.enqueue(steamId, open);
        assertTrue(await(new Condition() {
            @Override public boolean value() {
                return backend.authentications.get() == 1
                        && backend.countFrames(SteamProtocol.RESET) >= 1;
            }
        }, 2_000L));

        assertTrue(await(new Condition() {
            @Override public boolean value() { return backend.request(steamId); }
        }, 2_000L));
        backend.enqueue(steamId, open);
        assertTrue(await(new Condition() {
            @Override public boolean value() {
                return backend.countFrames(SteamProtocol.RESET) >= 2;
            }
        }, 2_000L));
        assertEquals(1, backend.authentications.get());
        assertEquals(0, host.players.get());
    }

    private static byte[] nonce(int seed) {
        byte[] value = new byte[16];
        Arrays.fill(value, (byte) seed);
        return value;
    }

    private static boolean await(Condition condition, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        do {
            if (condition.value()) return true;
            Thread.sleep(10L);
        } while (System.currentTimeMillis() < deadline);
        return condition.value();
    }

    private interface Condition {
        boolean value();
    }

    private static final class FakeHost implements RetroDedicatedServerTransport.Host {
        private final int port;
        private final long generation;
        private final AtomicInteger players = new AtomicInteger();
        private final AtomicInteger closedIngress = new AtomicInteger();
        private volatile long ingressSteamId;
        private volatile long ingressGeneration;
        private volatile int ingressPort;

        private FakeHost(int port, long generation) {
            this.port = port;
            this.generation = generation;
        }

        @Override public boolean accepting() { return true; }
        @Override public long generation() { return generation; }
        @Override public int minecraftPort() { return port; }
        @Override public InetAddress minecraftAddress() { return InetAddress.getLoopbackAddress(); }
        @Override public int maxPeers() { return 2; }
        @Override public boolean allows(long steamId) { return true; }

        @Override public AutoCloseable registerIngress(
                int localPort,
                long steamId,
                long expectedGeneration
        ) {
            ingressPort = localPort;
            ingressSteamId = steamId;
            ingressGeneration = expectedGeneration;
            return new AutoCloseable() {
                @Override public void close() { closedIngress.incrementAndGet(); }
            };
        }

        @Override public void transportFailed(String category) {
            throw new AssertionError(category);
        }

        @Override public void players(int count) { players.set(count); }
    }

    private static final class FakeBackend
            implements RetroDedicatedServerTransport.Backend {
        private final ConcurrentLinkedQueue<Inbound> inbound =
                new ConcurrentLinkedQueue<Inbound>();
        private final ConcurrentLinkedQueue<byte[]> sent =
                new ConcurrentLinkedQueue<byte[]>();
        private final AtomicInteger authentications = new AtomicInteger();
        private volatile PeerListener listener;
        private volatile boolean authenticationResult = true;

        private boolean request(long steamId) {
            PeerListener current = listener;
            return current != null && current.onSessionRequest(steamId);
        }

        private void enqueue(long steamId, byte[] packet) {
            inbound.add(new Inbound(steamId, packet.clone()));
        }

        private int countFrames(byte type) {
            int count = 0;
            for (byte[] packet : sent) {
                SteamProtocol.Frame frame = SteamProtocol.decode(ByteBuffer.wrap(packet));
                assertNotNull(frame);
                if (frame.type() == type) count++;
            }
            return count;
        }

        @Override public void peerListener(PeerListener replacement) {
            listener = replacement;
        }

        @Override public CompletionStage<Boolean> authenticate(
                long steamId,
                byte[] ticket,
                long generation
        ) {
            authentications.incrementAndGet();
            return CompletableFuture.completedFuture(Boolean.valueOf(authenticationResult));
        }

        @Override public void endAuthentication(long steamId) { }

        @Override public boolean send(
                long steamId,
                ByteBuffer payload,
                boolean unreliable,
                int channel
        ) {
            byte[] copy = new byte[payload.remaining()];
            payload.get(copy);
            sent.add(copy);
            return true;
        }

        @Override public int availablePacketSize(int channel) {
            Inbound next = inbound.peek();
            return next == null ? 0 : next.packet.length;
        }

        @Override public ReceivedPacket receive(ByteBuffer target, int channel)
                throws IOException {
            Inbound next = inbound.poll();
            if (next == null) throw new IOException("No queued packet");
            target.put(next.packet);
            return new ReceivedPacket(next.steamId, next.packet.length);
        }

        @Override public void closePeer(long steamId) { }
    }

    private static final class Inbound {
        private final long steamId;
        private final byte[] packet;

        private Inbound(long steamId, byte[] packet) {
            this.steamId = steamId;
            this.packet = packet;
        }
    }
}
