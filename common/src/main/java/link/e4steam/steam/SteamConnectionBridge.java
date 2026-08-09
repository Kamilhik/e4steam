package link.e4steam.steam;

import link.e4steam.E4steamClient;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Bridges one ordinary local TCP connection to one logical Steam P2P stream. */
final class SteamConnectionBridge {
    // Eight MiB at the current 32 KiB protocol chunk size. This absorbs
    // registry/chunk bursts while remaining bounded for multiple players.
    private static final int MAX_QUEUED_INBOUND_CHUNKS = 256;
    private static final long OUTBOUND_BACKPRESSURE_TIMEOUT_MILLIS = 30_000;
    private static final long OUTBOUND_BACKPRESSURE_RETRY_MILLIS = 10;
    private static final long GRACEFUL_CLOSE_TIMEOUT_MILLIS = 2_000;

    private final SteamRuntime runtime;
    private final long remoteSteamId;
    private final int connectionId;
    private final Socket socket;
    private final SteamSession hostOwner;
    private final AtomicReference<SteamRuntime.Activity> activity;
    private final BlockingQueue<InboundFrame> inbound = new ArrayBlockingQueue<>(MAX_QUEUED_INBOUND_CHUNKS + 1);
    private final Semaphore inboundDataSlots = new Semaphore(MAX_QUEUED_INBOUND_CHUNKS);
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean outboundFinQueued = new AtomicBoolean();
    private final AtomicBoolean outboundFinSubmitted = new AtomicBoolean();
    private final AtomicBoolean inboundFinQueued = new AtomicBoolean();
    private final AtomicBoolean inboundFinished = new AtomicBoolean();
    private final AtomicBoolean outboundDataObserved = new AtomicBoolean();
    private final AtomicBoolean inboundDataObserved = new AtomicBoolean();
    private final AtomicBoolean peerReady = new AtomicBoolean();
    private final AtomicLong gracefulCloseDeadlineMillis = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong outboundNotBeforeMillis = new AtomicLong();
    private final AtomicLong outboundDataFallbackMillis = new AtomicLong();

    private volatile Thread readerThread;
    private volatile Thread writerThread;

    SteamConnectionBridge(
            SteamRuntime runtime,
            long remoteSteamId,
            int connectionId,
            Socket socket,
            SteamSession hostOwner,
            SteamRuntime.Activity activity
    ) {
        this.runtime = runtime;
        this.remoteSteamId = remoteSteamId;
        this.connectionId = connectionId;
        this.socket = socket;
        this.hostOwner = hostOwner;
        this.activity = new AtomicReference<>(activity);
    }

    long remoteSteamId() {
        return remoteSteamId;
    }

    int connectionId() {
        return connectionId;
    }

    int localPort() {
        return socket.getLocalPort();
    }

    boolean isHostSide() {
        return hostOwner != null;
    }

    boolean isHostedBy(SteamSession owner) {
        return hostOwner == owner;
    }

    boolean isClosed() {
        return closed.get();
    }

    void delayOutboundUntil(long deadlineMillis) {
        outboundNotBeforeMillis.accumulateAndGet(deadlineMillis, Math::max);
    }

    boolean isOutboundReady(long nowMillis) {
        return nowMillis >= outboundNotBeforeMillis.get();
    }

    void waitForPeerReadyUntil(long fallbackMillis) {
        outboundDataFallbackMillis.accumulateAndGet(fallbackMillis, Math::max);
    }

    void markPeerReady() {
        peerReady.set(true);
    }

    boolean isOutboundDataReady(long nowMillis) {
        return isPeerReadyOrFallbackReached(
                outboundDataFallbackMillis.get(),
                peerReady.get(),
                nowMillis
        );
    }

    static boolean isPeerReadyOrFallbackReached(
            long fallbackMillis,
            boolean peerReady,
            long nowMillis
    ) {
        return fallbackMillis == 0 || peerReady || nowMillis >= fallbackMillis;
    }

    boolean markFirstOutboundData() {
        return outboundDataObserved.compareAndSet(false, true);
    }

    boolean markFirstInboundData() {
        return inboundDataObserved.compareAndSet(false, true);
    }

    void start() {
        if (!started.compareAndSet(false, true) || closed.get()) {
            return;
        }

        writerThread = daemonThread(this::writeLoop, "e4steam-steam-local-writer");
        readerThread = daemonThread(this::readLoop, "e4steam-steam-local-reader");
        writerThread.start();
        readerThread.start();
    }

    void acceptSteamData(byte[] payload) {
        if (closed.get()) {
            return;
        }
        if (inboundFinQueued.get()) {
            closeForSlowOrInvalidPeer();
            return;
        }
        if (!inboundDataSlots.tryAcquire()) {
            closeForSlowOrInvalidPeer();
            return;
        }
        if (!inbound.offer(new InboundData(payload))) {
            inboundDataSlots.release();
            closeForSlowOrInvalidPeer();
        }
    }

    private void closeForSlowOrInvalidPeer() {
        if (!closed.get()) {
            E4steamClient.LOGGER.warn(
                    "Closing Steam bridge {}:{} because its local TCP consumer is too slow or sent data after FIN",
                    Long.toUnsignedString(remoteSteamId),
                    Integer.toUnsignedString(connectionId)
            );
            close(true);
        }
    }

    void acceptRemoteFin() {
        if (closed.get() || !inboundFinQueued.compareAndSet(false, true)) {
            return;
        }
        beginGracefulClose();
        if (!inbound.offer(InboundFin.INSTANCE)) {
            close(true);
        }
    }

    void resetFromRemote() {
        close(false);
    }

    void markFinSubmitted() {
        if (outboundFinQueued.get()) {
            outboundFinSubmitted.set(true);
            closeIfFullyFinished();
        }
    }

    void markResetSubmitted() {
        runtime.unregister(this);
    }

    /** Immediately aborts both directions. Graceful EOF is handled with FIN frames. */
    void close(boolean notifyRemote) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            runtime.closeUdpBridge(this);
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            inbound.clear();

            Thread reader = readerThread;
            if (reader != null) {
                reader.interrupt();
            }
            Thread writer = writerThread;
            if (writer != null) {
                writer.interrupt();
            }

            // Keep this exact bridge generation registered until its RESET reaches
            // Steam's send queue. That prevents a reused connection ID from being
            // affected by stale DATA/FIN/RESET frames belonging to this bridge.
            if (!notifyRemote || !runtime.sendReset(this)) {
                runtime.unregister(this);
            }
        } finally {
            releaseActivity();
        }
    }

    void releaseActivity() {
        SteamRuntime.Activity activityToClose = activity.getAndSet(null);
        if (activityToClose == null) {
            return;
        }
        try {
            activityToClose.close();
        } catch (Exception exception) {
            E4steamClient.LOGGER.warn("Could not release Steam bridge activity", exception);
        }
    }

    private void readLoop() {
        byte[] buffer = new byte[SteamProtocol.DATA_CHUNK_SIZE];
        try {
            java.io.InputStream input = socket.getInputStream();
            while (!closed.get()) {
                int read = input.read(buffer);
                if (read < 0) {
                    if (outboundFinQueued.compareAndSet(false, true)) {
                        beginGracefulClose();
                        if (!runtime.sendFin(this)) {
                            close(true);
                        }
                    }
                    return;
                }
                if (read == 0) {
                    continue;
                }

                byte[] payload = Arrays.copyOf(buffer, read);
                sendDataWithBackpressure(payload);
            }
        } catch (IOException exception) {
            if (!closed.get()) {
                E4steamClient.LOGGER.debug("Local TCP reader for a Steam bridge stopped", exception);
                close(true);
            }
        }
    }

    private void sendDataWithBackpressure(byte[] payload) throws IOException {
        long deadline = System.currentTimeMillis() + OUTBOUND_BACKPRESSURE_TIMEOUT_MILLIS;
        while (!closed.get()) {
            if (runtime.sendData(this, payload)) {
                return;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new IOException("Steam outbound queue remained full for 30 seconds");
            }
            try {
                Thread.sleep(OUTBOUND_BACKPRESSURE_RETRY_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for Steam outbound capacity", exception);
            }
        }
        throw new IOException("Steam bridge closed while waiting for outbound capacity");
    }

    private void writeLoop() {
        try {
            java.io.OutputStream output = socket.getOutputStream();
            while (!closed.get()) {
                InboundFrame frame = inbound.take();
                if (frame instanceof InboundData) {
                    InboundData data = (InboundData) frame;
                    inboundDataSlots.release();
                    output.write(data.payload());
                    output.flush();
                    continue;
                }

                output.flush();
                socket.shutdownOutput();
                inboundFinished.set(true);
                closeIfFullyFinished();
                return;
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            if (!closed.get()) {
                E4steamClient.LOGGER.debug("Local TCP writer for a Steam bridge stopped", exception);
                close(true);
            }
        }
    }

    private void closeIfFullyFinished() {
        if (outboundFinSubmitted.get() && inboundFinished.get()) {
            close(false);
        }
    }

    void closeIfGracefulCloseTimedOut(long nowMillis) {
        long deadline = gracefulCloseDeadlineMillis.get();
        if (!closed.get() && isGracefulCloseExpired(deadline, nowMillis)) {
            E4steamClient.LOGGER.debug(
                    "Forcing cleanup of stale Steam bridge {}:{} after graceful FIN timeout",
                    Long.toUnsignedString(remoteSteamId),
                    Integer.toUnsignedString(connectionId)
            );
            close(true);
        }
    }

    private void beginGracefulClose() {
        long deadline = System.currentTimeMillis() + GRACEFUL_CLOSE_TIMEOUT_MILLIS;
        gracefulCloseDeadlineMillis.accumulateAndGet(deadline, Math::min);
    }

    static boolean isGracefulCloseExpired(long deadlineMillis, long nowMillis) {
        return deadlineMillis != Long.MAX_VALUE && nowMillis >= deadlineMillis;
    }

    private Thread daemonThread(Runnable action, String role) {
        Thread thread = new Thread(
                action,
                role + "-" + Long.toUnsignedString(remoteSteamId) + "-" + Integer.toUnsignedString(connectionId)
        );
        thread.setDaemon(true);
        return thread;
    }

    private interface InboundFrame {
    }

    private static final class InboundData implements InboundFrame {
        private final byte[] payload;

        private InboundData(byte[] payload) {
            this.payload = payload;
        }

        private byte[] payload() { return payload; }
    }

    private enum InboundFin implements InboundFrame {
        INSTANCE
    }
}
