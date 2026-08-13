package link.e4steam.steam;

import com.codedisaster.steamworks.SteamAuth;
import com.codedisaster.steamworks.SteamGameServer;
import com.codedisaster.steamworks.SteamGameServerAPI;
import com.codedisaster.steamworks.SteamGameServerCallback;
import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamNativeHandle;
import com.codedisaster.steamworks.SteamResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Headless Steam GameServer context. It never initializes a user Steam API,
 * never asks for a personal account and keeps master-server advertising off.
 */
public final class SteamGameServerRuntimeBackend implements SteamRuntimeBackend {
    private static final Logger LOGGER = LogManager.getLogger("e4steam");
    private static final long START_TIMEOUT_MILLIS = 30_000L;
    private static final long AUTH_TIMEOUT_MILLIS = 15_000L;
    private static final int MAX_AUTH_TICKET_BYTES = 4_096;

    private final StateListener stateListener;
    private final ThreadPoolExecutor control = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32),
            daemonFactory("e4steam-gameserver-control"), new ThreadPoolExecutor.AbortPolicy()
    );
    private final AtomicReference<State> state = new AtomicReference<>(State.OFF);
    private final AtomicBoolean startRequested = new AtomicBoolean();
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final ConcurrentHashMap<Long, PendingAuth> pendingAuth = new ConcurrentHashMap<>();
    private final Set<Long> authenticatedPeers = ConcurrentHashMap.newKeySet();
    private final CompletableFuture<RuntimeReady> readiness = new CompletableFuture<>();

    private volatile String failureCategory = "";
    private volatile Config config;
    private volatile SteamProcessGuard.Lease processLease;
    private volatile SteamNativeLibraryLoader nativeLoader;
    private volatile SteamGameServer gameServer;
    private volatile SteamNetworkingMessagesTransport transport;
    private volatile Thread callbackThread;
    private volatile long startupDeadline;
    private volatile PeerListener peerListener = PeerListener.REJECT_ALL;

    public SteamGameServerRuntimeBackend(StateListener stateListener) {
        this.stateListener = stateListener == null ? (state, category) -> { } : stateListener;
    }

    @Override public RuntimeKind kind() {
        return RuntimeKind.DEDICATED_GAME_SERVER;
    }

    @Override public CompletionStage<RuntimeReady> start(Config requested) {
        if (requested == null) throw new NullPointerException("config");
        if (!startRequested.compareAndSet(false, true)) {
            return readiness.thenApply(value -> value);
        }
        config = requested;
        transition(State.CONFIG_VALIDATED, "");
        if (!submitControl(this::startInternal)) {
            fail("CONTROL_QUEUE_FULL", null);
        }
        return readiness.thenApply(value -> value);
    }

    @Override public Snapshot snapshot() {
        SteamProcessGuard.Lease lease = processLease;
        return new Snapshot(state.get(), lease == null ? 0L : lease.generation(), failureCategory);
    }

    @Override public CompletionStage<Void> stop(ShutdownReason reason) {
        CompletableFuture<Void> stopped = new CompletableFuture<>();
        if (stopRequested.compareAndSet(false, true)) {
            if (!submitControl(() -> {
                stopInternal(false);
                stopped.complete(null);
            })) {
                stopInternal(false);
                stopped.complete(null);
            }
        } else {
            stopped.complete(null);
        }
        return stopped.thenApply(value -> null);
    }

    public void peerListener(PeerListener listener) {
        peerListener = listener == null ? PeerListener.REJECT_ALL : listener;
    }

    public CompletionStage<Boolean> authenticate(
            long remoteSteamId,
            byte[] authTicket,
            long expectedGeneration
    ) {
        if (remoteSteamId == 0L || authTicket == null || authTicket.length == 0
                || authTicket.length > MAX_AUTH_TICKET_BYTES) {
            return CompletableFuture.completedFuture(false);
        }
        SteamProcessGuard.Lease lease = processLease;
        SteamGameServer server = gameServer;
        if (state.get() != State.TRANSPORT_READY || lease == null
                || lease.generation() != expectedGeneration || server == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (pendingAuth.size() >= config.maxPeers() * 2
                || authenticatedPeers.size() >= config.maxPeers()
                || authenticatedPeers.contains(remoteSteamId)) {
            return CompletableFuture.completedFuture(false);
        }
        PendingAuth pending = new PendingAuth(expectedGeneration,
                System.currentTimeMillis() + AUTH_TIMEOUT_MILLIS);
        if (pendingAuth.putIfAbsent(remoteSteamId, pending) != null) {
            return CompletableFuture.completedFuture(false);
        }
        ByteBuffer direct = ByteBuffer.allocateDirect(authTicket.length);
        direct.put(authTicket).flip();
        Arrays.fill(authTicket, (byte) 0);
        try {
            SteamAuth.BeginAuthSessionResult result = server.beginAuthSession(
                    direct,
                    SteamID.createFromNativeHandle(remoteSteamId)
            );
            zero(direct);
            if (result != SteamAuth.BeginAuthSessionResult.OK) {
                pendingAuth.remove(remoteSteamId, pending);
                pending.result.complete(false);
            }
        } catch (Exception | LinkageError failure) {
            zero(direct);
            pendingAuth.remove(remoteSteamId, pending);
            pending.result.complete(false);
        }
        return pending.result.thenApply(value -> value);
    }

    public void endAuthentication(long remoteSteamId) {
        PendingAuth pending = pendingAuth.remove(remoteSteamId);
        if (pending != null) pending.result.complete(false);
        boolean authenticated = authenticatedPeers.remove(remoteSteamId);
        if (pending == null && !authenticated) return;
        SteamGameServer server = gameServer;
        if (server != null) {
            try {
                server.endAuthSession(SteamID.createFromNativeHandle(remoteSteamId));
            } catch (RuntimeException ignored) {
            }
        }
    }

    public boolean isAuthenticated(long remoteSteamId, long expectedGeneration) {
        SteamProcessGuard.Lease lease = processLease;
        return lease != null && lease.generation() == expectedGeneration
                && authenticatedPeers.contains(remoteSteamId);
    }

    public boolean send(long remoteSteamId, ByteBuffer payload, boolean unreliable, int channel)
            throws IOException {
        SteamNetworkingMessagesTransport active = requireTransport();
        return active.send(remoteSteamId, payload, unreliable, channel);
    }

    public int availablePacketSize(int channel) throws IOException {
        return requireTransport().availablePacketSize(channel);
    }

    public ReceivedPacket receive(ByteBuffer target, int channel) throws IOException {
        SteamNetworkingMessagesTransport.Received received =
                requireTransport().receive(target, channel);
        return new ReceivedPacket(received.remoteSteamId(), received.size());
    }

    public void closePeer(long remoteSteamId) {
        endAuthentication(remoteSteamId);
        SteamNetworkingMessagesTransport active = transport;
        if (active != null) active.closePeer(remoteSteamId);
    }

    private void startInternal() {
        try {
            Config requested = config;
            if (!requested.anonymousLogin()) {
                throw new IOException("GSLT is not enabled for the App ID 480 backend");
            }
            ensureAppIdFile(requested.appId());
            processLease = SteamProcessGuard.acquire(SteamProcessGuard.Context.GAME_SERVER);
            nativeLoader = new SteamNativeLibraryLoader();
            if (!SteamGameServerAPI.loadLibraries(nativeLoader)) {
                throw new IOException("GameServer native binding could not be loaded");
            }
            transition(State.NATIVES_READY, "");
            transition(State.STEAM_INITIALIZING, "");
            if (!SteamGameServerAPI.init(
                    0,
                    (short) requested.gamePort(),
                    (short) requested.queryPort(),
                    SteamGameServerAPI.ServerMode.Authentication,
                    "0.3.0"
            )) {
                throw new IOException("Steam GameServer initialization was rejected");
            }
            SteamGameServer created = new SteamGameServer(new Callback());
            gameServer = created;
            created.setProduct("e4steam");
            created.setGameDescription("e4steam Minecraft");
            created.setModDir("e4steam");
            created.setDedicatedServer(true);
            created.setMaxPlayerCount(requested.maxPeers());
            created.setBotPlayerCount(0);
            created.setServerName(requested.serverName());
            created.setMapName("minecraft");
            created.setPasswordProtected(false);
            created.setGameTags("private,e4steam");
            created.setAdvertiseServerActive(false);
            startupDeadline = System.currentTimeMillis() + START_TIMEOUT_MILLIS;
            startCallbackThread();
            transition(State.STEAM_LOGGING_ON, "");
            created.logOnAnonymous();
        } catch (Throwable failure) {
            fail(category(failure, "GAMESERVER_START_FAILED"), failure);
        }
    }

    private void onSteamConnected() {
        if (stopRequested.get() || state.get() != State.STEAM_LOGGING_ON) return;
        try {
            SteamNativeLibraryLoader loader = nativeLoader;
            if (loader == null) throw new IOException("Native loader is unavailable");
            SteamNetworkingMessagesTransport created =
                    SteamNetworkingMessagesTransport.openGameServer(
                            loader.steamApiPath(),
                            new TransportListener()
                    );
            transport = created;
            SteamProcessGuard.Lease lease = processLease;
            long serverSteamId = SteamNativeHandle.getNativeHandle(
                    SteamGameServerAPI.getSteamID()
            );
            if (lease == null || serverSteamId == 0L) {
                throw new IOException("Steam GameServer identity is unavailable");
            }
            transition(State.TRANSPORT_READY, "");
            readiness.complete(new RuntimeReady(lease.generation(), serverSteamId));
        } catch (Throwable failure) {
            fail(category(failure, "TRANSPORT_START_FAILED"), failure);
        }
    }

    private void startCallbackThread() {
        Thread thread = new Thread(() -> {
            while (!stopRequested.get()) {
                try {
                    SteamGameServerAPI.runCallbacks();
                    expirePendingAuth();
                    if (state.get() == State.STEAM_LOGGING_ON
                            && System.currentTimeMillis() >= startupDeadline) {
                        submitControl(() -> fail("STEAM_LOGON_TIMEOUT", null));
                        return;
                    }
                    Thread.sleep(15L);
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Throwable failure) {
                    submitControl(() -> fail("STEAM_CALLBACK_FAILED", failure));
                    return;
                }
            }
        }, "e4steam-gameserver-callbacks");
        thread.setDaemon(true);
        callbackThread = thread;
        thread.start();
    }

    private void expirePendingAuth() {
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<Long, PendingAuth> entry : pendingAuth.entrySet()) {
            PendingAuth pending = entry.getValue();
            if (now >= pending.deadline && pendingAuth.remove(entry.getKey(), pending)) {
                pending.result.complete(false);
                SteamGameServer server = gameServer;
                if (server != null) {
                    try {
                        server.endAuthSession(SteamID.createFromNativeHandle(entry.getKey()));
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }
    }

    private void validated(long steamId, SteamAuth.AuthSessionResponse response) {
        PendingAuth pending = pendingAuth.remove(steamId);
        if (pending == null) return;
        SteamProcessGuard.Lease lease = processLease;
        boolean valid = response == SteamAuth.AuthSessionResponse.OK
                && lease != null && lease.generation() == pending.generation
                && state.get() == State.TRANSPORT_READY
                && authenticatedPeers.size() < config.maxPeers();
        if (valid) authenticatedPeers.add(steamId);
        else {
            SteamGameServer server = gameServer;
            if (server != null) {
                try {
                    server.endAuthSession(SteamID.createFromNativeHandle(steamId));
                } catch (RuntimeException ignored) {
                }
            }
        }
        pending.result.complete(valid);
    }

    private SteamNetworkingMessagesTransport requireTransport() throws IOException {
        SteamNetworkingMessagesTransport active = transport;
        if (active == null || state.get() != State.TRANSPORT_READY) {
            throw new IOException("Dedicated Steam transport is not ready");
        }
        return active;
    }

    private void fail(String category, Throwable failure) {
        failureCategory = category == null ? "GAMESERVER_FAILED" : category;
        if (!readiness.isDone()) {
            readiness.completeExceptionally(new IOException(failureCategory));
        }
        stopRequested.set(true);
        stopInternal(true);
        transition(State.FAILED, failureCategory);
        if (failure != null) {
            LOGGER.warn("Dedicated Steam backend failed [{}]", failureCategory);
        }
    }

    private void stopInternal(boolean failed) {
        State current = state.get();
        if (!failed && current != State.OFF && current != State.STOPPED) {
            transition(State.DRAINING, "");
        }
        stopRequested.set(true);
        Thread callbacks = callbackThread;
        callbackThread = null;
        if (callbacks != null && callbacks != Thread.currentThread()) callbacks.interrupt();

        SteamNetworkingMessagesTransport activeTransport = transport;
        transport = null;
        if (activeTransport != null) {
            try { activeTransport.close(); } catch (RuntimeException ignored) { }
        }
        SteamGameServer server = gameServer;
        gameServer = null;
        for (Long steamId : new ArrayList<>(authenticatedPeers)) {
            if (server != null) {
                try { server.endAuthSession(SteamID.createFromNativeHandle(steamId)); }
                catch (RuntimeException ignored) { }
            }
        }
        authenticatedPeers.clear();
        for (java.util.Map.Entry<Long, PendingAuth> entry : pendingAuth.entrySet()) {
            entry.getValue().result.complete(false);
            if (server != null) {
                try { server.endAuthSession(SteamID.createFromNativeHandle(entry.getKey())); }
                catch (RuntimeException ignored) { }
            }
        }
        pendingAuth.clear();
        if (server != null) {
            try { server.setAdvertiseServerActive(false); } catch (RuntimeException ignored) { }
            try { server.logOff(); } catch (RuntimeException ignored) { }
            try { server.dispose(); } catch (RuntimeException ignored) { }
        }
        if (processLease != null) {
            try { SteamGameServerAPI.shutdown(); } catch (RuntimeException ignored) { }
        }
        SteamProcessGuard.Lease lease = processLease;
        processLease = null;
        if (lease != null) lease.close();
        if (!failed) transition(State.STOPPED, "");
        control.shutdownNow();
    }

    private void transition(State next, String category) {
        state.set(next);
        try { stateListener.onState(next, category == null ? "" : category); }
        catch (RuntimeException ignored) { }
    }

    private boolean submitControl(Runnable action) {
        try {
            control.execute(action);
            return true;
        } catch (RejectedExecutionException rejected) {
            return false;
        }
    }

    private static void ensureAppIdFile(int appId) throws IOException {
        Path path = Paths.get(System.getProperty("user.dir", "."), "steam_appid.txt")
                .toAbsolutePath().normalize();
        if (Files.exists(path)) {
            String current = new String(Files.readAllBytes(path), StandardCharsets.US_ASCII).trim();
            if (!Integer.toString(appId).equals(current)) {
                throw new IOException("Existing steam_appid.txt contains another App ID");
            }
            return;
        }
        Files.write(path, (Integer.toString(appId) + System.lineSeparator())
                        .getBytes(StandardCharsets.US_ASCII),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void zero(ByteBuffer buffer) {
        if (buffer == null) return;
        buffer.clear();
        while (buffer.hasRemaining()) buffer.put((byte) 0);
        buffer.clear();
    }

    private static String category(Throwable failure, String fallback) {
        if (failure instanceof UnsatisfiedLinkError || failure instanceof LinkageError) {
            return "NATIVE_BINDING_FAILED";
        }
        if (failure instanceof SecurityException) return "FILESYSTEM_POLICY_REJECTED";
        if (failure instanceof IOException) return fallback;
        return "GAMESERVER_INTERNAL_FAILURE";
    }

    private static ThreadFactory daemonFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    public interface PeerListener {
        PeerListener REJECT_ALL = new PeerListener() {
            @Override public boolean onSessionRequest(long remoteSteamId) { return false; }
            @Override public void onSessionFailed(long remoteSteamId, int reason, String detail) { }
        };
        boolean onSessionRequest(long remoteSteamId);
        void onSessionFailed(long remoteSteamId, int reason, String detail);
    }

    public static final class ReceivedPacket {
        private final long remoteSteamId;
        private final int size;
        private ReceivedPacket(long remoteSteamId, int size) {
            this.remoteSteamId = remoteSteamId;
            this.size = size;
        }
        public long remoteSteamId() { return remoteSteamId; }
        public int size() { return size; }
    }

    private final class TransportListener
            implements SteamNetworkingMessagesTransport.SessionListener {
        @Override public void onSessionRequest(long remoteSteamId) {
            boolean allowed;
            try {
                allowed = state.get() == State.TRANSPORT_READY
                        && peerListener.onSessionRequest(remoteSteamId);
            } catch (RuntimeException failure) {
                allowed = false;
            }
            SteamNetworkingMessagesTransport active = transport;
            if (active == null || !allowed || !active.accept(remoteSteamId)) {
                if (active != null) active.closePeer(remoteSteamId);
            }
        }

        @Override public void onSessionFailed(long remoteSteamId, int reason, String detail) {
            endAuthentication(remoteSteamId);
            try { peerListener.onSessionFailed(remoteSteamId, reason, "steam-session-failed"); }
            catch (RuntimeException ignored) { }
        }
    }

    private final class Callback implements SteamGameServerCallback {
        @Override public void onValidateAuthTicketResponse(
                SteamID steamID,
                SteamAuth.AuthSessionResponse response,
                SteamID ownerSteamID
        ) {
            long value = SteamNativeHandle.getNativeHandle(steamID);
            submitControl(() -> validated(value, response));
        }
        @Override public void onSteamServersConnected() {
            submitControl(SteamGameServerRuntimeBackend.this::onSteamConnected);
        }
        @Override public void onSteamServerConnectFailure(SteamResult result, boolean retrying) {
            if (!retrying) submitControl(() -> fail("STEAM_LOGON_FAILED", null));
        }
        @Override public void onSteamServersDisconnected(SteamResult result) {
            submitControl(() -> fail("STEAM_DISCONNECTED", null));
        }
        @Override public void onClientApprove(SteamID steamID, SteamID ownerSteamID) { }
        @Override public void onClientDeny(
                SteamID steamID,
                SteamGameServer.DenyReason reason,
                String optionalText
        ) { }
        @Override public void onClientKick(SteamID steamID, SteamGameServer.DenyReason reason) { }
        @Override public void onClientGroupStatus(
                SteamID steamID,
                SteamID group,
                boolean member,
                boolean officer
        ) { }
        @Override public void onAssociateWithClanResult(SteamResult result) { }
        @Override public void onComputeNewPlayerCompatibilityResult(
                SteamResult result,
                int playersThatDontLikeCandidate,
                int playersThatCandidateDoesntLike,
                int clanPlayersThatDontLikeCandidate,
                SteamID candidate
        ) { }
    }

    private static final class PendingAuth {
        private final long generation;
        private final long deadline;
        private final CompletableFuture<Boolean> result = new CompletableFuture<>();
        private PendingAuth(long generation, long deadline) {
            this.generation = generation;
            this.deadline = deadline;
        }
    }
}
