package link.e4steam.internal.dedicated;

import link.e4steam.api.ApiResult;
import link.e4steam.api.dedicated.DedicatedServerService;
import link.e4steam.api.dedicated.DedicatedServerService.DedicatedServerSnapshot;
import link.e4steam.api.dedicated.DedicatedServerService.PublicationPlan;
import link.e4steam.api.dedicated.DedicatedServerService.PublicationProposal;
import link.e4steam.steam.SteamGameServerRuntimeBackend;
import link.e4steam.steam.SteamDedicatedAddress;
import link.e4steam.steam.SteamDedicatedServerTransport;
import link.e4steam.api.identity.IdentityService.PeerId;
import link.e4steam.api.session.SessionService.SessionId;
import link.e4steam.steam.SteamMinecraftIdentity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Coordinates headless Minecraft readiness with the isolated Steam GameServer backend. */
public final class DedicatedServerController implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger("e4steam");
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private static volatile DedicatedServerController current;

    private final DedicatedRuntimeConfig config;
    private final DedicatedLifecycle lifecycle;
    private final SteamGameServerRuntimeBackend backend;
    private final DedicatedIngressRegistry ingress = new DedicatedIngressRegistry();
    private final DedicatedAccessStore accessStore;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean listenerReady = new AtomicBoolean();
    private final AtomicBoolean minecraftReady = new AtomicBoolean();
    private volatile long backendGeneration;
    private volatile long backendSteamId;
    private volatile int minecraftPort;
    private volatile SteamDedicatedServerTransport transport;

    public DedicatedServerController(DedicatedRuntimeConfig config) {
        this(config, null);
    }

    DedicatedServerController(DedicatedRuntimeConfig config,
                              SteamGameServerRuntimeBackend backend) {
        this.config = java.util.Objects.requireNonNull(config, "config");
        this.lifecycle = new DedicatedLifecycle(config);
        this.accessStore = new DedicatedAccessStore(Paths.get(
                System.getProperty("user.dir", "."), "config", "e4steam-dedicated-access.txt"));
        this.backend = backend == null
                ? new SteamGameServerRuntimeBackend(this::backendState)
                : backend;
    }

    public static DedicatedServerController install(DedicatedRuntimeConfig config) {
        synchronized (DedicatedServerController.class) {
            if (current != null) return current;
            current = new DedicatedServerController(config);
            return current;
        }
    }

    public static DedicatedServerController current() {
        return current;
    }

    public void minecraftListening(InetAddress bindAddress, int port) {
        if (!config.enabled()) return;
        if (port < 1 || port > 65535) {
            fail("INVALID_MINECRAFT_PORT");
            return;
        }
        try {
            config.validateMinecraftBind(bindAddress);
        } catch (RuntimeException failure) {
            fail("INGRESS_NOT_LOOPBACK");
            throw failure;
        }
        listenerReady.set(true);
        minecraftPort = port;
        if (!started.compareAndSet(false, true)) {
            maybeAccept();
            return;
        }
        long generation = GENERATIONS.incrementAndGet();
        lifecycle.begin(generation);
        backend.start(config.backend(port)).whenComplete((ready, failure) -> {
            if (failure != null || ready == null) {
                fail("GAMESERVER_START_FAILED");
                return;
            }
            backendGeneration = ready.generation();
            backendSteamId = ready.internalServerSteamId();
            SteamDedicatedServerTransport created = new SteamDedicatedServerTransport(
                    backend, this, config.maxPeers());
            transport = created;
            created.start();
            maybeAccept();
        });
    }

    public void minecraftReady() {
        if (!config.enabled()) return;
        minecraftReady.set(true);
        maybeAccept();
    }

    public void validateMinecraftBind(InetAddress bindAddress) {
        if (config.enabled()) config.validateMinecraftBind(bindAddress);
    }

    public void minecraftStopped() {
        if (!started.get()) return;
        listenerReady.set(false);
        minecraftReady.set(false);
        try {
            if (lifecycle.snapshot().state()
                    != DedicatedServerService.DedicatedServerState.DRAINING) {
                lifecycle.transition(DedicatedServerService.DedicatedServerState.DRAINING);
            }
        } catch (RuntimeException ignored) {
        }
        ingress.clear();
        SteamDedicatedServerTransport activeTransport = transport;
        transport = null;
        if (activeTransport != null) activeTransport.close();
        backend.stop(SteamGameServerRuntimeBackend.ShutdownReason.MINECRAFT_STOPPING)
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        fail("GAMESERVER_STOP_FAILED");
                        return;
                    }
                    try {
                        lifecycle.transition(DedicatedServerService.DedicatedServerState.STOPPED);
                    } catch (RuntimeException ignoredTransition) {
                    }
                });
    }

    public long authenticatedMinecraftPeer(SocketAddress remoteAddress) {
        long generation = backendGeneration;
        return generation <= 0L ? 0L : ingress.resolve(remoteAddress, generation);
    }

    public AutoCloseable registerAuthenticatedIngress(int localPort, long steamId, long generation) {
        if (generation != backendGeneration || !backend.isAuthenticated(steamId, generation)) {
            throw new SecurityException("Dedicated ingress requires current Steam authentication");
        }
        return ingress.register(localPort, steamId, generation);
    }

    public DedicatedServerService service() {
        return new DedicatedServerService() {
            @Override public ApiResult<DedicatedServerSnapshot> snapshot() {
                return ApiResult.success(lifecycle.snapshot());
            }
            @Override public ApiResult<DedicatedConfigSnapshot> config() {
                return ApiResult.success(DedicatedServerController.this.config.safeSnapshot());
            }
            @Override public CompletionStage<ApiResult<DedicatedServerSnapshot>> readiness() {
                CompletableFuture<ApiResult<DedicatedServerSnapshot>> result =
                        new CompletableFuture<>();
                lifecycle.readiness().whenComplete((snapshot, failure) -> {
                    if (failure != null) {
                        result.complete(ApiResult.failure(new link.e4steam.api.ApiError(
                                link.e4steam.api.ApiErrorCode.UNAVAILABLE,
                                "error.e4steam.dedicated_not_ready",
                                link.e4steam.api.Retryability.AFTER_STATE_CHANGE,
                                "dedicated.readiness",
                                "",
                                "DedicatedNotReady"
                        )));
                    } else result.complete(ApiResult.success(snapshot));
                });
                return result;
            }
            @Override public CompletionStage<ApiResult<DedicatedServerSnapshot>> drain(String reason) {
                minecraftStopped();
                return CompletableFuture.completedFuture(ApiResult.success(lifecycle.snapshot()));
            }
            @Override public CompletionStage<ApiResult<PublicationPlan>> proposePublication(
                    PublicationProposal proposal
            ) {
                return CompletableFuture.completedFuture(ApiResult.success(
                        new PublicationPlan(false, "public-worlds-addon-required")
                ));
            }
        };
    }

    public long generation() { return backendGeneration; }

    public int minecraftPort() { return minecraftPort; }

    public int maxPeers() { return config.maxPeers(); }

    public SessionId addonSessionId() {
        long generation = backendGeneration;
        return generation <= 0L ? null : new SessionId(
                link.e4steam.steam.SteamPeerPrivacy.dedicatedSessionId(generation), generation);
    }

    public java.util.Set<String> addonPeerIds() {
        SteamDedicatedServerTransport active = transport;
        return active == null ? java.util.Collections.emptySet() : active.safeOpaquePeerIds();
    }

    public boolean matchesAddonPeer(SessionId sessionId, PeerId peerId) {
        SessionId current = addonSessionId();
        return current != null && current.equals(sessionId) && peerId != null
                && addonPeerIds().contains(peerId.value());
    }

    public DedicatedPeerIdentity addonPeerIdentity(String opaquePeerId) {
        if (opaquePeerId == null || opaquePeerId.isEmpty()) return null;
        SteamDedicatedServerTransport active = transport;
        return active == null ? null : active.safePeerIdentity(opaquePeerId);
    }

    /** Safe authenticated Minecraft projection; the source Steam ID is absent. */
    public static final class DedicatedPeerIdentity {
        private final String opaquePeerId;
        private final java.util.UUID minecraftUuid;
        private final String minecraftName;

        public DedicatedPeerIdentity(String opaquePeerId, java.util.UUID minecraftUuid,
                                     String minecraftName) {
            this.opaquePeerId = java.util.Objects.requireNonNull(opaquePeerId, "opaquePeerId");
            this.minecraftUuid = java.util.Objects.requireNonNull(minecraftUuid, "minecraftUuid");
            this.minecraftName = java.util.Objects.requireNonNull(minecraftName, "minecraftName");
        }

        public String opaquePeerId() { return opaquePeerId; }
        public java.util.UUID minecraftUuid() { return minecraftUuid; }
        public String minecraftName() { return minecraftName; }
        @Override public String toString() { return "DedicatedPeerIdentity{opaque}"; }
    }

    public boolean accepting() {
        return lifecycle.snapshot().state()
                == DedicatedServerService.DedicatedServerState.ACCEPTING;
    }

    public boolean requiresAuthenticatedIngress() {
        return config.enabled() && started.get();
    }

    public void players(int value) { lifecycle.players(value); }

    public void transportFailed(String category) { fail(category); }

    public boolean isBanned(long steamId) {
        return accessStore.banned(steamId)
                || accessStore.banned(SteamMinecraftIdentity.uuid(steamId));
    }

    public boolean whitelistRequired() {
        return config.accessMode()
                != DedicatedServerService.DedicatedAccessMode.UNLISTED;
    }

    public boolean isWhitelisted(long steamId) {
        return config.isWhitelisted(steamId) || accessStore.whitelisted(steamId)
                || accessStore.whitelisted(SteamMinecraftIdentity.uuid(steamId));
    }

    public boolean updateWhitelist(String identity, boolean present) {
        ParsedIdentity parsed = ParsedIdentity.parse(identity);
        return parsed.steamId != 0L
                ? accessStore.setWhitelisted(parsed.steamId, present)
                : accessStore.setWhitelisted(parsed.uuid, present);
    }

    public boolean updateBan(String identity, boolean present) {
        ParsedIdentity parsed = ParsedIdentity.parse(identity);
        return parsed.steamId != 0L
                ? accessStore.setBanned(parsed.steamId, present)
                : accessStore.setBanned(parsed.uuid, present);
    }

    public String normalizedIdentity(String identity) {
        ParsedIdentity parsed = ParsedIdentity.parse(identity);
        return parsed.steamId != 0L
                ? SteamMinecraftIdentity.uuid(parsed.steamId).toString()
                : parsed.uuid.toString();
    }

    public String descriptor() {
        long id = backendSteamId;
        long generation = backendGeneration;
        if (id == 0L || generation <= 0L || !accepting()) return "";
        return new SteamDedicatedAddress(id, generation).descriptor();
    }

    public String safeStatus() {
        link.e4steam.api.dedicated.DedicatedServerService.DedicatedServerSnapshot snapshot =
                lifecycle.snapshot();
        return "e4steam dedicated: state=" + snapshot.state()
                + ", access=" + snapshot.accessMode()
                + ", players=" + snapshot.players() + '/' + snapshot.capacity()
                + ", guarded=" + snapshot.ingressGuarded()
                + ", publication=false";
    }

    @Override public void close() {
        minecraftStopped();
    }

    private void maybeAccept() {
        if (!listenerReady.get() || !minecraftReady.get() || backend.snapshot().state()
                != SteamGameServerRuntimeBackend.State.TRANSPORT_READY) return;
        try {
            if (lifecycle.snapshot().state()
                    == DedicatedServerService.DedicatedServerState.TRANSPORT_READY) {
                lifecycle.accepting(true);
                LOGGER.info("Dedicated e4steam transport is accepting authenticated peers");
            }
        } catch (RuntimeException failure) {
            fail("READINESS_TRANSITION_FAILED");
        }
    }

    void backendState(SteamGameServerRuntimeBackend.State state, String category) {
        try {
            switch (state) {
                case CONFIG_VALIDATED:
                case OFF:
                    return;
                case NATIVES_READY:
                    lifecycle.transition(DedicatedServerService.DedicatedServerState.NATIVES_READY);
                    return;
                case STEAM_INITIALIZING:
                    lifecycle.transition(DedicatedServerService.DedicatedServerState.STEAM_INITIALIZING);
                    return;
                case STEAM_LOGGING_ON:
                    lifecycle.transition(DedicatedServerService.DedicatedServerState.STEAM_LOGGING_ON);
                    return;
                case TRANSPORT_READY:
                    lifecycle.transition(DedicatedServerService.DedicatedServerState.TRANSPORT_READY);
                    maybeAccept();
                    return;
                case DRAINING:
                    if (lifecycle.snapshot().state()
                            != DedicatedServerService.DedicatedServerState.DRAINING) {
                        lifecycle.transition(DedicatedServerService.DedicatedServerState.DRAINING);
                    }
                    return;
                case STOPPED:
                    if (lifecycle.snapshot().state()
                            == DedicatedServerService.DedicatedServerState.DRAINING) {
                        lifecycle.transition(DedicatedServerService.DedicatedServerState.STOPPED);
                    }
                    return;
                case FAILED:
                    fail(category);
                    return;
                default:
            }
        } catch (RuntimeException failure) {
            fail("INVALID_BACKEND_TRANSITION");
        }
    }

    private void fail(String category) {
        ingress.clear();
        SteamDedicatedServerTransport activeTransport = transport;
        transport = null;
        if (activeTransport != null) activeTransport.close();
        lifecycle.fail(category);
        LOGGER.warn("Dedicated e4steam is unavailable [{}]", category);
    }

    private static final class ParsedIdentity {
        private final long steamId;
        private final java.util.UUID uuid;
        private ParsedIdentity(long steamId, java.util.UUID uuid) {
            this.steamId = steamId;
            this.uuid = uuid;
        }
        private static ParsedIdentity parse(String value) {
            String checked = java.util.Objects.requireNonNull(value, "identity").trim();
            if (checked.isEmpty() || checked.length() > 64) {
                throw new IllegalArgumentException("identity");
            }
            try {
                long steamId = Long.parseUnsignedLong(checked);
                if (steamId != 0L) return new ParsedIdentity(steamId, null);
            } catch (NumberFormatException ignored) { }
            try { return new ParsedIdentity(0L, java.util.UUID.fromString(checked)); }
            catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("Expected SteamID64 or derived UUID", failure);
            }
        }
    }
}
