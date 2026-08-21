package link.e4steam.steam;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * Bounded, category-aware outbound queue. Reliable Minecraft data, unreliable
 * voice datagrams, lobby handshakes, and terminal resets cannot consume one
 * another's reserved capacity.
 */
final class SteamOutboundQueue<B> {
    enum Kind {
        OPEN,
        OPEN_ACK,
        DEDICATED_OPEN,
        DEDICATED_OPEN_ACK,
        BRIDGE_READY,
        ADDON_HELLO,
        ADDON_DATA,
        ADDON_DATAGRAM,
        DATA,
        DATAGRAM,
        FIN,
        RESET
    }

    static final class Packet<B> {
        private final long remoteSteamId;
        private final int connectionId;
        private final byte[] payload;
        private final Kind kind;
        private final B bridge;

        Packet(long remoteSteamId, int connectionId, byte[] payload, Kind kind, B bridge) {
            this.remoteSteamId = remoteSteamId;
            this.connectionId = connectionId;
            this.payload = payload;
            this.kind = kind;
            this.bridge = bridge;
        }

        long remoteSteamId() { return remoteSteamId; }
        int connectionId() { return connectionId; }
        byte[] payload() { return payload; }
        Kind kind() { return kind; }
        B bridge() { return bridge; }
    }

    private final Object lock = new Object();
    private final ArrayBlockingQueue<Packet<B>> packets;
    private final Semaphore dataSlots;
    private final Semaphore datagramSlots;
    private final Semaphore addonSlots;
    private final Semaphore openSlots;
    private final Semaphore standaloneResetSlots;
    private final int terminalReserve;

    SteamOutboundQueue(
            int totalCapacity,
            int dataCapacity,
            int datagramCapacity,
            int addonCapacity,
            int openCapacity,
            int standaloneResetCapacity
    ) {
        packets = new ArrayBlockingQueue<>(totalCapacity);
        dataSlots = new Semaphore(dataCapacity);
        datagramSlots = new Semaphore(datagramCapacity);
        addonSlots = new Semaphore(addonCapacity);
        openSlots = new Semaphore(openCapacity);
        standaloneResetSlots = new Semaphore(standaloneResetCapacity);
        terminalReserve = Math.max(1, Math.min(64, totalCapacity / 16));
    }

    boolean offerData(long remoteSteamId, int connectionId, byte[] payload, B bridge) {
        return offer(new Packet<>(remoteSteamId, connectionId, payload, Kind.DATA, bridge));
    }

    boolean offerDatagram(long remoteSteamId, int connectionId, byte[] payload, B bridge) {
        return offer(new Packet<>(remoteSteamId, connectionId, payload, Kind.DATAGRAM, bridge));
    }

    boolean offerAddonData(long remoteSteamId, int connectionId, byte[] payload,
                           boolean unreliable, B bridge) {
        return offer(new Packet<>(remoteSteamId, connectionId, payload,
                unreliable ? Kind.ADDON_DATAGRAM : Kind.ADDON_DATA, bridge));
    }

    boolean offerControl(long remoteSteamId, int connectionId, byte[] payload, Kind kind, B bridge) {
        if (kind == Kind.DATA || kind == Kind.DATAGRAM
                || kind == Kind.ADDON_DATA || kind == Kind.ADDON_DATAGRAM) {
            throw new IllegalArgumentException("Control queue cannot accept " + kind);
        }
        return offer(new Packet<>(remoteSteamId, connectionId, payload, kind, bridge));
    }

    private boolean offer(Packet<B> packet) {
        synchronized (lock) {
            Semaphore category = categorySlots(packet);
            if (!isTerminal(packet.kind())
                    && packets.remainingCapacity() <= terminalReserve) {
                return false;
            }
            if (category != null && !category.tryAcquire()) {
                return false;
            }
            if (!packets.offer(packet)) {
                if (category != null) {
                    category.release();
                }
                return false;
            }
            return true;
        }
    }

    private static boolean isTerminal(Kind kind) {
        return kind == Kind.FIN || kind == Kind.RESET;
    }

    Packet<B> poll() {
        synchronized (lock) {
            Packet<B> packet = packets.poll();
            if (packet != null) {
                releaseSlot(packet);
            }
            return packet;
        }
    }

    void purge(B bridge) {
        synchronized (lock) {
            packets.removeIf(packet -> {
                if (packet.bridge() != bridge) {
                    return false;
                }
                releaseSlot(packet);
                return true;
            });
        }
    }

    void clear() {
        synchronized (lock) {
            Packet<B> packet;
            while ((packet = packets.poll()) != null) {
                releaseSlot(packet);
            }
        }
    }

    boolean isEmpty() {
        return packets.isEmpty();
    }

    private Semaphore categorySlots(Packet<B> packet) {
        switch (packet.kind()) {
            case DATA:
                return dataSlots;
            case DATAGRAM:
                return datagramSlots;
            case ADDON_DATA:
            case ADDON_DATAGRAM:
                return addonSlots;
            case OPEN:
            case OPEN_ACK:
            case DEDICATED_OPEN:
            case DEDICATED_OPEN_ACK:
            case BRIDGE_READY:
            case ADDON_HELLO:
                return openSlots;
            case RESET:
                return packet.bridge() == null ? standaloneResetSlots : null;
            case FIN:
            default:
                return null;
        }
    }

    private void releaseSlot(Packet<B> packet) {
        Semaphore category = categorySlots(packet);
        if (category != null) {
            category.release();
        }
    }
}
