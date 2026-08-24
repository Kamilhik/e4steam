package link.e4steam.steam;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Server-neutral projection of the client Steam runtime used by the Addon API.
 * The headless graph reaches only this no-op boundary, never Steam client/UI code.
 */
public final class SteamClientApiBridge {
    interface Delegate {
        String statusCode();
        String failureCategory();
        SessionView sessionView();
        MinecraftIdentity localIdentity();
        PeerIdentity resolvePeer(String opaquePeerId);
        String opaquePeerId(long remoteSteamId);
        boolean disconnect(long generation);
        long authenticatedMinecraftPeer(SocketAddress remoteAddress);
        boolean sendAddonHello(SteamConnectionBridge bridge, byte[] packet);
        boolean sendAddonFrame(SteamConnectionBridge bridge, byte[] packet, boolean reliable);
    }

    private static final Delegate NOOP = new Delegate() {
        @Override public String statusCode() { return "NEW"; }
        @Override public String failureCategory() { return ""; }
        @Override public SessionView sessionView() { return SessionView.inactive(); }
        @Override public MinecraftIdentity localIdentity() { return null; }
        @Override public PeerIdentity resolvePeer(String opaquePeerId) { return null; }
        @Override public String opaquePeerId(long remoteSteamId) { return null; }
        @Override public boolean disconnect(long generation) { return false; }
        @Override public long authenticatedMinecraftPeer(SocketAddress remoteAddress) { return 0L; }
        @Override public boolean sendAddonHello(
                SteamConnectionBridge bridge, byte[] packet) { return false; }
        @Override public boolean sendAddonFrame(
                SteamConnectionBridge bridge, byte[] packet, boolean reliable) { return false; }
    };
    private static final AtomicReference<Delegate> CURRENT =
            new AtomicReference<Delegate>(NOOP);

    private SteamClientApiBridge() {
    }

    static AutoCloseable install(Delegate delegate) {
        if (delegate == null) throw new NullPointerException("delegate");
        if (!CURRENT.compareAndSet(NOOP, delegate)) {
            throw new IllegalStateException("Steam client API bridge is already installed");
        }
        return () -> CURRENT.compareAndSet(delegate, NOOP);
    }

    public static String statusCode() { return CURRENT.get().statusCode(); }
    public static String failureCategory() { return CURRENT.get().failureCategory(); }
    public static SessionView sessionView() { return CURRENT.get().sessionView(); }
    public static MinecraftIdentity localIdentity() { return CURRENT.get().localIdentity(); }
    public static PeerIdentity resolvePeer(String opaquePeerId) {
        return CURRENT.get().resolvePeer(opaquePeerId);
    }
    public static boolean disconnect(long generation) {
        return CURRENT.get().disconnect(generation);
    }
    public static long authenticatedMinecraftPeer(SocketAddress remoteAddress) {
        return CURRENT.get().authenticatedMinecraftPeer(remoteAddress);
    }
    static String opaquePeerId(long remoteSteamId) {
        return CURRENT.get().opaquePeerId(remoteSteamId);
    }
    static boolean sendAddonHello(SteamConnectionBridge bridge, byte[] packet) {
        return CURRENT.get().sendAddonHello(bridge, packet);
    }
    static boolean sendAddonFrame(
            SteamConnectionBridge bridge, byte[] packet, boolean reliable) {
        return CURRENT.get().sendAddonFrame(bridge, packet, reliable);
    }

    public static final class SessionView {
        private final String sessionId;
        private final long generation;
        private final String roleCode;
        private final String stateCode;
        private final int capacity;
        private final List<PeerIdentity> peers;

        SessionView(String sessionId, long generation, String roleCode, String stateCode,
                    int capacity, List<PeerIdentity> peers) {
            this.sessionId = sessionId == null ? "" : sessionId;
            this.generation = generation;
            this.roleCode = roleCode == null ? "NONE" : roleCode;
            this.stateCode = stateCode == null ? "NONE" : stateCode;
            this.capacity = Math.max(0, capacity);
            this.peers = Collections.unmodifiableList(new ArrayList<>(
                    peers == null ? Collections.<PeerIdentity>emptyList() : peers));
        }

        static SessionView inactive() {
            return new SessionView("", 0L, "NONE", "NONE", 0,
                    Collections.<PeerIdentity>emptyList());
        }

        public boolean active() { return generation > 0L; }
        public String sessionId() { return sessionId; }
        public long generation() { return generation; }
        public String roleCode() { return roleCode; }
        public String stateCode() { return stateCode; }
        public int capacity() { return capacity; }
        public List<PeerIdentity> peers() { return peers; }

        @Override public String toString() {
            return "SteamClientSessionView{generation=" + generation + ", role="
                    + roleCode + ", state=" + stateCode + ", peers=" + peers.size() + '}';
        }
    }

    public static final class PeerIdentity {
        private final String opaquePeerId;
        private final UUID minecraftUuid;
        private final String minecraftName;

        PeerIdentity(String opaquePeerId, UUID minecraftUuid, String minecraftName) {
            this.opaquePeerId = java.util.Objects.requireNonNull(opaquePeerId, "opaquePeerId");
            this.minecraftUuid = java.util.Objects.requireNonNull(minecraftUuid, "minecraftUuid");
            this.minecraftName = java.util.Objects.requireNonNull(minecraftName, "minecraftName");
        }

        public String opaquePeerId() { return opaquePeerId; }
        public UUID minecraftUuid() { return minecraftUuid; }
        public String minecraftName() { return minecraftName; }
        @Override public String toString() { return "SteamClientPeerIdentity{opaque}"; }
    }

    public static final class MinecraftIdentity {
        private final UUID minecraftUuid;
        private final String minecraftName;

        MinecraftIdentity(UUID minecraftUuid, String minecraftName) {
            this.minecraftUuid = java.util.Objects.requireNonNull(minecraftUuid, "minecraftUuid");
            this.minecraftName = java.util.Objects.requireNonNull(minecraftName, "minecraftName");
        }

        public UUID minecraftUuid() { return minecraftUuid; }
        public String minecraftName() { return minecraftName; }
        @Override public String toString() {
            return "SteamClientMinecraftIdentity{uuid=" + minecraftUuid + '}';
        }
    }
}
