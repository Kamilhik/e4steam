package link.e4steam.steam;

import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamAuthTicket;
import com.codedisaster.steamworks.SteamNativeHandle;
import com.codedisaster.steamworks.SteamResult;
import com.codedisaster.steamworks.SteamUser;
import com.codedisaster.steamworks.SteamUserCallback;
import com.codedisaster.steamworks.SteamUtils;
import com.codedisaster.steamworks.SteamUtilsCallback;
import link.e4steam.Agnos;
import link.e4steam.E4steamClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Owns Steamworks for the Minecraft process. Every native networking call is
 * serialized on a single daemon thread.
 */
public final class SteamRuntime implements SteamBridgeRuntime {
    private static final int APP_ID = 480;
    private static final int CHANNEL = 480;
    // Category limits leave room for terminal frames while preventing UDP
    // voice traffic from starving Minecraft's reliable TCP stream.
    private static final int MAX_OUTBOUND_PACKETS = 2048;
    private static final int MAX_OUTBOUND_DATA_PACKETS = 1344;
    private static final int MAX_OUTBOUND_DATAGRAM_PACKETS = 320;
    private static final int MAX_OUTBOUND_ADDON_PACKETS = 128;
    private static final int MAX_OUTBOUND_OPEN_PACKETS = 64;
    private static final int MAX_OUTBOUND_STANDALONE_RESETS = 64;
    // Yield regularly to the per-bridge localhost writer threads. Draining
    // hundreds of 32 KiB packets in one worker iteration can fill a bridge's
    // bounded inbound queue before its writer gets scheduled, which looks
    // like an infinitely falling player followed by a disconnect.
    private static final int MAX_PACKETS_PER_TICK = 32;
    private static final long OUTBOUND_SEND_RETRY_DELAY_MILLIS = 25;
    private static final long OUTBOUND_SEND_RETRY_TIMEOUT_MILLIS = 30_000;
    private static final int MAX_RESET_RETRIES = 64;
    private static final int MAX_RESET_RETRY_ATTEMPTS = 10;
    private static final long MAX_RESET_RETRY_AGE_MILLIS = 30_000;
    private static final long RESET_RETRY_BASE_DELAY_MILLIS = 25;
    private static final long RESET_RETRY_MAX_DELAY_MILLIS = 2_000;
    private static final int MAX_RESET_RETRIES_PER_TICK = 8;
    private static final int MAX_ACTIVE_CONNECTIONS = 64;
    private static final int MAX_PENDING_PEERS = 64;
    private static final long PENDING_PEER_TIMEOUT_MILLIS = 10_000;
    private static final long IDLE_SESSION_CLOSE_DELAY_MILLIS = 250;
    private static final long IDLE_SESSION_RECHECK_MILLIS = 100;
    private static final long IDLE_SESSION_MAX_DRAIN_MILLIS = 2_000;
    private static final int LOOPBACK_CONNECT_TIMEOUT_MILLIS = 100;
    private static final long LOOPBACK_FAILURE_BACKOFF_MILLIS = 2_000;
    /**
     * Bounds compatibility with older peers that do not send BRIDGE_READY.
     *
     * <p>Every loader uses the same OPEN -> OPEN_ACK -> BRIDGE_READY ordering.
     * Without the final confirmation, a fast local Minecraft socket can put DATA
     * ahead of the peer's bridge registration, which makes the first join fail
     * even though a retry succeeds.</p>
     */
    private static final long BRIDGE_READY_FALLBACK_MILLIS = 2_000;
    private static final long CLIENT_RECONNECT_GRACE_MILLIS = 3_000;
    private static final long CLIENT_OPEN_ACK_FALLBACK_MILLIS = 10_000;
    private static final long KNOWN_PEER_ACCEPT_INTERVAL_MILLIS = 100;
    // CloseSessionWithUser releases the old native session asynchronously from
    // the bridge lifecycle. Do not let the compatibility pre-accept poll revive
    // that same session before its terminal frames have drained.
    private static final long KNOWN_PEER_REACCEPT_DELAY_MILLIS = Math.max(
            CLIENT_RECONNECT_GRACE_MILLIS,
            IDLE_SESSION_MAX_DRAIN_MILLIS + KNOWN_PEER_ACCEPT_INTERVAL_MILLIS
    );
    private static final Duration START_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STEAM_TASK_TIMEOUT = Duration.ofSeconds(10);
    private static final long RUNTIME_IDLE_SHUTDOWN_MILLIS = 1_000;

    private static final SteamRuntime INSTANCE = new SteamRuntime();

    private final Object lifecycleLock = new Object();
    private final Object peerSessionLock = new Object();
    private final SteamLifecycle steamLifecycle;
    private final SteamOutboundQueue<SteamConnectionBridge> outbound = new SteamOutboundQueue<>(
            MAX_OUTBOUND_PACKETS,
            MAX_OUTBOUND_DATA_PACKETS,
            MAX_OUTBOUND_DATAGRAM_PACKETS,
            MAX_OUTBOUND_ADDON_PACKETS,
            MAX_OUTBOUND_OPEN_PACKETS,
            MAX_OUTBOUND_STANDALONE_RESETS
    );
    private final SteamResetRetryQueue<SteamConnectionBridge> resetRetries =
            new SteamResetRetryQueue<>(
                    MAX_RESET_RETRIES,
                    MAX_RESET_RETRY_ATTEMPTS,
                    MAX_RESET_RETRY_AGE_MILLIS,
                    RESET_RETRY_BASE_DELAY_MILLIS,
                    RESET_RETRY_MAX_DELAY_MILLIS
            );
    private final SteamBridgeRegistry<SteamConnectionBridge, SteamUdpBridge> bridgeRegistry =
            new SteamBridgeRegistry<>(MAX_ACTIVE_CONNECTIONS);
    private final ConcurrentHashMap<Long, Long> pendingPeers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> clientReconnectDeadlines = new ConcurrentHashMap<>();
    private final SteamKnownPeerSessionGate knownPeerSessionGate =
            new SteamKnownPeerSessionGate(KNOWN_PEER_REACCEPT_DELAY_MILLIS);
    // Host bridges authenticate an exact localhost source port. Keep this
    // fact independent from lobby/runtime state because Forge may perform its
    // login check on another thread while those states are transitioning.
    private final ConcurrentHashMap<Integer, AuthenticatedLoopbackPeer> authenticatedLoopbackPeers =
            new ConcurrentHashMap<>();
    // long[0] = next check, long[1] = forced close. An array keeps this
    // state in SteamRuntime itself and avoids an extra lazy-loaded class on
    // Forge 1.17/1.18.
    private final ConcurrentHashMap<Long, long[]> idleSessionDeadlines = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<SteamTask<?>> steamTasks = new ConcurrentLinkedQueue<>();

    // A reliable packet rejected by Steam with temporary backpressure stays
    // ahead of the regular queue. Re-enqueuing it at the tail would reorder
    // Minecraft's TCP byte stream and corrupt the connection.
    private SteamOutboundQueue.Packet<SteamConnectionBridge> retryOutboundPacket;
    private long retryOutboundNotBeforeMillis;
    private long retryOutboundDeadlineMillis;

    private volatile Status status = Status.NEW;
    private volatile Throwable failureCause;
    private volatile long localSteamId;
    private volatile Thread workerThread;
    private volatile WorkerGeneration generation;
    private volatile SteamNetworkingMessagesTransport transport;
    private volatile SteamNetworkingSocketsP2PTransport dedicatedTransport;
    private volatile SteamUser user;
    private volatile SteamUtils utils;
    private volatile SteamLobbyManager lobbyManager;
    private volatile HostRegistration hostRegistration;
    private volatile long nextLoopbackConnectAttemptAtMillis;
    private long nextKnownPeerAcceptAtMillis;
    private boolean permanentlyShutdown;
    private int activityCount;
    private long nextWorkerGenerationId;
    private final AtomicBoolean launchStartRequested = new AtomicBoolean();
    private volatile Activity launchActivity;

    SteamRuntime() {
        steamLifecycle = new SteamLifecycle(new SteamworksApi());
        Thread shutdownHook = new Thread(this::shutdown, "e4steam-steam-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public static SteamRuntime get() {
        return INSTANCE;
    }

    /** Starts Spacewar with Minecraft and keeps it active for this game process. */
    public void startAtGameLaunchAsync() {
        if (!Agnos.isClient() || !launchStartRequested.compareAndSet(false, true)) {
            return;
        }
        Thread starter = new Thread(() -> {
            Activity activity = null;
            try {
                activity = acquireActivity();
                awaitReady();
                synchronized (lifecycleLock) {
                    if (status != Status.RUNNING || !launchStartRequested.get()) {
                        throw new IOException("Steam stopped during automatic startup");
                    }
                    launchActivity = activity;
                }
                E4steamClient.LOGGER.info("Steam/Spacewar started with Minecraft");
            } catch (Throwable throwable) {
                if (activity != null) {
                    activity.close();
                }
                launchStartRequested.set(false);
                E4steamClient.LOGGER.warn(
                        "Steam was not available at Minecraft startup; e4steam can retry later",
                        throwable
                );
            }
        }, "e4steam-steam-launch-start");
        starter.setDaemon(true);
        starter.start();
    }

    /**
     * Old ModLauncher releases may fail to define a shaded class when that
     * class is first requested from a Steam callback thread. The retro build
     * generates a complete inventory of e4steam classes, including anonymous
     * and nested classes, so they can all be resolved on Minecraft's startup
     * thread before Steam begins processing callbacks.
     */
    public static void preloadCompatibilityClasses() {
        List<String> names = new ArrayList<>();
        ClassLoader loader = SteamRuntime.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream("e4steam-retro-preload.txt")) {
            if (input != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    String name;
                    while ((name = reader.readLine()) != null) {
                        name = name.trim();
                        if (!name.isEmpty()) {
                            names.add(name);
                        }
                    }
                }
            }
        } catch (IOException exception) {
            E4steamClient.LOGGER.warn("Could not read the retro compatibility preload list", exception);
        }

        Collections.addAll(names,
                "com.codedisaster.steamworks.SteamAPICall",
                "com.codedisaster.steamworks.SteamException",
                "com.codedisaster.steamworks.SteamFriends",
                "com.codedisaster.steamworks.SteamFriends$FriendFlags",
                "com.codedisaster.steamworks.SteamFriends$FriendGameInfo",
                "com.codedisaster.steamworks.SteamFriends$FriendRelationship",
                "com.codedisaster.steamworks.SteamFriends$OverlayDialog",
                "com.codedisaster.steamworks.SteamFriends$OverlayToStoreFlag",
                "com.codedisaster.steamworks.SteamFriends$OverlayToUserDialog",
                "com.codedisaster.steamworks.SteamFriends$OverlayToWebPageMode",
                "com.codedisaster.steamworks.SteamFriends$PersonaChange",
                "com.codedisaster.steamworks.SteamFriends$PersonaState",
                "com.codedisaster.steamworks.SteamFriendsCallback",
                "com.codedisaster.steamworks.SteamFriendsCallbackAdapter",
                "com.codedisaster.steamworks.SteamFriendsNative",
                "com.codedisaster.steamworks.SteamID",
                "com.codedisaster.steamworks.SteamMatchmaking",
                "com.codedisaster.steamworks.SteamMatchmaking$ChatEntry",
                "com.codedisaster.steamworks.SteamMatchmaking$ChatEntryType",
                "com.codedisaster.steamworks.SteamMatchmaking$ChatMemberStateChange",
                "com.codedisaster.steamworks.SteamMatchmaking$ChatRoomEnterResponse",
                "com.codedisaster.steamworks.SteamMatchmaking$LobbyComparison",
                "com.codedisaster.steamworks.SteamMatchmaking$LobbyDistanceFilter",
                "com.codedisaster.steamworks.SteamMatchmaking$LobbyType",
                "com.codedisaster.steamworks.SteamMatchmakingCallback",
                "com.codedisaster.steamworks.SteamMatchmakingCallbackAdapter",
                "com.codedisaster.steamworks.SteamMatchmakingGameServerItem",
                "com.codedisaster.steamworks.SteamMatchmakingKeyValuePair",
                "com.codedisaster.steamworks.SteamMatchmakingNative",
                "com.codedisaster.steamworks.SteamMatchmakingPingResponse",
                "com.codedisaster.steamworks.SteamMatchmakingPlayersResponse",
                "com.codedisaster.steamworks.SteamMatchmakingRulesResponse",
                "com.codedisaster.steamworks.SteamMatchmakingServerListResponse",
                "com.codedisaster.steamworks.SteamMatchmakingServerListResponse$Response",
                "com.codedisaster.steamworks.SteamMatchmakingServerNetAdr",
                "com.codedisaster.steamworks.SteamMatchmakingServers",
                "com.codedisaster.steamworks.SteamMatchmakingServersNative"
        );
        for (String name : names) {
            try {
                Class.forName(name, false, loader);
            } catch (ClassNotFoundException | LinkageError error) {
                E4steamClient.LOGGER.warn("Could not preload Steam compatibility class {}", name, error);
            }
        }
    }

    /**
     * Keeps the Steam API alive for one user-visible operation. Activities are
     * cheap, restart-safe leases and may be closed more than once.
     */
    public Activity acquireActivity() {
        synchronized (lifecycleLock) {
            if (permanentlyShutdown) {
                throw new IllegalStateException("Steam runtime has been shut down");
            }
            activityCount++;
            WorkerGeneration current = generation;
            if (current != null) {
                current.idleSinceMillis = 0;
            }
            return new Activity(this);
        }
    }

    public void awaitReady() throws IOException {
        if (!Agnos.isClient()) {
            throw new IOException("This e4steam release supports integrated LAN worlds only");
        }
        WorkerGeneration target = ensureWorkerStarted();
        try {
            target.ready.get(START_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            synchronized (lifecycleLock) {
                if (generation != target || target.stopRequested.get() || status != Status.RUNNING) {
                    throw new IOException("Steam runtime stopped before it became usable (status: " + status + ")");
                }
            }
        } catch (TimeoutException exception) {
            throw new IOException("Timed out while initializing Steam", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while initializing Steam", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IOException("Steam initialization failed: " + cause.getMessage(), cause);
        }
        // If Steam was unavailable during Minecraft startup but became
        // available for a later command/screen operation, promote that
        // successful recovery to the normal process-lifetime lease.
        if (!launchStartRequested.get()) {
            startAtGameLaunchAsync();
        }
    }

    public String statusSummary() {
        String summary = status.name().toLowerCase();
        if (status == Status.RUNNING) {
            summary += " (Steam client connected as " + steamId() + ")";
        }
        return summary;
    }

    /** Returns a privacy-safe machine status without Steam identifiers or native state. */
    public String safeStatusCode() {
        return status.name();
    }

    /** Returns only the sanitized exception class category, never its message or stack trace. */
    public String safeFailureCategory() {
        Throwable failure = failureCause;
        if (failure == null) return "";
        String category = failure.getClass().getSimpleName();
        return category.matches("[A-Za-z0-9_$.-]{1,64}") ? category : "SteamFailure";
    }

    /** Returns a generation-bound view without Steam IDs, tickets, lobby tokens or native handles. */
    public SafeSessionView safeSessionView() {
        WorkerGeneration current = generation;
        HostRegistration hosting = hostRegistration;
        Collection<SteamConnectionBridge> bridges = bridgeRegistry.snapshot();
        if (current == null || (hosting == null && bridges.isEmpty())) {
            return SafeSessionView.inactive();
        }
        long generationId = current.id;
        String role = hosting != null ? "INTEGRATED_HOST" : "GUEST";
        SteamConnectionBridge dedicatedBridge = null;
        if (hosting == null) {
            for (SteamConnectionBridge candidate : bridges) {
                if (candidate != null && !candidate.isClosed()
                        && candidate.dedicatedSessionGeneration() > 0L) {
                    dedicatedBridge = candidate;
                    generationId = candidate.dedicatedSessionGeneration();
                    role = "DEDICATED_SERVER_CLIENT";
                    break;
                }
            }
        }
        String stateCode;
        switch (status) {
            case STARTING: stateCode = "CREATING"; break;
            case RUNNING: stateCode = "ACTIVE"; break;
            case STOPPING: stateCode = "CLOSING"; break;
            case FAILED: stateCode = "FAILED"; break;
            case STOPPED: stateCode = "CLOSED"; break;
            default: stateCode = "NONE"; break;
        }
        LinkedHashMap<String, SafePeerIdentity> peers = new LinkedHashMap<>();
        for (SteamConnectionBridge bridge : bridges) {
            if (bridge == null || bridge.isClosed()) continue;
            if (dedicatedBridge != null && bridge != dedicatedBridge) continue;
            long remote = bridge.remoteSteamId();
            String opaque = opaquePeerId(generationId, remote);
            peers.putIfAbsent(opaque, new SafePeerIdentity(
                    opaque, SteamMinecraftIdentity.uuid(remote),
                    SteamMinecraftIdentity.safeName(remote)));
        }
        ArrayList<SafePeerIdentity> ordered = new ArrayList<>(peers.values());
        ordered.sort(Comparator.comparing(SafePeerIdentity::opaquePeerId));
        if (generation != current) return SafeSessionView.inactive();
        String sessionId = dedicatedBridge == null
                ? "session_" + Long.toUnsignedString(generationId, 36)
                : dedicatedSessionId(generationId);
        return new SafeSessionView(sessionId,
                generationId, role, stateCode, SteamLobbyManager.VANILLA_LOBBY_CAPACITY, ordered);
    }

    /** Returns the local Steam-derived Minecraft projection without exposing the SteamID. */
    public SafeMinecraftIdentity safeLocalMinecraftIdentity() {
        long id = localSteamId;
        return id == 0 ? null : new SafeMinecraftIdentity(
                SteamMinecraftIdentity.uuid(id), SteamMinecraftIdentity.safeName(id));
    }

    /** Resolves only a currently authenticated opaque peer from the active generation. */
    public SafePeerIdentity safeResolvePeer(String opaquePeerId) {
        if (opaquePeerId == null) return null;
        for (SafePeerIdentity peer : safeSessionView().peers()) {
            if (peer.opaquePeerId().equals(opaquePeerId)) return peer;
        }
        return null;
    }

    String safeOpaquePeerId(long remoteSteamId) {
        SafeSessionView view = safeSessionView();
        return !view.active() || remoteSteamId == 0L
                ? null : opaquePeerId(view.generation(), remoteSteamId);
    }

    /** Gracefully closes the exact current session generation; stale generations cannot affect a replacement. */
    public boolean disconnectSafeSession(long expectedGeneration) {
        WorkerGeneration current = generation;
        if (expectedGeneration <= 0L) return false;
        for (SteamConnectionBridge bridge : bridgeRegistry.snapshot()) {
            if (bridge != null && !bridge.isClosed()
                    && bridge.dedicatedSessionGeneration() == expectedGeneration) {
                bridge.close(true);
                return true;
            }
        }
        if (current == null || current.id != expectedGeneration) return false;
        HostRegistration hosting = hostRegistration;
        if (hosting != null) hosting.owner().stop();
        else stopForDirectServerConnection();
        return true;
    }

    public String steamId() {
        return localSteamId == 0 ? "unavailable" : Long.toUnsignedString(localSteamId);
    }

    public Throwable failureCause() {
        return failureCause;
    }

    private static String opaquePeerId(long generationId, long steamId) {
        return SteamPeerPrivacy.opaquePeerId(generationId, steamId);
    }

    public static String dedicatedSessionId(long generation) {
        return SteamPeerPrivacy.dedicatedSessionId(generation);
    }

    long steamIdValue() {
        return localSteamId;
    }

    /**
     * Returns the authenticated Steam peer owning this exact Minecraft TCP
     * connection, or zero for ordinary LAN, unresolved, and stale sockets.
     */
    public long authenticatedMinecraftPeer(SocketAddress remoteAddress) {
        int remotePort = SteamLoopbackAuthentication.loopbackPort(remoteAddress);
        if (remotePort < 0) {
            return 0;
        }
        AuthenticatedLoopbackPeer peer = authenticatedLoopbackPeers.get(remotePort);
        if (peer == null || peer.bridge().isClosed()) {
            if (peer != null) {
                authenticatedLoopbackPeers.remove(remotePort, peer);
            }
            return 0;
        }
        return peer.remoteSteamId();
    }

    void startHosting(
            SteamSession owner,
            int localPort,
            int udpPort,
            byte[] token,
            SteamAccessMode accessMode
    ) throws IOException {
        awaitReady();
        if (localPort < 1 || localPort > 65535) {
            throw new IOException("Invalid LAN port: " + localPort);
        }
        if (accessMode == SteamAccessMode.LOCAL_ONLY) {
            throw new IOException("Local-only mode does not start Steam hosting");
        }
        if (udpPort < 0 || udpPort > 65535) {
            throw new IOException("Invalid UDP tunnel port: " + udpPort);
        }

        VoiceChatUdpEndpoint udpEndpoint = VoiceChatUdpEndpoint.resolve(localPort, udpPort);
        HostRegistration replacement = new HostRegistration(
                owner,
                localPort,
                udpEndpoint,
                token.clone(),
                accessMode
        );
        if (udpEndpoint.hostPort() > 0) {
            E4steamClient.LOGGER.info(
                    "Using UDP port {} for {}",
                    udpEndpoint.hostPort(),
                    udpEndpoint.source()
            );
        }
        synchronized (lifecycleLock) {
            HostRegistration current = hostRegistration;
            if (current != null && current.owner() != owner) {
                throw new IOException("Another Steam hosting session is still stopping");
            }
            hostRegistration = replacement;
            nextLoopbackConnectAttemptAtMillis = 0;
        }
    }

    void stopHosting(SteamSession owner) {
        boolean removed = false;
        synchronized (lifecycleLock) {
            HostRegistration current = hostRegistration;
            if (current != null && current.owner() == owner) {
                hostRegistration = null;
                removed = true;
            }
        }
        if (removed) {
            closeHostBridges(owner);
        }
        // Social state is authoritative for the Steam lobby. Always ask it
        // to stop this owner even if the local registration was already
        // removed during a race or worker failure.
        submitSteamTaskIfRunning(() -> {
            SteamLobbyManager current = lobbyManager;
            if (current != null) {
                current.stopHosting(owner);
            }
            return null;
        });
    }

    CompletableFuture<Long> createHostLobby(
            SteamSession owner,
            SteamAccessMode accessMode,
            SteamAddress address
    ) throws IOException {
        awaitReady();
        CompletableFuture<CompletableFuture<Long>> scheduled = submitSteamTask(() -> {
            SteamLobbyManager current = lobbyManager;
            if (current == null) {
                throw new IOException("Steam social services are unavailable");
            }
            return current.createHostLobby(owner, accessMode, address);
        });
        return scheduled.thenCompose(Function.identity());
    }

    CompletableFuture<Void> openHostInviteOverlay(SteamSession owner) throws IOException {
        awaitReady();
        return submitSteamTask(() -> {
            SteamLobbyManager current = lobbyManager;
            if (current == null) {
                throw new IOException("Steam social services are unavailable");
            }
            current.openHostInviteOverlay(owner);
            return null;
        });
    }

    public void openFriendsOverlay() throws IOException {
        awaitReady();
        CompletableFuture<Void> task = submitSteamTask(() -> {
            SteamLobbyManager current = lobbyManager;
            if (current == null) {
                throw new IOException("Steam social services are unavailable");
            }
            current.openFriendsOverlay();
            return null;
        });
        waitForSteamTask(task, STEAM_TASK_TIMEOUT, "opening the Steam friends overlay");
    }

    public void cancelGuestJoin() {
        submitSteamTaskIfRunning(() -> {
            SteamLobbyManager current = lobbyManager;
            if (current != null) {
                current.cancelGuestJoin();
            }
            return null;
        });
    }

    /** Clears e4steam connections before an ordinary multiplayer connection. */
    public void stopForDirectServerConnection() {
        SteamClientBridge.cancelPending();
        SteamDedicatedClientBridge.cancelPending();
        synchronized (lifecycleLock) {
            hostRegistration = null;
        }
        for (SteamConnectionBridge bridge : bridgeRegistry.snapshot()) {
            bridge.close(false);
        }
        clearOutbound();
        pendingPeers.clear();
        knownPeerSessionGate.clear();
        idleSessionDeadlines.clear();

        // The launch activity intentionally keeps Spacewar running for the
        // lifetime of Minecraft. If startup failed, normal idle shutdown still
        // stops any temporary runtime generation after bridges are released.
    }

    public CompletableFuture<Boolean> beginGuestConnect(String endpoint) {
        return submitSteamTaskIfRunning(() -> {
            SteamLobbyManager current = lobbyManager;
            return current != null && current.beginGuestConnect(endpoint);
        });
    }

    public CompletableFuture<Boolean> claimGuestInvite(String endpoint) {
        return submitSteamTaskIfRunning(() -> {
            SteamLobbyManager current = lobbyManager;
            return current != null && current.claimGuestInvite(endpoint);
        });
    }

    /** Creates a short-lived official Steam auth proof for a dedicated server bridge. */
    DedicatedClientAuth createDedicatedClientAuth() throws IOException {
        awaitReady();
        try {
            return waitForSteamTask(
                    submitSteamTask(() -> {
                        SteamUser current = user;
                        if (current == null) throw new IOException("Steam user API is unavailable");
                        ByteBuffer target = ByteBuffer.allocateDirect(SteamProtocol.MAX_AUTH_TICKET_SIZE);
                        int[] size = new int[1];
                        SteamAuthTicket handle = current.getAuthSessionTicket(target, size);
                        if (handle == null || !handle.isValid() || size[0] <= 0
                                || size[0] > SteamProtocol.MAX_AUTH_TICKET_SIZE) {
                            if (handle != null && handle.isValid()) current.cancelAuthTicket(handle);
                            zero(target);
                            throw new IOException("Steam did not issue a valid authentication proof");
                        }
                        target.flip();
                        target.limit(size[0]);
                        byte[] proof = new byte[size[0]];
                        target.get(proof);
                        zero(target);
                        byte[] nonce = new byte[32];
                        new SecureRandom().nextBytes(nonce);
                        return new DedicatedClientAuth(this, handle, proof, nonce);
                    }),
                    STEAM_TASK_TIMEOUT,
                    "create dedicated Steam authentication proof"
            );
        } catch (IOException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IOException("Could not create Steam authentication proof", failure);
        }
    }

    /** Opens the connection-oriented Steam route used by a dedicated server descriptor. */
    void prepareDedicatedConnection(long remoteSteamId) throws IOException {
        awaitReady();
        try {
            waitForSteamTask(
                    submitSteamTask(() -> {
                        SteamNetworkingSocketsP2PTransport current = dedicatedTransport;
                        if (current != null) {
                            current.close();
                            dedicatedTransport = null;
                        }
                        dedicatedTransport = SteamNetworkingSocketsP2PTransport.openClient(
                                steamLifecycle.steamApiPath(),
                                remoteSteamId,
                                new SteamNetworkingSocketsP2PTransport.SessionListener() {
                                    @Override
                                    public void onSessionRequest(long ignored) {
                                        // ConnectP2P is outbound; no inbound admission is expected here.
                                    }

                                    @Override
                                    public void onSessionFailed(
                                            long failedSteamId,
                                            int reason,
                                            String detail
                                    ) {
                                        String safeDetail = detail == null || detail.trim().isEmpty()
                                                ? "no Steam diagnostic"
                                                : detail.trim();
                                        E4steamClient.LOGGER.warn(
                                                "Dedicated Steam P2P connection failed (reason {}): {}",
                                                reason,
                                                safeDetail
                                        );
                                        for (SteamConnectionBridge bridge : bridgeRegistry.snapshot()) {
                                            if (!bridge.isHostSide()
                                                    && bridge.remoteSteamId() == failedSteamId
                                                    && bridge.dedicatedSessionGeneration() > 0L) {
                                                bridge.close(false);
                                            }
                                        }
                                    }
                                }
                        );
                        return null;
                    }),
                    STEAM_TASK_TIMEOUT,
                    "open dedicated Steam P2P connection"
            );
        } catch (IOException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IOException("Could not open the dedicated Steam P2P connection", failure);
        }
    }

    private void cancelDedicatedAuthTicket(SteamAuthTicket ticket) {
        if (ticket == null || !ticket.isValid()) return;
        submitSteamTaskIfRunning(() -> {
            SteamUser current = user;
            if (current != null) current.cancelAuthTicket(ticket);
            return null;
        });
    }

    int nextConnectionId(long remoteSteamId) {
        return bridgeRegistry.nextConnectionId(remoteSteamId, ThreadLocalRandom.current());
    }

    SteamConnectionBridge registerClientBridge(
            long remoteSteamId,
            int connectionId,
            Socket socket,
            AutoCloseable activity
    ) throws IOException {
        verifyRunning();
        if (remoteSteamId == 0) {
            throw new IOException("Invalid host Steam ID: " + Long.toUnsignedString(remoteSteamId));
        }

        SteamConnectionBridge bridge = new SteamConnectionBridge(
                this,
                remoteSteamId,
                connectionId,
                socket,
                null,
                activity
        );
        // Minecraft writes its handshake as soon as it connects to localhost.
        // Keep those bytes behind OPEN until the host has acknowledged this exact
        // bridge generation. The bounded fallback preserves compatibility with an
        // older peer which accepted OPEN but omitted OPEN_ACK.
        bridge.waitForPeerReadyUntil(
                System.currentTimeMillis() + CLIENT_OPEN_ACK_FALLBACK_MILLIS
        );
        SteamBridgeRegistry.Key key = new SteamBridgeRegistry.Key(remoteSteamId, connectionId);
        SteamBridgeRegistry.Registration result = registerBridge(key, bridge);
        if (result != SteamBridgeRegistry.Registration.REGISTERED) {
            String reason;
            switch (result) {
                case CAPACITY:
                    reason = "Too many active Steam bridges";
                    break;
                case COLLISION:
                    reason = "Steam connection identifier collision";
                    break;
                case UNAVAILABLE:
                    reason = "Steam runtime stopped while opening the bridge";
                    break;
                default:
                    reason = "Could not register the Steam bridge";
            }
            throw new IOException(reason);
        }
        long reconnectDeadline = clientReconnectDeadlines.getOrDefault(remoteSteamId, 0L);
        boolean olderClientBridgeExists = bridgeRegistry.any(
                candidate -> candidate != bridge
                        && !candidate.isHostSide()
                        && candidate.remoteSteamId() == remoteSteamId
        );
        if (olderClientBridgeExists) {
            reconnectDeadline = Math.max(
                    reconnectDeadline,
                    System.currentTimeMillis() + CLIENT_RECONNECT_GRACE_MILLIS
            );
        }
        clientReconnectDeadlines.remove(remoteSteamId);
        if (reconnectDeadline > System.currentTimeMillis()) {
            bridge.delayOutboundUntil(reconnectDeadline);
            E4steamClient.LOGGER.info(
                    "Delaying Steam reconnect to {} until the previous peer session is closed",
                    Long.toUnsignedString(remoteSteamId)
            );
        }
        submitSteamTaskIfRunning(() -> {
            SteamLobbyManager current = lobbyManager;
            if (current != null) {
                current.clientBridgeOpened(remoteSteamId);
            }
            return null;
        });
        return bridge;
    }

    boolean sendOpen(SteamConnectionBridge bridge, byte[] token) {
        return enqueueControl(
                bridge.remoteSteamId(),
                bridge.connectionId(),
                SteamProtocol.encodeOpen(bridge.connectionId(), token),
                SteamOutboundQueue.Kind.OPEN,
                bridge
        );
    }

    boolean sendDedicatedOpen(
            SteamConnectionBridge bridge,
            SteamDedicatedAddress address,
            DedicatedClientAuth authentication
    ) {
        byte[] proof = authentication.takeProof();
        byte[] nonce = authentication.nonce();
        try {
            return enqueueControl(
                    bridge.remoteSteamId(),
                    bridge.connectionId(),
                    SteamProtocol.encodeDedicatedOpen(
                            bridge.connectionId(),
                            address.generation(),
                            nonce,
                            proof
                    ),
                    SteamOutboundQueue.Kind.DEDICATED_OPEN,
                    bridge
            );
        } finally {
            java.util.Arrays.fill(proof, (byte) 0);
            java.util.Arrays.fill(nonce, (byte) 0);
        }
    }

    private boolean sendOpenAck(SteamConnectionBridge bridge, VoiceChatUdpEndpoint endpoint) {
        return enqueueControl(
                bridge.remoteSteamId(),
                bridge.connectionId(),
                SteamProtocol.encodeOpenAck(bridge.connectionId(),
                        endpoint.clientPortMode(), endpoint.hostPort()),
                SteamOutboundQueue.Kind.OPEN_ACK,
                bridge
        );
    }

    private boolean sendBridgeReady(SteamConnectionBridge bridge) {
        return enqueueControl(
                bridge.remoteSteamId(),
                bridge.connectionId(),
                SteamProtocol.encodeBridgeReady(bridge.connectionId()),
                SteamOutboundQueue.Kind.BRIDGE_READY,
                bridge
        );
    }

    boolean sendAddonHello(SteamConnectionBridge bridge, byte[] packet) {
        return enqueueControl(bridge.remoteSteamId(), bridge.connectionId(), packet,
                SteamOutboundQueue.Kind.ADDON_HELLO, bridge);
    }

    boolean sendAddonFrame(SteamConnectionBridge bridge, byte[] packet, boolean reliable) {
        if (!reliable) {
            // SteamAddonProtocol performs its own replay protection. Unreliable
            // delivery still uses the reserved addon queue and Steam no-delay flag.
        }
        if (status != Status.RUNNING || isWorkerStopping() || bridge.isClosed()) return false;
        return outbound.offerAddonData(bridge.remoteSteamId(), bridge.connectionId(),
                packet, !reliable, bridge);
    }

    @Override
    public boolean sendData(SteamConnectionBridge bridge, byte[] payload) {
        return enqueueData(
                bridge,
                SteamProtocol.encodeData(bridge.connectionId(), payload)
        );
    }

    void sendDatagram(SteamUdpBridge bridge, byte[] payload) {
        SteamConnectionBridge owner = bridge.owner();
        byte[] packet = SteamProtocol.encodeDatagram(owner.connectionId(), payload);
        if (status != Status.RUNNING
                || isWorkerStopping()
                || bridge.isClosed()
                || owner.isClosed()) {
            return;
        }
        outbound.offerDatagram(owner.remoteSteamId(), owner.connectionId(), packet, owner);
    }

    private void startClientUdpBridge(SteamConnectionBridge owner, VoiceChatUdpEndpoint endpoint) {
        startUdpBridge(owner, endpoint.clientPort(owner.localPort()), false);
    }

    private void startHostUdpBridge(SteamConnectionBridge owner, VoiceChatUdpEndpoint endpoint) {
        startUdpBridge(owner, endpoint.hostPort(), true);
    }

    private void startUdpBridge(SteamConnectionBridge owner, int port, boolean hostSide) {
        if (port == 0 || owner.isClosed()) {
            return;
        }
        if (port < 1 || port > 65535) {
            E4steamClient.LOGGER.warn("UDP tunneling is disabled because port {} is invalid", port);
            return;
        }

        SteamBridgeRegistry.Key key = new SteamBridgeRegistry.Key(
                owner.remoteSteamId(),
                owner.connectionId()
        );
        if (bridgeRegistry.containsUdp(key)) {
            return;
        }
        SteamUdpBridge bridge = null;
        try {
            bridge = hostSide
                    ? SteamUdpBridge.host(this, owner, port)
                    : SteamUdpBridge.client(this, owner, port);
            SteamUdpBridge previous = bridgeRegistry.putUdpIfAbsent(key, bridge);
            if (previous != null || owner.isClosed()) {
                bridge.close();
                return;
            }
            bridge.start();
            E4steamClient.LOGGER.info(
                    "Opened {} UDP tunnel on port {} for Steam user {}",
                    hostSide ? "host" : "client",
                    port,
                    Long.toUnsignedString(owner.remoteSteamId())
            );
        } catch (IOException exception) {
            if (bridge != null) {
                bridge.close();
            }
            E4steamClient.LOGGER.warn(
                    "Could not open the optional UDP tunnel on port {}; Minecraft TCP will continue",
                    port,
                    exception
            );
        }
    }

    @Override
    public void closeUdpBridge(SteamConnectionBridge owner) {
        SteamUdpBridge udp = bridgeRegistry.removeUdp(
                new SteamBridgeRegistry.Key(owner.remoteSteamId(), owner.connectionId())
        );
        if (udp != null) {
            udp.close();
        }
    }

    @Override
    public boolean sendFin(SteamConnectionBridge bridge) {
        return enqueueControl(
                bridge.remoteSteamId(),
                bridge.connectionId(),
                SteamProtocol.encodeFin(bridge.connectionId()),
                SteamOutboundQueue.Kind.FIN,
                bridge
        );
    }

    @Override
    public boolean sendReset(SteamConnectionBridge bridge) {
        return enqueueControl(
                bridge.remoteSteamId(),
                bridge.connectionId(),
                SteamProtocol.encodeReset(bridge.connectionId()),
                SteamOutboundQueue.Kind.RESET,
                bridge
        );
    }

    private void sendStandaloneReset(long remoteSteamId, int connectionId) {
        enqueueControl(
                remoteSteamId,
                connectionId,
                SteamProtocol.encodeReset(connectionId),
                SteamOutboundQueue.Kind.RESET,
                null
        );
        synchronized (peerSessionLock) {
            if (!hasBridgeForRemote(remoteSteamId)) {
                idleSessionDeadlines.put(remoteSteamId, newIdleSessionDeadline());
            }
        }
    }

    @Override
    public void unregister(SteamConnectionBridge bridge) {
        SteamAddonHooks.bridgeClosed(bridge);
        if (bridge.isHostSide()) {
            AuthenticatedLoopbackPeer peer = authenticatedLoopbackPeers.get(bridge.localPort());
            if (peer != null && peer.bridge() == bridge) {
                authenticatedLoopbackPeers.remove(bridge.localPort(), peer);
            }
        }
        closeUdpBridge(bridge);
        purgeOutbound(bridge);
        boolean removed = false;
        boolean anotherBridgeExists = false;
        synchronized (peerSessionLock) {
            if (bridgeRegistry.remove(
                    new SteamBridgeRegistry.Key(bridge.remoteSteamId(), bridge.connectionId()),
                    bridge
            )) {
                removed = true;
                anotherBridgeExists = bridge.isHostSide()
                        ? hasBridgeForRemote(bridge.remoteSteamId())
                        : hasClientBridgeForRemote(bridge.remoteSteamId());
                if (!hasBridgeForRemote(bridge.remoteSteamId())) {
                    long now = System.currentTimeMillis();
                    idleSessionDeadlines.put(bridge.remoteSteamId(), newIdleSessionDeadline());
                    knownPeerSessionGate.defer(bridge.remoteSteamId(), now);
                }
            }
        }
        if (removed && !bridge.isHostSide()) {
            if (!anotherBridgeExists) {
                clientReconnectDeadlines.put(
                        bridge.remoteSteamId(),
                        System.currentTimeMillis() + CLIENT_RECONNECT_GRACE_MILLIS
                );
            }
            boolean finalAnotherBridgeExists = anotherBridgeExists;
            submitSteamTaskIfRunning(() -> {
                SteamLobbyManager current = lobbyManager;
                if (current != null) {
                    current.clientBridgeClosed(bridge.remoteSteamId(), finalAnotherBridgeExists);
                }
                return null;
            });
        }
        bridge.releaseActivity();
    }

    public void shutdown() {
        WorkerGeneration target;
        synchronized (lifecycleLock) {
            if (permanentlyShutdown) {
                return;
            }
            permanentlyShutdown = true;
            target = generation;
            if (target != null) {
                target.stopRequested.set(true);
                status = Status.STOPPING;
            }
        }

        SteamClientBridge.cancelPending();
        SteamDedicatedClientBridge.cancelPending();
        hostRegistration = null;
        for (SteamConnectionBridge bridge : bridgeRegistry.snapshot()) {
            bridge.close(false);
        }
        clearOutbound();
        pendingPeers.clear();
        knownPeerSessionGate.clear();
        idleSessionDeadlines.clear();

        Thread worker = target == null ? null : target.worker;
        if (worker != null) {
            worker.interrupt();
            if (worker != Thread.currentThread()) {
                try {
                    worker.join(2000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            status = Status.STOPPED;
        }
    }

    private WorkerGeneration ensureWorkerStarted() throws IOException {
        synchronized (lifecycleLock) {
            long deadline = System.currentTimeMillis() + START_TIMEOUT.toMillis();
            while (generation != null && generation.stopRequested.get()) {
                if (permanentlyShutdown) {
                    throw new IOException("Steam runtime has been shut down");
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new IOException("Timed out while waiting for the previous Steam runtime to stop");
                }
                try {
                    lifecycleLock.wait(Math.min(remaining, 250));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for Steam to restart", exception);
                }
            }
            if (permanentlyShutdown) {
                throw new IOException("Steam runtime has been shut down");
            }
            if (generation != null) {
                return generation;
            }

            failureCause = null;
            localSteamId = 0;
            status = Status.STARTING;
            WorkerGeneration created = new WorkerGeneration(++nextWorkerGenerationId);
            Thread worker = new Thread(() -> runWorker(created), "e4steam-steam-runtime");
            worker.setDaemon(true);
            created.worker = worker;
            generation = created;
            workerThread = worker;
            worker.start();
            return created;
        }
    }

    private void runWorker(WorkerGeneration currentGeneration) {
        Throwable workerFailure = null;
        try {
            initializeSteam();
            synchronized (lifecycleLock) {
                if (generation != currentGeneration || currentGeneration.stopRequested.get()) {
                    throw new IOException("Steam runtime was stopped during initialization");
                }
                status = Status.RUNNING;
            }
            currentGeneration.ready.complete(null);
            E4steamClient.LOGGER.info(
                    "Steam Networking Messages initialized as {} using App ID {}",
                    steamId(),
                    APP_ID
            );

            ByteBuffer sendBuffer = ByteBuffer.allocateDirect(SteamProtocol.MAX_PACKET_SIZE);
            ByteBuffer receiveBuffer = ByteBuffer.allocateDirect(SteamProtocol.MAX_ACCEPTED_STEAM_PACKET_SIZE);

            while (!currentGeneration.stopRequested.get()) {
                if (!steamLifecycle.isRunning()) {
                    throw new IOException("Steam disconnected while e4steam was active");
                }
                steamLifecycle.runCallbacks();
                SteamNetworkingSocketsP2PTransport activeDedicatedTransport = dedicatedTransport;
                if (activeDedicatedTransport != null) activeDedicatedTransport.runCallbacks();
                drainSteamTasks();
                acceptKnownPeerSessions(System.currentTimeMillis());
                drainOutbound(sendBuffer, currentGeneration.id);
                receivePackets(receiveBuffer);
                receiveDedicatedPackets(receiveBuffer);
                SteamAddonHooks.tick();
                cleanupPeerSessions();
                cleanupGracefulBridgeClosures(System.currentTimeMillis());
                SteamLobbyManager currentSocial = lobbyManager;
                if (currentSocial != null) {
                    currentSocial.cleanup(System.currentTimeMillis());
                }
                if (shouldStopForIdle(currentGeneration, System.currentTimeMillis())) {
                    break;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException exception) {
                    // Wake-ups are used for queued Steam tasks and lifecycle changes.
                }
            }
        } catch (Throwable throwable) {
            workerFailure = throwable;
            failureCause = throwable;
            synchronized (lifecycleLock) {
                status = Status.FAILED;
            }
            currentGeneration.ready.completeExceptionally(throwable);
            E4steamClient.LOGGER.error("Steam runtime failed", throwable);
        } finally {
            HostRegistration failedHost;
            synchronized (lifecycleLock) {
                failedHost = hostRegistration;
                hostRegistration = null;
            }
            ArrayList<SteamConnectionBridge> failedBridges = new ArrayList<>(bridgeRegistry.snapshot());
            for (SteamConnectionBridge bridge : failedBridges) {
                bridge.close(false);
            }
            // A bridge that had already queued RESET is closed but still
            // registered. Explicit unregistration is required here so its
            // capacity permit and optional Activity survive no restart.
            for (SteamConnectionBridge bridge : failedBridges) {
                unregister(bridge);
            }
            bridgeRegistry.clear();
            authenticatedLoopbackPeers.clear();
            clearOutbound();
            pendingPeers.clear();
            knownPeerSessionGate.clear();
            idleSessionDeadlines.clear();

            if (workerFailure != null && failedHost != null) {
                failedHost.owner().runtimeFailed(
                        workerFailure
                );
            }

            SteamLobbyManager currentSocial = lobbyManager;
            lobbyManager = null;
            if (currentSocial != null) {
                try {
                    currentSocial.close();
                } catch (Throwable ignored) {
                }
            }

            SteamNetworkingSocketsP2PTransport currentDedicatedTransport = dedicatedTransport;
            dedicatedTransport = null;
            if (currentDedicatedTransport != null) {
                try {
                    currentDedicatedTransport.close();
                } catch (Throwable ignored) {
                }
            }

            SteamNetworkingMessagesTransport currentTransport = transport;
            transport = null;
            if (currentTransport != null) {
                try {
                    currentTransport.close();
                } catch (Throwable ignored) {
                }
            }
            SteamUser currentUser = user;
            user = null;
            if (currentUser != null) {
                try {
                    currentUser.dispose();
                } catch (Throwable ignored) {
                }
            }
            SteamUtils currentUtils = utils;
            utils = null;
            if (currentUtils != null) {
                try {
                    currentUtils.dispose();
                } catch (Throwable ignored) {
                }
            }
            try {
                steamLifecycle.close();
            } catch (Throwable ignored) {
            }
            failPendingSteamTasks(workerFailure == null
                    ? new IOException("Steam runtime stopped")
                    : workerFailure);
            localSteamId = 0;
            Activity failedLaunchActivity = null;
            synchronized (lifecycleLock) {
                if (generation == currentGeneration) {
                    generation = null;
                    workerThread = null;
                }
                if (workerFailure != null) {
                    failedLaunchActivity = launchActivity;
                    launchActivity = null;
                    launchStartRequested.set(false);
                }
                if (workerFailure == null) {
                    status = Status.STOPPED;
                }
                lifecycleLock.notifyAll();
            }
            if (failedLaunchActivity != null) {
                failedLaunchActivity.close();
            }
        }
    }

    private boolean shouldStopForIdle(WorkerGeneration currentGeneration, long nowMillis) {
        synchronized (lifecycleLock) {
            if (permanentlyShutdown || generation != currentGeneration) {
                currentGeneration.stopRequested.set(true);
                status = Status.STOPPING;
                return true;
            }

            SteamLobbyManager currentSocial = lobbyManager;
            boolean keepAlive = activityCount > 0
                    || hostRegistration != null
                    || !bridgeRegistry.isEmpty()
                    || !outbound.isEmpty()
                    || !resetRetries.isEmpty()
                    || !idleSessionDeadlines.isEmpty()
                    || !steamTasks.isEmpty()
                    || (currentSocial != null && currentSocial.keepsRuntimeAlive());
            if (keepAlive) {
                currentGeneration.idleSinceMillis = 0;
                return false;
            }
            if (currentGeneration.idleSinceMillis == 0) {
                currentGeneration.idleSinceMillis = nowMillis;
                return false;
            }
            if (nowMillis - currentGeneration.idleSinceMillis < RUNTIME_IDLE_SHUTDOWN_MILLIS) {
                return false;
            }

            status = Status.STOPPING;
            currentGeneration.stopRequested.set(true);
            return true;
        }
    }

    private <T> CompletableFuture<T> submitSteamTask(Callable<T> action) throws IOException {
        SteamTask<T> task = new SteamTask<>(action);
        synchronized (lifecycleLock) {
            WorkerGeneration current = generation;
            if (current == null
                    || current.stopRequested.get()
                    || status != Status.RUNNING
                    || permanentlyShutdown) {
                throw new IOException("Steam runtime is not available for this operation");
            }
            steamTasks.add(task);
            current.idleSinceMillis = 0;
            current.worker.interrupt();
        }
        return task.result;
    }

    private <T> CompletableFuture<T> submitSteamTaskIfRunning(Callable<T> action) {
        try {
            return submitSteamTask(action);
        } catch (IOException exception) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }
    }

    private void drainSteamTasks() {
        for (int handled = 0; handled < 256; handled++) {
            SteamTask<?> task = steamTasks.poll();
            if (task == null) {
                return;
            }
            task.run();
        }
    }

    private void failPendingSteamTasks(Throwable cause) {
        SteamTask<?> task;
        while ((task = steamTasks.poll()) != null) {
            task.fail(cause);
        }
    }

    private static <T> T waitForSteamTask(
            CompletableFuture<T> task,
            Duration timeout,
            String operation
    ) throws IOException {
        try {
            return task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new IOException("Timed out while " + operation, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while " + operation, exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Steam failed while " + operation + ": " + cause.getMessage(), cause);
        }
    }

    private static void zero(ByteBuffer buffer) {
        if (buffer == null) return;
        buffer.clear();
        while (buffer.hasRemaining()) buffer.put((byte) 0);
        buffer.clear();
    }

    private void releaseActivity() {
        synchronized (lifecycleLock) {
            if (activityCount > 0) {
                activityCount--;
            }
            WorkerGeneration current = generation;
            if (current != null) {
                current.worker.interrupt();
            }
        }
    }

    boolean isOverlayEnabledOnWorker() {
        SteamUtils current = utils;
        return current != null && current.isOverlayEnabled();
    }

    private boolean isWorkerStopping() {
        WorkerGeneration current = generation;
        return permanentlyShutdown || current == null || current.stopRequested.get();
    }

    private void initializeSteam() throws Exception {
        ensureAppIdFile();

        steamLifecycle.start();

        SteamUtils createdUtils = new SteamUtils(new SteamUtilsCallback() {
        });
        int initializedAppId = createdUtils.getAppID();
        if (initializedAppId != APP_ID) {
            createdUtils.dispose();
            throw new IOException(
                    "Steam initialized the Minecraft process with App ID " + initializedAppId
                            + " instead of the required App ID " + APP_ID
            );
        }
        utils = createdUtils;

        SteamUser createdUser = new SteamUser(new SteamUserCallback() {
        });
        SteamID id = createdUser.getSteamID();
        if (id == null || !id.isValid()) {
            createdUser.dispose();
            throw new IOException("Steam returned an invalid user ID");
        }

        localSteamId = SteamNativeHandle.getNativeHandle(id);
        user = createdUser;
        transport = SteamNetworkingMessagesTransport.open(
                steamLifecycle.steamApiPath(),
                new SteamNetworkingMessagesTransport.SessionListener() {
                    @Override
                    public void onSessionRequest(long remoteId) {
                        E4steamClient.LOGGER.debug(
                                "Steam Networking Messages session requested by {}",
                                Long.toUnsignedString(remoteId)
                        );
                        SteamNetworkingMessagesTransport current = transport;
                        if (current == null) {
                            return;
                        }
                        synchronized (peerSessionLock) {
                            SteamLobbyManager currentSocial = lobbyManager;
                            boolean bridgeExists = hasBridgeForRemote(remoteId);
                            boolean hosting = hostRegistration != null;
                            boolean knownSocialPeer = currentSocial != null
                                    && currentSocial.mayAcceptPeer(remoteId);
                            // Accepting the Steam transport does not grant world access.
                            // handleOpen still validates the current secret token, social
                            // policy, live world and guest limit before opening localhost.
                            if (!shouldAcceptSessionRequest(
                                    bridgeExists, hosting, knownSocialPeer
                            )) {
                                current.closePeer(remoteId);
                                return;
                            }
                            if (!bridgeExists && pendingPeers.size() >= MAX_PENDING_PEERS) {
                                current.closePeer(remoteId);
                                return;
                            }
                            if (!current.accept(remoteId)) {
                                current.closePeer(remoteId);
                                return;
                            }
                            // A real callback identifies a new native session
                            // generation and may bypass the compatibility poll's
                            // post-close quarantine.
                            knownPeerSessionGate.observeNewSession(remoteId);
                            if (!bridgeExists) {
                                pendingPeers.put(
                                        remoteId,
                                        System.currentTimeMillis() + PENDING_PEER_TIMEOUT_MILLIS
                                );
                            }
                        }
                    }

                    @Override
                    public void onSessionFailed(long remoteId, int endReason, String detail) {
                        if (detail.trim().isEmpty()) {
                            E4steamClient.LOGGER.warn(
                                    "Steam Networking Messages session with {} failed (reason {})",
                                    Long.toUnsignedString(remoteId),
                                    endReason
                            );
                        } else {
                            E4steamClient.LOGGER.warn(
                                    "Steam Networking Messages session with {} failed (reason {}): {}",
                                    Long.toUnsignedString(remoteId),
                                    endReason,
                                    detail
                            );
                        }
                        ArrayList<SteamConnectionBridge> failedBridges = new ArrayList<>();
                        synchronized (peerSessionLock) {
                            pendingPeers.remove(remoteId);
                            idleSessionDeadlines.remove(remoteId);
                            knownPeerSessionGate.defer(remoteId, System.currentTimeMillis());
                            for (SteamConnectionBridge bridge : bridgeRegistry.snapshot()) {
                                if (bridge.remoteSteamId() == remoteId) {
                                    failedBridges.add(bridge);
                                }
                            }
                        }
                        SteamNetworkingMessagesTransport current = transport;
                        if (current != null) {
                            current.closePeer(remoteId);
                        }
                        for (SteamConnectionBridge bridge : failedBridges) {
                            bridge.close(false);
                        }
                    }
                }
        );
        lobbyManager = new SteamLobbyManager(this);
    }

    static boolean shouldAcceptSessionRequest(
            boolean bridgeExists,
            boolean hosting,
            boolean knownSocialPeer
    ) {
        return bridgeExists || hosting || knownSocialPeer;
    }

    static boolean autoRestartsBrokenSession(
            SteamOutboundQueue.Kind kind,
            boolean platformRequiresRestart
    ) {
        // A dedicated GameServer identity is short-lived and a previous
        // failed attempt can leave Steam's implicit Messages session in a
        // broken state. Always replace that stale route for the authenticated
        // dedicated handshake. Integrated-world traffic keeps the narrower
        // loader-specific workaround used by Forge.
        if (kind == SteamOutboundQueue.Kind.DEDICATED_OPEN
                || kind == SteamOutboundQueue.Kind.DEDICATED_OPEN_ACK) {
            return true;
        }
        return platformRequiresRestart
                && (kind == SteamOutboundQueue.Kind.OPEN
                || kind == SteamOutboundQueue.Kind.OPEN_ACK);
    }

    private void ensureAppIdFile() throws IOException {
        Path appIdFile = Paths.get(System.getProperty("user.dir"), "steam_appid.txt").toAbsolutePath().normalize();
        if (Files.exists(appIdFile)) {
            String value = new String(Files.readAllBytes(appIdFile), StandardCharsets.US_ASCII).trim();
            if (!Integer.toString(APP_ID).equals(value)) {
                throw new IOException(
                        "Refusing to overwrite " + appIdFile + "; expected App ID 480 but found '" + value + "'"
                );
            }
            return;
        }

        Files.write(
                appIdFile,
                (Integer.toString(APP_ID) + System.lineSeparator()).getBytes(StandardCharsets.US_ASCII),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );
        E4steamClient.LOGGER.info("Created {} for Steam App ID {}", appIdFile, APP_ID);
    }

    private void verifyRunning() throws IOException {
        if (status != Status.RUNNING || transport == null || isWorkerStopping()) {
            throw new IOException("Steam runtime is not running (status: " + status + ")");
        }
    }

    private boolean enqueueData(SteamConnectionBridge bridge, byte[] packet) {
        if (status != Status.RUNNING || isWorkerStopping() || bridge.isClosed()) {
            return false;
        }
        return outbound.offerData(
                bridge.remoteSteamId(),
                bridge.connectionId(),
                packet,
                bridge
        );
    }

    private boolean enqueueControl(
            long remoteSteamId,
            int connectionId,
            byte[] packet,
            SteamOutboundQueue.Kind kind,
            SteamConnectionBridge bridge
    ) {
        if (status != Status.RUNNING || isWorkerStopping()) {
            return false;
        }
        if (bridge != null && kind != SteamOutboundQueue.Kind.RESET && bridge.isClosed()) {
            return false;
        }
        return outbound.offerControl(remoteSteamId, connectionId, packet, kind, bridge);
    }

    private void drainOutbound(ByteBuffer buffer, long workerGeneration) throws Exception {
        SteamNetworkingMessagesTransport current = Objects.requireNonNull(transport);
        int resetWork = drainResetRetries(
                current,
                buffer,
                workerGeneration,
                MAX_RESET_RETRIES_PER_TICK
        );
        for (int sent = resetWork; sent < MAX_PACKETS_PER_TICK; sent++) {
            long now = System.currentTimeMillis();
            SteamOutboundQueue.Packet<SteamConnectionBridge> packet = retryOutboundPacket;
            if (packet != null && now < retryOutboundNotBeforeMillis) {
                return;
            }
            if (packet == null) {
                packet = outbound.poll();
            }
            if (packet == null) {
                return;
            }

            SteamConnectionBridge queuedBridge = packet.bridge();
            if (packet.kind() != SteamOutboundQueue.Kind.RESET
                    && queuedBridge != null
                    && !queuedBridge.isOutboundReady(now)) {
                if (retryOutboundPacket != packet) {
                    retryOutboundPacket = packet;
                    retryOutboundDeadlineMillis = now + OUTBOUND_SEND_RETRY_TIMEOUT_MILLIS;
                }
                retryOutboundNotBeforeMillis = now + 10;
                return;
            }
            if (packet.kind() == SteamOutboundQueue.Kind.DATA
                    && queuedBridge != null
                    && !queuedBridge.isOutboundDataReady(now)) {
                if (retryOutboundPacket != packet) {
                    retryOutboundPacket = packet;
                    retryOutboundDeadlineMillis = now + OUTBOUND_SEND_RETRY_TIMEOUT_MILLIS;
                }
                retryOutboundNotBeforeMillis = now + 10;
                return;
            }

            SteamBridgeRegistry.Key key = new SteamBridgeRegistry.Key(
                    packet.remoteSteamId(),
                    packet.connectionId()
            );
            SteamConnectionBridge currentBridge = bridgeRegistry.get(key);
            if (!isPacketCurrent(packet, currentBridge)) {
                clearRetriedPacket(packet);
                continue;
            }

            buffer.clear();
            buffer.put(packet.payload()).flip();
            boolean restartBrokenSession = autoRestartsBrokenSession(
                    packet.kind(),
                    Agnos.autoRestartBrokenSteamSessionForHandshake()
            );
            int result = sendTransportResult(
                    current,
                    packet.bridge(),
                    packet.remoteSteamId(),
                    buffer,
                    packet.kind() == SteamOutboundQueue.Kind.DATAGRAM
                            || packet.kind() == SteamOutboundQueue.Kind.ADDON_DATAGRAM,
                    restartBrokenSession
            );
            boolean accepted = result == 1;
            SteamConnectionBridge packetBridge = packet.bridge();
            if (accepted) {
                clearRetriedPacket(packet);
                if (packet.kind() == SteamOutboundQueue.Kind.DEDICATED_OPEN) {
                    E4steamClient.LOGGER.info(
                            "Submitted dedicated Steam authentication handshake"
                    );
                } else if (restartBrokenSession) {
                    E4steamClient.LOGGER.info(
                            "Submitted Forge Steam {} frame for bridge {}:{}",
                            packet.kind(),
                            Long.toUnsignedString(packet.remoteSteamId()),
                            Integer.toUnsignedString(packet.connectionId())
                    );
                }
                if (packet.kind() == SteamOutboundQueue.Kind.DATA
                        && packetBridge != null
                        && packetBridge.markFirstOutboundData()) {
                    E4steamClient.LOGGER.info(
                            "Sent first Minecraft DATA frame ({} bytes) to Steam user {}",
                            packet.payload().length,
                            Long.toUnsignedString(packet.remoteSteamId())
                    );
                }
                if (packet.kind() == SteamOutboundQueue.Kind.RESET && packetBridge != null) {
                    packetBridge.markResetSubmitted();
                } else if (packet.kind() == SteamOutboundQueue.Kind.FIN && packetBridge != null) {
                    packetBridge.markFinSubmitted();
                }
                continue;
            }

            if (packet.kind() == SteamOutboundQueue.Kind.DATAGRAM
                    || packet.kind() == SteamOutboundQueue.Kind.ADDON_DATAGRAM) {
                continue;
            }

            SteamResult failure = steamResult(result);
            if (isRetryableSendFailure(failure)) {
                if (packet.kind() == SteamOutboundQueue.Kind.RESET) {
                    SteamResetRetryQueue.Offer<SteamConnectionBridge> admitted =
                            resetRetries.offerAfterTemporaryFailure(
                                    packet.remoteSteamId(),
                                    packet.connectionId(),
                                    packet.payload(),
                                    packetBridge,
                                    workerGeneration,
                                    now
                            );
                    clearRetriedPacket(packet);
                    if (admitted.status() != SteamResetRetryQueue.OfferStatus.FULL) {
                        continue;
                    }
                    E4steamClient.LOGGER.warn(
                            "RESET retry capacity exhausted for Steam user {}; closing the stale bridge",
                            Long.toUnsignedString(packet.remoteSteamId())
                    );
                    if (packetBridge != null) {
                        packetBridge.markResetSubmitted();
                    }
                    continue;
                }
                if (retryOutboundPacket != packet) {
                    retryOutboundPacket = packet;
                    retryOutboundDeadlineMillis = now + OUTBOUND_SEND_RETRY_TIMEOUT_MILLIS;
                    E4steamClient.LOGGER.debug(
                            "Steam applied outbound backpressure for {}; preserving and retrying {}",
                            Long.toUnsignedString(packet.remoteSteamId()),
                            packet.kind()
                    );
                }
                if (now < retryOutboundDeadlineMillis) {
                    retryOutboundNotBeforeMillis = now + OUTBOUND_SEND_RETRY_DELAY_MILLIS;
                    return;
                }
                clearRetriedPacket(packet);
            }
            E4steamClient.LOGGER.warn(
                    "Steam Networking Messages send to {} failed for {}: {} ({})",
                    Long.toUnsignedString(packet.remoteSteamId()),
                    packet.kind(),
                    failure,
                    result
            );
            if (packetBridge != null) {
                if (packet.kind() == SteamOutboundQueue.Kind.RESET) {
                    // RESET is queued only after close() has already marked
                    // the bridge closed, so close(false) would be a no-op.
                    packetBridge.markResetSubmitted();
                } else {
                    packetBridge.close(false);
                }
            }
        }
    }

    private int sendTransportResult(
            SteamNetworkingMessagesTransport messages,
            SteamConnectionBridge bridge,
            long remoteSteamId,
            ByteBuffer payload,
            boolean unreliable,
            boolean restartBrokenSession
    ) throws IOException {
        if (bridge != null && bridge.dedicatedSessionGeneration() > 0L) {
            SteamNetworkingSocketsP2PTransport dedicated = dedicatedTransport;
            return dedicated == null
                    ? 3 // SteamResult.NoConnection
                    : dedicated.sendResult(remoteSteamId, payload, unreliable);
        }
        return messages.sendResult(
                remoteSteamId,
                payload,
                unreliable,
                restartBrokenSession,
                CHANNEL
        );
    }

    private int drainResetRetries(
            SteamNetworkingMessagesTransport current,
            ByteBuffer buffer,
            long workerGeneration,
            int limit
    ) throws Exception {
        int handled = 0;
        while (handled < limit) {
            long now = System.currentTimeMillis();
            SteamResetRetryQueue.Entry<SteamConnectionBridge> entry =
                    resetRetries.poll(workerGeneration, now);
            if (entry == null) {
                return handled;
            }
            handled++;

            if (entry.state() != SteamResetRetryQueue.State.AWAITING_SEND) {
                finishResetRetry(entry, entry.state(), null, 0);
                continue;
            }

            SteamBridgeRegistry.Key key = new SteamBridgeRegistry.Key(
                    entry.remoteSteamId(),
                    entry.connectionId()
            );
            SteamConnectionBridge currentBridge = bridgeRegistry.get(key);
            boolean currentConnection = entry.bridge() == null
                    ? currentBridge == null
                    : currentBridge == entry.bridge();
            if (!currentConnection) {
                SteamResetRetryQueue.State state = resetRetries.cancelStaleConnection(entry);
                finishResetRetry(entry, state, null, 0);
                continue;
            }

            buffer.clear();
            entry.putPayload(buffer);
            buffer.flip();
            int result = sendTransportResult(
                    current,
                    entry.bridge(),
                    entry.remoteSteamId(),
                    buffer,
                    false,
                    false
            );
            SteamResult failure = result == 1 ? null : steamResult(result);
            SteamResetRetryQueue.SendOutcome outcome = result == 1
                    ? SteamResetRetryQueue.SendOutcome.SUCCESS
                    : isRetryableSendFailure(failure)
                    ? SteamResetRetryQueue.SendOutcome.TEMPORARY_FAILURE
                    : SteamResetRetryQueue.SendOutcome.PERMANENT_FAILURE;
            SteamResetRetryQueue.State state = resetRetries.complete(entry, outcome, now);
            if (state != SteamResetRetryQueue.State.RETRY_SCHEDULED) {
                finishResetRetry(entry, state, failure, result);
            }
        }
        return handled;
    }

    private void finishResetRetry(
            SteamResetRetryQueue.Entry<SteamConnectionBridge> entry,
            SteamResetRetryQueue.State state,
            SteamResult failure,
            int resultCode
    ) {
        if (state == SteamResetRetryQueue.State.EXHAUSTED) {
            E4steamClient.LOGGER.warn(
                    "RESET retry budget exhausted for Steam user {} after {} attempts",
                    Long.toUnsignedString(entry.remoteSteamId()),
                    entry.attempts()
            );
        } else if (state == SteamResetRetryQueue.State.PERMANENT_FAILURE) {
            E4steamClient.LOGGER.warn(
                    "Steam permanently rejected RESET for user {}: {} ({})",
                    Long.toUnsignedString(entry.remoteSteamId()),
                    failure,
                    resultCode
            );
        }
        SteamConnectionBridge bridge = entry.bridge();
        if (bridge != null) {
            bridge.markResetSubmitted();
        }
    }

    private void acceptKnownPeerSessions(long now) {
        if (now < nextKnownPeerAcceptAtMillis) {
            return;
        }
        nextKnownPeerAcceptAtMillis = now + KNOWN_PEER_ACCEPT_INTERVAL_MILLIS;
        SteamLobbyManager currentSocial = lobbyManager;
        SteamNetworkingMessagesTransport currentTransport = transport;
        if (currentSocial == null || currentTransport == null) {
            return;
        }
        currentSocial.forEachKnownSessionPeer(remoteSteamId -> {
            synchronized (peerSessionLock) {
                if (!knownPeerSessionGate.mayProactivelyAccept(
                        remoteSteamId,
                        hasBridgeForRemote(remoteSteamId),
                        pendingPeers.containsKey(remoteSteamId),
                        pendingPeers.size() < MAX_PENDING_PEERS,
                        now
                )) {
                    return;
                }
                if (currentTransport.accept(remoteSteamId)) {
                    knownPeerSessionGate.observeNewSession(remoteSteamId);
                    pendingPeers.put(
                            remoteSteamId,
                            now + PENDING_PEER_TIMEOUT_MILLIS
                    );
                    E4steamClient.LOGGER.debug(
                            "Accepted Steam session for known lobby peer {}",
                            Long.toUnsignedString(remoteSteamId)
                    );
                }
            }
        });
    }

    private void clearOutbound() {
        outbound.clear();
        retryOutboundPacket = null;
        retryOutboundNotBeforeMillis = 0;
        retryOutboundDeadlineMillis = 0;
        ArrayList<SteamResetRetryQueue.Entry<SteamConnectionBridge>> cancelled =
                new ArrayList<>(resetRetries.cancelAll());
        for (SteamResetRetryQueue.Entry<SteamConnectionBridge> entry : cancelled) {
            SteamConnectionBridge bridge = entry.bridge();
            if (bridge != null) {
                bridge.markResetSubmitted();
            }
        }
    }

    private void purgeOutbound(SteamConnectionBridge bridge) {
        outbound.purge(bridge);
        resetRetries.purge(bridge);
        SteamOutboundQueue.Packet<SteamConnectionBridge> retry = retryOutboundPacket;
        if (retry != null && retry.bridge() == bridge) {
            clearRetriedPacket(retry);
        }
    }

    private void clearRetriedPacket(SteamOutboundQueue.Packet<SteamConnectionBridge> packet) {
        if (retryOutboundPacket == packet) {
            retryOutboundPacket = null;
            retryOutboundNotBeforeMillis = 0;
            retryOutboundDeadlineMillis = 0;
        }
    }

    static boolean isRetryableSendFailure(SteamResult result) {
        return result == SteamResult.LimitExceeded
                || result == SteamResult.Busy
                || result == SteamResult.NoConnection
                || result == SteamResult.ServiceUnavailable;
    }

    static SteamResult steamResult(int resultCode) {
        if (resultCode < 0) {
            return SteamResult.UnknownErrorCode_NotImplementedByAPI;
        }
        SteamResult result = SteamResult.byValue(resultCode);
        return result == null ? SteamResult.UnknownErrorCode_NotImplementedByAPI : result;
    }

    private boolean isPacketCurrent(
            SteamOutboundQueue.Packet<SteamConnectionBridge> packet,
            SteamConnectionBridge currentBridge
    ) {
        SteamConnectionBridge packetBridge = packet.bridge();
        if (packetBridge == null) {
            // A standalone RESET rejects an OPEN that never created a bridge.
            return packet.kind() == SteamOutboundQueue.Kind.RESET && currentBridge == null;
        }
        if (currentBridge != packetBridge) {
            return false;
        }
        return packet.kind() == SteamOutboundQueue.Kind.RESET || !packetBridge.isClosed();
    }

    private void receivePackets(ByteBuffer buffer) throws Exception {
        SteamNetworkingMessagesTransport current = Objects.requireNonNull(transport);
        for (int received = 0; received < MAX_PACKETS_PER_TICK; received++) {
            int size = current.availablePacketSize(CHANNEL);
            if (size == 0) {
                return;
            }

            if (size < 0) {
                throw new IOException("Steam reported an invalid P2P packet size: " + size);
            }
            if (size > SteamProtocol.MAX_ACCEPTED_STEAM_PACKET_SIZE) {
                current.discardPendingMessage();
                E4steamClient.LOGGER.debug(
                        "Discarded an oversized foreign Steam packet ({} bytes)",
                        size
                );
                continue;
            }

            buffer.clear();
            SteamNetworkingMessagesTransport.Received packet = current.receive(buffer, CHANNEL);
            int read = packet.size();
            if (read <= 0) {
                continue;
            }
            if (read > SteamProtocol.MAX_PACKET_SIZE) {
                continue; // Foreign App ID 480 traffic; consume and ignore it.
            }
            if (packet.remoteSteamId() == 0) {
                continue; // Steam API peers must have an authenticated Steam identity.
            }

            buffer.position(0);
            buffer.limit(read);
            SteamProtocol.Frame frame = SteamProtocol.decode(buffer);
            if (frame == null) {
                continue; // App ID 480 is shared, so unrelated traffic is expected.
            }
            dispatchFrame(packet.remoteSteamId(), frame);
        }
    }

    private void receiveDedicatedPackets(ByteBuffer buffer) throws Exception {
        SteamNetworkingSocketsP2PTransport current = dedicatedTransport;
        if (current == null) return;
        for (int received = 0; received < MAX_PACKETS_PER_TICK; received++) {
            int size = current.availablePacketSize();
            if (size == 0) return;
            if (size < 0 || size > SteamProtocol.MAX_ACCEPTED_STEAM_PACKET_SIZE) {
                current.discardPendingMessage();
                continue;
            }
            buffer.clear();
            SteamNetworkingSocketsP2PTransport.Received packet = current.receive(buffer);
            int read = packet.size();
            if (read <= 0 || read > SteamProtocol.MAX_PACKET_SIZE
                    || packet.remoteSteamId() == 0L) continue;
            buffer.position(0);
            buffer.limit(read);
            SteamProtocol.Frame frame = SteamProtocol.decode(buffer);
            if (frame != null) dispatchFrame(packet.remoteSteamId(), frame);
        }
    }

    private void dispatchFrame(long remoteSteamId, SteamProtocol.Frame frame) {
        SteamBridgeRegistry.Key key = new SteamBridgeRegistry.Key(remoteSteamId, frame.connectionId());
        boolean handshakeDiagnostics = Agnos.autoRestartBrokenSteamSessionForHandshake();
        if (handshakeDiagnostics
                && (frame.type() == SteamProtocol.OPEN || frame.type() == SteamProtocol.OPEN_ACK)) {
            E4steamClient.LOGGER.info(
                    "Received Steam {} frame for bridge {}:{}",
                    frame.type() == SteamProtocol.OPEN ? "OPEN" : "OPEN_ACK",
                    Long.toUnsignedString(remoteSteamId),
                    Integer.toUnsignedString(frame.connectionId())
            );
        }
        switch (frame.type()) {
            case SteamProtocol.OPEN:
                handleOpen(remoteSteamId, key, frame.payload());
                break;
            case SteamProtocol.OPEN_ACK:
                handleOpenAck(key, frame.payload());
                break;
            case SteamProtocol.DEDICATED_OPEN_ACK:
                handleDedicatedOpenAck(key, frame.payload());
                break;
            case SteamProtocol.BRIDGE_READY:
                handleBridgeReady(key);
                break;
            case SteamProtocol.ADDON_HELLO:
            case SteamProtocol.ADDON_DATA: {
                SteamConnectionBridge bridge = bridgeRegistry.get(key);
                if (bridge != null && !bridge.isClosed()) {
                    SteamAddonHooks.accept(
                            bridge, frame.type(), frame.payload());
                }
                break;
            }
            case SteamProtocol.DATA: {
                SteamConnectionBridge bridge = bridgeRegistry.get(key);
                if (bridge != null) {
                    if (bridge.markFirstInboundData()) {
                        E4steamClient.LOGGER.info(
                                "Received first Minecraft DATA frame ({} bytes) from Steam user {}",
                                frame.payload().length,
                                Long.toUnsignedString(remoteSteamId)
                        );
                    }
                    bridge.acceptSteamData(frame.payload());
                } else if (handshakeDiagnostics) {
                    E4steamClient.LOGGER.warn(
                            "Ignored DATA for unknown Steam bridge {}:{} ({} bytes)",
                            Long.toUnsignedString(remoteSteamId),
                            Integer.toUnsignedString(frame.connectionId()),
                            frame.payload().length
                    );
                }
                break;
            }
            case SteamProtocol.FIN: {
                SteamConnectionBridge bridge = bridgeRegistry.get(key);
                if (bridge != null) {
                    bridge.acceptRemoteFin();
                }
                break;
            }
            case SteamProtocol.RESET: {
                SteamConnectionBridge bridge = bridgeRegistry.get(key);
                if (bridge != null) {
                    bridge.resetFromRemote();
                }
                break;
            }
            case SteamProtocol.DATAGRAM: {
                SteamUdpBridge bridge = bridgeRegistry.getUdp(key);
                if (bridge != null) {
                    bridge.acceptSteamDatagram(frame.payload());
                }
                break;
            }
            default:
                break;
        }
    }

    private void handleOpenAck(SteamBridgeRegistry.Key key, byte[] payload) {
        SteamConnectionBridge bridge = bridgeRegistry.get(key);
        if (bridge == null || bridge.isHostSide() || bridge.isClosed()) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte clientPortMode = buffer.get();
        int hostPort = Short.toUnsignedInt(buffer.getShort());
        try {
            startClientUdpBridge(
                    bridge,
                    VoiceChatUdpEndpoint.fromHandshake(hostPort, clientPortMode)
            );
        } catch (IllegalArgumentException exception) {
            bridge.close(true);
            return;
        }
        if (!sendBridgeReady(bridge)) {
            E4steamClient.LOGGER.debug(
                    "Could not queue Steam bridge-ready confirmation for {}:{}; host fallback will be used",
                    Long.toUnsignedString(key.remoteSteamId()),
                    Integer.toUnsignedString(key.connectionId())
            );
        }
        bridge.markPeerReady();
    }

    private void handleDedicatedOpenAck(SteamBridgeRegistry.Key key, byte[] payload) {
        SteamConnectionBridge bridge = bridgeRegistry.get(key);
        if (bridge == null || bridge.isHostSide() || bridge.isClosed()
                || payload.length != Long.BYTES) return;
        long generation = ByteBuffer.wrap(payload).getLong();
        if (generation <= 0L || bridge.dedicatedSessionGeneration() != generation) {
            bridge.close(true);
            return;
        }
        SteamAddonHooks.bridgeReady(bridge);
        bridge.markPeerReady();
        bridge.start();
    }

    private void handleBridgeReady(SteamBridgeRegistry.Key key) {
        SteamConnectionBridge bridge = bridgeRegistry.get(key);
        if (bridge == null || !bridge.isHostSide() || bridge.isClosed()) {
            return;
        }
        bridge.markPeerReady();
        if (Agnos.autoRestartBrokenSteamSessionForHandshake()) {
            E4steamClient.LOGGER.info(
                    "Steam bridge is ready for Minecraft data {}:{}",
                    Long.toUnsignedString(key.remoteSteamId()),
                    Integer.toUnsignedString(key.connectionId())
            );
        }
    }

    private void handleOpen(long remoteSteamId, SteamBridgeRegistry.Key key, byte[] token) {
        HostRegistration registration = hostRegistration;
        SteamLobbyManager currentSocial = lobbyManager;
        boolean peerAllowed = registration != null
                && currentSocial != null
                && currentSocial.allows(registration.owner(), remoteSteamId);
        SteamInvitationAuthorizer.Decision authorization = SteamInvitationAuthorizer.authorize(
                registration == null ? null : registration.token(),
                token,
                peerAllowed
        );
        if (authorization != SteamInvitationAuthorizer.Decision.ALLOWED) {
            sendStandaloneReset(remoteSteamId, key.connectionId());
            return;
        }
        synchronized (peerSessionLock) {
            pendingPeers.remove(remoteSteamId);
            idleSessionDeadlines.remove(remoteSteamId);
        }
        if (bridgeRegistry.contains(key)) {
            return;
        }
        long activeHostConnections = 0;
        for (SteamConnectionBridge bridge : bridgeRegistry.snapshot()) {
            if (bridge.isHostedBy(registration.owner()) && !bridge.isClosed()) {
                activeHostConnections++;
            }
        }
        if (activeHostConnections >= SteamLobbyManager.VANILLA_MAX_GUESTS) {
            sendStandaloneReset(remoteSteamId, key.connectionId());
            return;
        }
        if (System.currentTimeMillis() < nextLoopbackConnectAttemptAtMillis) {
            sendStandaloneReset(remoteSteamId, key.connectionId());
            return;
        }

        Socket socket = new Socket();
        boolean handedOff = false;
        try {
            socket.connect(
                    new InetSocketAddress("127.0.0.1", registration.localPort()),
                    LOOPBACK_CONNECT_TIMEOUT_MILLIS
            );
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            nextLoopbackConnectAttemptAtMillis = 0;

            if (hostRegistration != registration || status != Status.RUNNING || isWorkerStopping()) {
                sendStandaloneReset(remoteSteamId, key.connectionId());
                return;
            }

            SteamConnectionBridge bridge = new SteamConnectionBridge(
                    this,
                    remoteSteamId,
                    key.connectionId(),
                    socket,
                    registration.owner(),
                    null
            );
            SteamBridgeRegistry.Registration result = registerBridge(key, bridge);
            if (result != SteamBridgeRegistry.Registration.REGISTERED) {
                if (result != SteamBridgeRegistry.Registration.COLLISION) {
                    sendStandaloneReset(remoteSteamId, key.connectionId());
                }
                return;
            }
            AuthenticatedLoopbackPeer authenticatedPeer = new AuthenticatedLoopbackPeer(
                    remoteSteamId,
                    bridge
            );
            AuthenticatedLoopbackPeer previousPeer = authenticatedLoopbackPeers.compute(
                    bridge.localPort(),
                    (port, existing) -> existing == null || existing.bridge().isClosed()
                            ? authenticatedPeer
                            : existing
            );
            if (previousPeer != null && previousPeer.bridge() != bridge) {
                bridge.close(true);
                E4steamClient.LOGGER.warn(
                        "Refused Steam bridge because localhost port {} is already authenticated",
                        bridge.localPort()
                );
                return;
            }
            handedOff = true;
            if (hostRegistration != registration) {
                bridge.close(true);
                return;
            }
            startHostUdpBridge(bridge, registration.udpEndpoint());
            // Do this on every loader. OPEN_ACK makes the client release its
            // Minecraft handshake; BRIDGE_READY makes the host release its
            // response. The bounded fallback keeps older 0.2.x peers usable.
            bridge.waitForPeerReadyUntil(
                    System.currentTimeMillis() + BRIDGE_READY_FALLBACK_MILLIS
            );
            if (!sendOpenAck(bridge, registration.udpEndpoint())) {
                bridge.close(true);
                return;
            }
            SteamAddonHooks.bridgeReady(bridge);
            bridge.start();
            E4steamClient.LOGGER.info(
                    "Accepted Steam bridge from {}",
                    Long.toUnsignedString(remoteSteamId)
            );
        } catch (IOException exception) {
            nextLoopbackConnectAttemptAtMillis =
                    System.currentTimeMillis() + LOOPBACK_FAILURE_BACKOFF_MILLIS;
            sendStandaloneReset(remoteSteamId, key.connectionId());
            E4steamClient.LOGGER.warn("Could not connect a Steam guest to the local LAN server", exception);
        } finally {
            if (!handedOff) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void closeHostBridges(SteamSession owner) {
        for (SteamConnectionBridge bridge : bridgeRegistry.snapshot()) {
            if (bridge.isHostedBy(owner)) {
                bridge.close(true);
            }
        }
    }

    void closeRemoteBridges(long remoteSteamId) {
        for (SteamConnectionBridge bridge : bridgeRegistry.snapshot()) {
            if (bridge.remoteSteamId() == remoteSteamId) {
                bridge.close(true);
            }
        }
    }

    private SteamBridgeRegistry.Registration registerBridge(
            SteamBridgeRegistry.Key key,
            SteamConnectionBridge bridge
    ) {
        synchronized (peerSessionLock) {
            SteamBridgeRegistry.Registration result = bridgeRegistry.register(
                    key,
                    bridge,
                    () -> status == Status.RUNNING && !isWorkerStopping()
            );
            if (result != SteamBridgeRegistry.Registration.REGISTERED) {
                return result;
            }
            pendingPeers.remove(key.remoteSteamId());
            knownPeerSessionGate.observeNewSession(key.remoteSteamId());
            idleSessionDeadlines.remove(key.remoteSteamId());
            return SteamBridgeRegistry.Registration.REGISTERED;
        }
    }

    private boolean hasBridgeForRemote(long remoteSteamId) {
        for (SteamConnectionBridge bridge : bridgeRegistry.snapshot()) {
            if (bridge.remoteSteamId() == remoteSteamId) {
                return true;
            }
        }
        return false;
    }

    boolean hasClientBridgeForRemote(long remoteSteamId) {
        for (SteamConnectionBridge bridge : bridgeRegistry.snapshot()) {
            if (!bridge.isHostSide() && bridge.remoteSteamId() == remoteSteamId) {
                return true;
            }
        }
        return false;
    }

    private void closeSteamSessionIfIdle(long remoteSteamId) {
        synchronized (peerSessionLock) {
            if (!hasBridgeForRemote(remoteSteamId)) {
                pendingPeers.remove(remoteSteamId);
                idleSessionDeadlines.remove(remoteSteamId);
                closeSteamSession(remoteSteamId);
            }
        }
    }

    private void cleanupPeerSessions() {
        long now = System.currentTimeMillis();
        pendingPeers.forEach((remoteSteamId, deadline) -> {
            if (deadline <= now) {
                synchronized (peerSessionLock) {
                    if (!hasBridgeForRemote(remoteSteamId)
                            && pendingPeers.remove(remoteSteamId, deadline)) {
                        closeSteamSession(remoteSteamId);
                    }
                }
            }
        });
        idleSessionDeadlines.forEach((remoteSteamId, deadline) -> {
            if (deadline[0] <= now) {
                synchronized (peerSessionLock) {
                    if (idleSessionDeadlines.get(remoteSteamId) != deadline) {
                        return;
                    }
                    if (hasBridgeForRemote(remoteSteamId)) {
                        idleSessionDeadlines.remove(remoteSteamId);
                    } else if (now < deadline[1]
                            && hasQueuedSteamPackets(remoteSteamId)) {
                        idleSessionDeadlines.put(
                                remoteSteamId,
                                new long[]{now + IDLE_SESSION_RECHECK_MILLIS, deadline[1]}
                        );
                    } else {
                        idleSessionDeadlines.remove(remoteSteamId);
                        closeSteamSession(remoteSteamId);
                    }
                }
            }
        });
    }

    private void cleanupGracefulBridgeClosures(long nowMillis) {
        for (SteamConnectionBridge bridge : bridgeRegistry.snapshot()) {
            bridge.closeIfGracefulCloseTimedOut(nowMillis);
        }
    }

    private long[] newIdleSessionDeadline() {
        long now = System.currentTimeMillis();
        return new long[]{
                now + IDLE_SESSION_CLOSE_DELAY_MILLIS,
                now + IDLE_SESSION_MAX_DRAIN_MILLIS
        };
    }

    private boolean hasQueuedSteamPackets(long remoteSteamId) {
        SteamNetworkingMessagesTransport current = transport;
        if (current == null) {
            return false;
        }
        return current.hasQueuedPackets(remoteSteamId);
    }

    private void closeSteamSession(long remoteSteamId) {
        knownPeerSessionGate.defer(remoteSteamId, System.currentTimeMillis());
        SteamNetworkingMessagesTransport current = transport;
        if (current != null) {
            current.closePeer(remoteSteamId);
        }
    }

    private enum Status {
        NEW,
        STARTING,
        RUNNING,
        STOPPING,
        FAILED,
        STOPPED
    }

    private static final class HostRegistration {
        private final SteamSession owner;
        private final int localPort;
        private final VoiceChatUdpEndpoint udpEndpoint;
        private final byte[] token;
        private final SteamAccessMode accessMode;

        private HostRegistration(
                SteamSession owner,
                int localPort,
                VoiceChatUdpEndpoint udpEndpoint,
                byte[] token,
                SteamAccessMode accessMode
        ) {
            this.owner = owner;
            this.localPort = localPort;
            this.udpEndpoint = udpEndpoint;
            this.token = token;
            this.accessMode = accessMode;
        }

        private SteamSession owner() { return owner; }
        private int localPort() { return localPort; }
        private VoiceChatUdpEndpoint udpEndpoint() { return udpEndpoint; }
        private byte[] token() { return token; }
        private SteamAccessMode accessMode() { return accessMode; }
    }

    private static final class AuthenticatedLoopbackPeer {
        private final long remoteSteamId;
        private final SteamConnectionBridge bridge;

        private AuthenticatedLoopbackPeer(long remoteSteamId, SteamConnectionBridge bridge) {
            this.remoteSteamId = remoteSteamId;
            this.bridge = bridge;
        }

        private long remoteSteamId() { return remoteSteamId; }
        private SteamConnectionBridge bridge() { return bridge; }
    }

    /** Immutable non-personal session projection for the internal Addon API adapter. */
    public static final class SafeSessionView {
        private final String sessionId;
        private final long generation;
        private final String roleCode;
        private final String stateCode;
        private final int capacity;
        private final List<SafePeerIdentity> peers;

        private SafeSessionView(String sessionId, long generation, String roleCode,
                                String stateCode, int capacity, List<SafePeerIdentity> peers) {
            this.sessionId = sessionId;
            this.generation = generation;
            this.roleCode = roleCode;
            this.stateCode = stateCode;
            this.capacity = capacity;
            this.peers = Collections.unmodifiableList(new ArrayList<>(peers));
        }

        private static SafeSessionView inactive() {
            return new SafeSessionView("", 0L, "NONE", "NONE", 0,
                    Collections.<SafePeerIdentity>emptyList());
        }

        public boolean active() { return generation > 0L; }
        public String sessionId() { return sessionId; }
        public long generation() { return generation; }
        public String roleCode() { return roleCode; }
        public String stateCode() { return stateCode; }
        public int capacity() { return capacity; }
        public List<SafePeerIdentity> peers() { return peers; }

        @Override public String toString() {
            return "SafeSessionView{generation=" + generation + ", role=" + roleCode
                    + ", state=" + stateCode + ", peers=" + peers.size() + '}';
        }
    }

    /** Immutable opaque peer projection; the SteamID is deliberately absent. */
    public static final class SafePeerIdentity {
        private final String opaquePeerId;
        private final UUID minecraftUuid;
        private final String minecraftName;

        private SafePeerIdentity(String opaquePeerId, UUID minecraftUuid, String minecraftName) {
            this.opaquePeerId = opaquePeerId;
            this.minecraftUuid = minecraftUuid;
            this.minecraftName = minecraftName;
        }

        public String opaquePeerId() { return opaquePeerId; }
        public UUID minecraftUuid() { return minecraftUuid; }
        public String minecraftName() { return minecraftName; }

        @Override public String toString() { return "SafePeerIdentity{opaque}"; }
    }

    /** Immutable local Minecraft projection with no platform identifier or persona data. */
    public static final class SafeMinecraftIdentity {
        private final UUID minecraftUuid;
        private final String minecraftName;

        private SafeMinecraftIdentity(UUID minecraftUuid, String minecraftName) {
            this.minecraftUuid = minecraftUuid;
            this.minecraftName = minecraftName;
        }

        public UUID minecraftUuid() { return minecraftUuid; }
        public String minecraftName() { return minecraftName; }
        @Override public String toString() { return "SafeMinecraftIdentity{uuid=" + minecraftUuid + '}'; }
    }

    /** A restart-safe lease that keeps Spacewar/Steamworks active while needed. */
    public static final class Activity implements AutoCloseable {
        private final SteamRuntime runtime;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Activity(SteamRuntime runtime) {
            this.runtime = runtime;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                runtime.releaseActivity();
            }
        }
    }

    private static final class WorkerGeneration {
        private final long id;
        private final CompletableFuture<Void> ready = new CompletableFuture<>();
        private final AtomicBoolean stopRequested = new AtomicBoolean();
        private volatile Thread worker;
        private long idleSinceMillis;

        private WorkerGeneration(long id) {
            this.id = id;
        }
    }

    private static final class SteamTask<T> {
        private final Callable<T> action;
        private final CompletableFuture<T> result = new CompletableFuture<>();

        private SteamTask(Callable<T> action) {
            this.action = action;
        }

        private void run() {
            try {
                result.complete(action.call());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        }

        private void fail(Throwable throwable) {
            result.completeExceptionally(throwable);
        }
    }

    static final class DedicatedClientAuth implements AutoCloseable {
        private final SteamRuntime runtime;
        private final SteamAuthTicket handle;
        private final AtomicBoolean closed = new AtomicBoolean();
        private byte[] proof;
        private final byte[] nonce;

        private DedicatedClientAuth(
                SteamRuntime runtime,
                SteamAuthTicket handle,
                byte[] proof,
                byte[] nonce
        ) {
            this.runtime = runtime;
            this.handle = handle;
            this.proof = proof;
            this.nonce = nonce;
        }

        synchronized byte[] takeProof() {
            byte[] current = proof;
            proof = null;
            return current == null ? new byte[0] : current;
        }

        byte[] nonce() { return nonce.clone(); }

        @Override public synchronized void close() {
            if (!closed.compareAndSet(false, true)) return;
            if (proof != null) {
                java.util.Arrays.fill(proof, (byte) 0);
                proof = null;
            }
            java.util.Arrays.fill(nonce, (byte) 0);
            runtime.cancelDedicatedAuthTicket(handle);
        }

        @Override public String toString() {
            return "DedicatedClientAuth{credentials=redacted}";
        }
    }
}
