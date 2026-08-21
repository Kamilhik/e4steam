package link.e4steam.steam;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serializes the process-global Steam client and GameServer contexts. */
final class SteamProcessGuard {
    enum Context { CLIENT, GAME_SERVER }

    private static final Object LOCK = new Object();
    private static Context active;
    private static long generation;
    private static int leases;

    private SteamProcessGuard() {
    }

    static Lease acquire(Context requested) throws IOException {
        if (requested == null) throw new NullPointerException("requested");
        synchronized (LOCK) {
            if (active != null && active != requested) {
                throw new IOException("Another Steam runtime context is already active");
            }
            if (active == null) {
                active = requested;
                generation++;
            }
            leases++;
            return new Lease(requested, generation);
        }
    }

    static Context activeContext() {
        synchronized (LOCK) {
            return active;
        }
    }

    static final class Lease implements AutoCloseable {
        private final Context context;
        private final long generation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(Context context, long generation) {
            this.context = context;
            this.generation = generation;
        }

        long generation() {
            return generation;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            synchronized (LOCK) {
                if (active == context && leases > 0) {
                    leases--;
                    if (leases == 0) active = null;
                }
            }
        }
    }
}
