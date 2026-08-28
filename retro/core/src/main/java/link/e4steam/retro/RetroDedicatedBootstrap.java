package link.e4steam.retro;

import link.e4steam.Agnos;
import link.e4steam.E4steamClient;
import link.e4steam.MinecraftVersion;
import link.e4steam.steam.RetroDedicatedServerTransport;
import link.e4steam.steam.SteamDedicatedAddress;
import link.e4steam.steam.SteamGameServerRuntimeBackend;
import link.e4steam.steam.SteamRuntimeBackend;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Headless Java 8 lifecycle shared by every released retro loader adapter. */
public final class RetroDedicatedBootstrap
        implements RetroDedicatedServerTransport.Host, AutoCloseable {
    private static final Object INSTALL_LOCK = new Object();
    private static volatile RetroDedicatedBootstrap current;

    private final RetroDedicatedConfig config;
    private final SteamGameServerRuntimeBackend backend;
    private final ConcurrentHashMap<Integer, Ingress> ingress =
            new ConcurrentHashMap<Integer, Ingress>();
    private final AtomicBoolean backendStarted = new AtomicBoolean();
    private final AtomicBoolean minecraftReady = new AtomicBoolean();
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile InetAddress minecraftAddress;
    private volatile int minecraftPort;
    private volatile long generation;
    private volatile long serverSteamId;
    private volatile String failureCategory = "";
    private volatile RetroDedicatedServerTransport transport;

    private RetroDedicatedBootstrap(RetroDedicatedConfig config) {
        this.config = config;
        this.backend = new SteamGameServerRuntimeBackend(new SteamRuntimeBackend.StateListener() {
            @Override public void onState(SteamRuntimeBackend.State state, String category) {
                if (category != null && !category.isEmpty()) failureCategory = category;
                E4steamClient.LOGGER.info("e4steam retro dedicated state: " + state.name());
            }
        });
    }

    public static RetroDedicatedBootstrap install(String minecraftVersion) {
        synchronized (INSTALL_LOCK) {
            if (current != null) return current;
            Agnos.installPhysicalSide(false);
            MinecraftVersion.install(minecraftVersion);
            RetroDedicatedConfig config;
            try {
                config = RetroDedicatedConfig.load();
                if (config.enabled()) RetroDedicatedConfig.validateServerProperties();
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Invalid e4steam dedicated configuration", failure);
            }
            RetroDedicatedBootstrap installed = new RetroDedicatedBootstrap(config);
            current = installed;
            if (config.enabled()) {
                E4steamClient.LOGGER.info(
                        "e4steam retro dedicated backend enabled (Steam-only ingress)");
            } else {
                E4steamClient.LOGGER.info("e4steam retro dedicated backend is disabled");
            }
            return installed;
        }
    }

    public static RetroDedicatedBootstrap current() {
        return current;
    }

    /** Called only after Minecraft has successfully bound its real listener. */
    public static void minecraftListening(InetAddress address, int port) {
        RetroDedicatedBootstrap active = current;
        if (active != null) active.listenerBound(address, port);
    }

    public static void minecraftReady() {
        RetroDedicatedBootstrap active = current;
        if (active != null) active.ready();
    }

    public static void minecraftStopped() {
        RetroDedicatedBootstrap active = current;
        if (active != null) active.close();
    }

    public static long authenticatedMinecraftPeer(SocketAddress remoteAddress) {
        RetroDedicatedBootstrap active = current;
        return active == null ? 0L : active.resolveIngress(remoteAddress);
    }

    public static boolean requiresAuthenticatedIngress() {
        RetroDedicatedBootstrap active = current;
        return active != null && active.config.enabled()
                && active.backendStarted.get() && !active.closed.get();
    }

    public static boolean enabled() {
        RetroDedicatedBootstrap active = current;
        return active != null && active.config.enabled() && !active.closed.get();
    }

    public static String descriptor() {
        RetroDedicatedBootstrap active = current;
        if (active == null || !active.accepting()) return "";
        return new SteamDedicatedAddress(active.serverSteamId, active.generation).descriptor();
    }

    public static String status() {
        RetroDedicatedBootstrap active = current;
        if (active == null) return "UNAVAILABLE";
        if (!active.config.enabled()) return "DISABLED";
        if (!active.failureCategory.isEmpty()) return "FAILED:" + active.failureCategory;
        if (active.accepting()) return "ACCEPTING";
        if (active.backendStarted.get()) return "STARTING";
        return "WAITING_FOR_MINECRAFT";
    }

    private void listenerBound(InetAddress address, int port) {
        if (!config.enabled() || closed.get()) return;
        if (address == null || !address.isLoopbackAddress()
                || port < 1 || port > 65535) {
            throw new IllegalStateException(
                    "e4steam dedicated listener must be bound to loopback");
        }
        minecraftAddress = address;
        minecraftPort = port;
        if (!backendStarted.compareAndSet(false, true)) return;
        backend.start(config.backend(port)).whenComplete((ready, failure) -> {
            if (failure != null || ready == null || closed.get()) {
                failureCategory = "GAMESERVER_START_FAILED";
                return;
            }
            generation = ready.generation();
            serverSteamId = ready.internalServerSteamId();
            maybeAccept();
        });
    }

    private void ready() {
        if (!config.enabled() || closed.get()) return;
        minecraftReady.set(true);
        maybeAccept();
    }

    private synchronized void maybeAccept() {
        if (closed.get() || accepting.get() || !minecraftReady.get()
                || generation <= 0L || serverSteamId == 0L
                || minecraftAddress == null || minecraftPort == 0) return;
        RetroDedicatedServerTransport created =
                new RetroDedicatedServerTransport(backend, this, config.maxPeers());
        transport = created;
        accepting.set(true);
        created.start();
        E4steamClient.LOGGER.info("e4steam retro dedicated address: " + descriptor());
    }

    @Override public boolean accepting() { return accepting.get() && !closed.get(); }
    @Override public long generation() { return generation; }
    @Override public int minecraftPort() { return minecraftPort; }
    @Override public InetAddress minecraftAddress() { return minecraftAddress; }
    @Override public int maxPeers() { return config.maxPeers(); }
    @Override public boolean allows(long steamId) { return config.allows(steamId); }

    @Override public AutoCloseable registerIngress(
            int localPort, long steamId, long expectedGeneration) {
        if (!accepting() || expectedGeneration != generation || localPort < 1
                || localPort > 65535 || steamId == 0L) {
            throw new SecurityException("Stale dedicated ingress");
        }
        final Ingress value = new Ingress(steamId, expectedGeneration);
        if (ingress.putIfAbsent(Integer.valueOf(localPort), value) != null) {
            throw new SecurityException("Duplicate dedicated ingress");
        }
        return new AutoCloseable() {
            @Override public void close() {
                ingress.remove(Integer.valueOf(localPort), value);
            }
        };
    }

    private long resolveIngress(SocketAddress remoteAddress) {
        if (!(remoteAddress instanceof InetSocketAddress)) return 0L;
        InetSocketAddress address = (InetSocketAddress) remoteAddress;
        if (address.isUnresolved() || address.getAddress() == null
                || !address.getAddress().isLoopbackAddress()) return 0L;
        // The lease belongs to one live loopback TCP bridge. Keep it available
        // after the login handler consumes the identity so later play handlers
        // can apply the same Steam-specific timeouts. The source port cannot be
        // reused while that socket is alive, and closing the bridge removes it.
        Ingress value = ingress.get(Integer.valueOf(address.getPort()));
        return value != null && value.generation == generation ? value.steamId : 0L;
    }

    @Override public void transportFailed(String category) {
        failureCategory = category == null ? "DEDICATED_TRANSPORT_FAILED" : category;
        close();
    }

    @Override public void players(int count) {
        // Kept as a stable hook for future retro server status commands.
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        accepting.set(false);
        ingress.clear();
        RetroDedicatedServerTransport active = transport;
        transport = null;
        if (active != null) active.close();
        backend.stop(SteamRuntimeBackend.ShutdownReason.MINECRAFT_STOPPING);
    }

    private static final class Ingress {
        private final long steamId;
        private final long generation;
        private Ingress(long steamId, long generation) {
            this.steamId = steamId;
            this.generation = generation;
        }
    }
}
