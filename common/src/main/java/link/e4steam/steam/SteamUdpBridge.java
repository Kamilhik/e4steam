package link.e4steam.steam;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Bridges local UDP datagrams to the Steam peer associated with a Minecraft TCP bridge. */
final class SteamUdpBridge {
    private static final int MAX_UDP_PACKET_SIZE = 65_507;

    private static final Logger LOGGER=LogManager.getLogger("e4steam");
    private final SteamBridgeRuntime runtime;
    private final SteamConnectionBridge owner;
    private final DatagramSocket socket;
    private final boolean hostSide;
    private final AtomicReference<SocketAddress> localClient = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile Thread readerThread;

    private SteamUdpBridge(
            SteamBridgeRuntime runtime,
            SteamConnectionBridge owner,
            DatagramSocket socket,
            boolean hostSide
    ) {
        this.runtime = runtime;
        this.owner = owner;
        this.socket = socket;
        this.hostSide = hostSide;
    }

    static SteamUdpBridge client(SteamBridgeRuntime runtime, SteamConnectionBridge owner, int port) throws IOException {
        return client(runtime,owner,port,false);
    }

    static SteamUdpBridge client(SteamBridgeRuntime runtime, SteamConnectionBridge owner, int port, boolean allowRandomPort) throws IOException {
        DatagramSocket socket = new DatagramSocket(null);
        boolean ready = false;
        try {
            socket.setReuseAddress(false);
            try { socket.bind(new InetSocketAddress(loopback(), port)); }
            catch (java.net.BindException busy) {
                socket.close();
                if (!allowRandomPort || port == 0) throw busy;
                socket = new DatagramSocket(new InetSocketAddress(loopback(), 0));
            }
            SteamUdpBridge bridge = new SteamUdpBridge(runtime, owner, socket, false);
            ready = true;
            return bridge;
        } finally {
            if (!ready) {
                socket.close();
            }
        }
    }

    static SteamUdpBridge host(SteamBridgeRuntime runtime, SteamConnectionBridge owner, int port) throws IOException {
        DatagramSocket socket = new DatagramSocket(null);
        boolean ready = false;
        try {
            socket.bind(new InetSocketAddress(loopback(), 0));
            socket.connect(new InetSocketAddress(loopback(), port));
            SteamUdpBridge bridge = new SteamUdpBridge(runtime, owner, socket, true);
            ready = true;
            return bridge;
        } finally {
            if (!ready) {
                socket.close();
            }
        }
    }

    SteamConnectionBridge owner() {
        return owner;
    }

    boolean isClosed() {
        return closed.get();
    }

    int localPort() {
        return socket.getLocalPort();
    }

    boolean readerAlive() { Thread thread=readerThread; return thread!=null && thread.isAlive(); }

    boolean hasLocalClient() {
        return localClient.get() != null;
    }

    void start() {
        if (closed.get() || readerThread != null) {
            return;
        }
        Thread thread = new Thread(
                this::readLoop,
                "e4steam-steam-udp-" + Long.toUnsignedString(owner.remoteSteamId())
                        + "-" + Integer.toUnsignedString(owner.connectionId())
        );
        thread.setDaemon(true);
        readerThread = thread;
        thread.start();
    }

    void acceptSteamDatagram(byte[] payload) {
        if (closed.get()) {
            return;
        }
        try {
            DatagramPacket packet;
            if (hostSide) {
                packet = new DatagramPacket(payload, payload.length);
            } else {
                SocketAddress destination = localClient.get();
                if (destination == null) {
                    return;
                }
                packet = new DatagramPacket(payload, payload.length, destination);
            }
            socket.send(packet);
        } catch (IOException exception) {
            if (!closed.get()) {
                LOGGER.debug("Could not deliver a tunneled UDP datagram", exception);
            }
        }
    }

    void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        socket.close();
        Thread thread = readerThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void readLoop() {
        byte[] buffer = new byte[MAX_UDP_PACKET_SIZE];
        while (!closed.get()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                if (!hostSide) {
                    InetAddress source = packet.getAddress();
                    if (source == null || !source.isLoopbackAddress()) {
                        continue;
                    }
                    localClient.set(packet.getSocketAddress());
                }
                if (packet.getLength() > SteamProtocol.MAX_DATAGRAM_SIZE) {
                    LOGGER.debug(
                            "Dropping oversized UDP datagram ({} bytes; maximum {})",
                            packet.getLength(),
                            SteamProtocol.MAX_DATAGRAM_SIZE
                    );
                    continue;
                }
                byte[] payload = Arrays.copyOfRange(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getOffset() + packet.getLength()
                );
                runtime.sendDatagram(this, payload);
            } catch (java.net.PortUnreachableException notReadyYet) {
                // Voice servers start/rebind asynchronously. An ICMP response is not a closed tunnel.
                if (closed.get()) return;
            } catch (SocketException exception) {
                if (!closed.get()) {
                    LOGGER.debug("UDP tunnel socket stopped", exception);
                }
                return;
            } catch (IOException exception) {
                if (!closed.get()) {
                    LOGGER.debug("UDP tunnel reader stopped", exception);
                }
                return;
            }
        }
    }

    private static InetAddress loopback() throws IOException {
        return InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
    }
}
