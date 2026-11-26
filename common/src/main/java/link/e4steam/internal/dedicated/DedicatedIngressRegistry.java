package link.e4steam.internal.dedicated;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/** Connection-lifetime loopback bindings created only after dedicated Steam admission. */
final class DedicatedIngressRegistry {
    private final ConcurrentHashMap<Integer, Entry> entries = new ConcurrentHashMap<>();

    AutoCloseable register(int localPort, long steamId, long generation) {
        if (localPort < 1 || localPort > 65535 || steamId == 0L || generation <= 0L) {
            throw new IllegalArgumentException("invalid ingress binding");
        }
        Entry entry = new Entry(steamId, generation);
        if (entries.putIfAbsent(localPort, entry) != null) {
            throw new IllegalStateException("loopback ingress port is already authenticated");
        }
        return () -> entries.remove(localPort, entry);
    }

    long resolve(SocketAddress remoteAddress, long activeGeneration) {
        if (!(remoteAddress instanceof InetSocketAddress)) return 0L;
        InetSocketAddress address = (InetSocketAddress) remoteAddress;
        if (address.isUnresolved() || address.getAddress() == null
                || !address.getAddress().isLoopbackAddress()) return 0L;
        // Login policy, session-mode checks and Forge's login state machine all
        // inspect the same admitted socket. Reading its identity must not revoke
        // it. The transport's lease removes this exact entry when the bridge
        // closes, preventing a later socket from inheriting the authenticated ID.
        Entry entry = entries.get(address.getPort());
        if (entry == null) return 0L;
        if (entry.generation != activeGeneration) {
            entries.remove(address.getPort(), entry);
            return 0L;
        }
        return entry.steamId;
    }

    void clear() {
        entries.clear();
    }

    private static final class Entry {
        private final long steamId;
        private final long generation;
        private Entry(long steamId, long generation) {
            this.steamId = steamId;
            this.generation = generation;
        }
    }
}
