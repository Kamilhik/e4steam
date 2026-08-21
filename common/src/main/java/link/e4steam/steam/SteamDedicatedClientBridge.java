package link.e4steam.steam;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Loopback endpoint used by an unmodified Minecraft client joining a dedicated peer. */
public final class SteamDedicatedClientBridge {
    private static final Logger LOGGER = LogManager.getLogger("e4steam");
    private static final int ACCEPT_TIMEOUT_MILLIS = 30_000;
    private static final Set<Pending> PENDING = ConcurrentHashMap.newKeySet();

    private SteamDedicatedClientBridge() {
    }

    public static InetSocketAddress open(SteamDedicatedAddress address) throws IOException {
        SteamRuntime runtime = SteamRuntime.get();
        SteamRuntime.Activity activity = runtime.acquireActivity();
        boolean handedOff = false;
        try {
            runtime.awaitReady();
            InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
            ServerSocket listener = new ServerSocket();
            listener.setReuseAddress(false);
            listener.bind(new InetSocketAddress(loopback, 0), 1);
            listener.setSoTimeout(ACCEPT_TIMEOUT_MILLIS);
            Pending pending = new Pending(listener, activity);
            PENDING.add(pending);
            Thread thread = new Thread(
                    () -> accept(runtime, address, pending),
                    "e4steam-dedicated-client-accept"
            );
            thread.setDaemon(true);
            thread.start();
            handedOff = true;
            return new InetSocketAddress(loopback, listener.getLocalPort());
        } finally {
            if (!handedOff) activity.close();
        }
    }

    public static void cancelPending() {
        for (Pending pending : PENDING.toArray(new Pending[0])) pending.close();
        PENDING.clear();
    }

    private static void accept(SteamRuntime runtime, SteamDedicatedAddress address, Pending pending) {
        Socket socket = null;
        SteamConnectionBridge bridge = null;
        SteamRuntime.DedicatedClientAuth authentication = null;
        boolean handedOff = false;
        try (ServerSocket listener = pending.listener) {
            socket = listener.accept();
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            SteamRuntime.Activity activity = pending.takeActivity();
            if (activity == null) throw new IOException("Dedicated Steam connection was cancelled");
            authentication = runtime.createDedicatedClientAuth();
            int connectionId = runtime.nextConnectionId(address.steamId());
            ClientLease lease = new ClientLease(activity, authentication);
            bridge = runtime.registerClientBridge(address.steamId(), connectionId, socket, lease);
            bridge.dedicatedSessionGeneration(address.generation());
            activity = null;
            // Hold local Minecraft bytes until the server has validated the auth proof.
            bridge.waitForPeerReadyUntil(Long.MAX_VALUE);
            if (!runtime.sendDedicatedOpen(bridge, address, authentication)) {
                throw new IOException("Steam outbound queue is unavailable");
            }
            authentication = null;
            handedOff = true;
            LOGGER.info("Opened a dedicated e4steam client bridge");
        } catch (SocketTimeoutException timeout) {
            LOGGER.debug("Timed out waiting for Minecraft to use a dedicated descriptor");
        } catch (IOException failure) {
            if (!pending.closed) LOGGER.warn("Dedicated Steam client bridge failed", failure);
        } finally {
            PENDING.remove(pending);
            pending.close();
            close(authentication);
            if (!handedOff) {
                if (bridge != null) bridge.close(false);
                else close(socket);
            }
        }
    }

    private static void close(AutoCloseable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (Exception ignored) { }
    }

    private static final class Pending implements AutoCloseable {
        private final ServerSocket listener;
        private SteamRuntime.Activity activity;
        private volatile boolean closed;

        private Pending(ServerSocket listener, SteamRuntime.Activity activity) {
            this.listener = listener;
            this.activity = activity;
        }

        synchronized SteamRuntime.Activity takeActivity() {
            if (closed) return null;
            SteamRuntime.Activity value = activity;
            activity = null;
            return value;
        }

        @Override public void close() {
            SteamRuntime.Activity value;
            synchronized (this) {
                if (closed) return;
                closed = true;
                value = activity;
                activity = null;
            }
            SteamDedicatedClientBridge.close(listener);
            SteamDedicatedClientBridge.close(value);
        }
    }

    private static final class ClientLease implements AutoCloseable {
        private final SteamRuntime.Activity activity;
        private final SteamRuntime.DedicatedClientAuth authentication;
        private final java.util.concurrent.atomic.AtomicBoolean closed =
                new java.util.concurrent.atomic.AtomicBoolean();

        private ClientLease(
                SteamRuntime.Activity activity,
                SteamRuntime.DedicatedClientAuth authentication
        ) {
            this.activity = activity;
            this.authentication = authentication;
        }

        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            authentication.close();
            activity.close();
        }
    }
}
