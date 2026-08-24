package link.e4steam.steam;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Connection-oriented Steam P2P transport used only by dedicated servers.
 *
 * <p>Integrated worlds continue to use {@link SteamNetworkingMessagesTransport}.
 * A dedicated server has a clear listener/client relationship, for which Valve
 * exposes CreateListenSocketP2P and ConnectP2P.</p>
 */
final class SteamNetworkingSocketsP2PTransport implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger("e4steam");

    private static final int VIRTUAL_PORT = 480;
    private static final int INVALID_HANDLE = 0;
    private static final int RESULT_OK = 1;
    private static final int RESULT_NO_CONNECTION = 3;
    private static final int SEND_NO_NAGLE = 1;
    private static final int SEND_NO_DELAY = 4;
    private static final int SEND_RELIABLE = 8;
    private static final int SEND_UNRELIABLE_NO_DELAY = SEND_NO_NAGLE | SEND_NO_DELAY;
    private static final int SEND_RELIABLE_NO_NAGLE = SEND_RELIABLE | SEND_NO_NAGLE;

    private static final int STATE_CONNECTING = 1;
    private static final int STATE_FINDING_ROUTE = 2;
    private static final int STATE_CONNECTED = 3;
    private static final int STATE_CLOSED_BY_PEER = 4;
    private static final int STATE_PROBLEM_DETECTED_LOCALLY = 5;

    private static final int RECEIVE_BATCH_SIZE = 32;
    private static final long MESSAGE_DATA_OFFSET = 0;
    private static final long MESSAGE_SIZE_OFFSET = 8;
    private static final long MESSAGE_CONNECTION_OFFSET = 12;
    private static final long MESSAGE_IDENTITY_OFFSET = 16;

    // SteamNetConnectionInfo_t uses the Steamworks 8-byte packing contract.
    private static final int CONNECTION_INFO_SIZE = 768;
    private static final long CONNECTION_INFO_STATE_OFFSET = 176;
    private static final long CONNECTION_INFO_END_REASON_OFFSET = 180;
    private static final long CONNECTION_INFO_END_DEBUG_OFFSET = 184;
    private static final int CONNECTION_INFO_END_DEBUG_SIZE = 128;

    private static final int REAL_TIME_STATUS_SIZE = 128;
    private static final long STATUS_STATE_OFFSET = 0;
    private static final long STATUS_PENDING_UNRELIABLE_OFFSET = 36;
    private static final long STATUS_PENDING_RELIABLE_OFFSET = 40;
    private static final long STATUS_SENT_UNACKED_RELIABLE_OFFSET = 44;

    static final class Received {
        private final long remoteSteamId;
        private final int size;

        private Received(long remoteSteamId, int size) {
            this.remoteSteamId = remoteSteamId;
            this.size = size;
        }

        long remoteSteamId() { return remoteSteamId; }
        int size() { return size; }
    }

    interface SessionListener {
        void onSessionRequest(long remoteSteamId);
        void onSessionFailed(long remoteSteamId, int endReason, String detail);
    }

    interface ConnectionStatusCallback extends Callback {
        void invoke(Pointer statusChanged);
    }

    private interface FlatApi extends Library {
        Pointer SteamAPI_SteamNetworkingSockets_SteamAPI_v012();
        Pointer SteamAPI_SteamGameServerNetworkingSockets_SteamAPI_v012();
        Pointer SteamAPI_SteamNetworkingUtils_SteamAPI_v004();

        int SteamAPI_ISteamNetworkingSockets_CreateListenSocketP2P(
                Pointer self, int localVirtualPort, int optionCount, Pointer options);

        int SteamAPI_ISteamNetworkingSockets_ConnectP2P(
                Pointer self, Pointer remoteIdentity, int remoteVirtualPort,
                int optionCount, Pointer options);

        int SteamAPI_ISteamNetworkingSockets_AcceptConnection(Pointer self, int connection);
        byte SteamAPI_ISteamNetworkingSockets_CloseConnection(
                Pointer self, int connection, int reason, String detail, byte linger);
        byte SteamAPI_ISteamNetworkingSockets_CloseListenSocket(Pointer self, int listenSocket);

        int SteamAPI_ISteamNetworkingSockets_CreatePollGroup(Pointer self);
        byte SteamAPI_ISteamNetworkingSockets_DestroyPollGroup(Pointer self, int pollGroup);
        byte SteamAPI_ISteamNetworkingSockets_SetConnectionPollGroup(
                Pointer self, int connection, int pollGroup);

        int SteamAPI_ISteamNetworkingSockets_SendMessageToConnection(
                Pointer self, int connection, Pointer data, int size, int flags,
                Pointer outputMessageNumber);

        int SteamAPI_ISteamNetworkingSockets_ReceiveMessagesOnConnection(
                Pointer self, int connection, Pointer[] messages, int maxMessages);

        int SteamAPI_ISteamNetworkingSockets_ReceiveMessagesOnPollGroup(
                Pointer self, int pollGroup, Pointer[] messages, int maxMessages);

        byte SteamAPI_ISteamNetworkingSockets_GetConnectionInfo(
                Pointer self, int connection, Pointer connectionInfo);

        int SteamAPI_ISteamNetworkingSockets_GetConnectionRealTimeStatus(
                Pointer self, int connection, Pointer status, int laneCount, Pointer laneStatus);

        void SteamAPI_ISteamNetworkingSockets_RunCallbacks(Pointer self);

        void SteamAPI_ISteamNetworkingUtils_InitRelayNetworkAccess(Pointer self);
        void SteamAPI_ISteamNetworkingUtils_SetGlobalCallback_SteamNetConnectionStatusChanged(
                Pointer self, ConnectionStatusCallback callback);
        void SteamAPI_SteamNetworkingMessage_t_Release(Pointer message);
    }

    private final FlatApi api;
    private final Pointer sockets;
    private final Pointer utils;
    private final SessionListener listener;
    private final boolean gameServer;
    private final long clientRemoteSteamId;
    private final ConnectionStatusCallback statusCallback = this::handleConnectionStatus;
    private final ArrayDeque<Pointer> pendingMessages = new ArrayDeque<>(RECEIVE_BATCH_SIZE);
    private final Map<Long, Integer> connectionsByPeer = new HashMap<>();
    private final Map<Integer, Long> peersByConnection = new HashMap<>();
    private final Map<Long, Integer> pendingConnectionsByPeer = new HashMap<>();
    private final Memory connectionInfo = new Memory(CONNECTION_INFO_SIZE);
    private final Memory realTimeStatus = new Memory(REAL_TIME_STATUS_SIZE);

    private int listenSocket;
    private int pollGroup;
    private int clientConnection;
    private boolean closed;

    static SteamNetworkingSocketsP2PTransport openClient(
            Path steamApiLibrary,
            long remoteSteamId,
            SessionListener listener
    ) throws IOException {
        if (remoteSteamId == 0L) throw new IOException("Dedicated Steam ID is invalid");
        return new SteamNetworkingSocketsP2PTransport(
                steamApiLibrary, false, remoteSteamId, listener);
    }

    static SteamNetworkingSocketsP2PTransport openGameServer(
            Path steamApiLibrary,
            SessionListener listener
    ) throws IOException {
        return new SteamNetworkingSocketsP2PTransport(
                steamApiLibrary, true, 0L, listener);
    }

    @SuppressWarnings("deprecation")
    private SteamNetworkingSocketsP2PTransport(
            Path steamApiLibrary,
            boolean gameServer,
            long clientRemoteSteamId,
            SessionListener listener
    ) throws IOException {
        Objects.requireNonNull(steamApiLibrary, "steamApiLibrary");
        this.gameServer = gameServer;
        this.clientRemoteSteamId = clientRemoteSteamId;
        this.listener = Objects.requireNonNull(listener, "listener");
        try {
            api = (FlatApi) Native.loadLibrary(
                    steamApiLibrary.toAbsolutePath().normalize().toString(), FlatApi.class);
            sockets = gameServer
                    ? api.SteamAPI_SteamGameServerNetworkingSockets_SteamAPI_v012()
                    : api.SteamAPI_SteamNetworkingSockets_SteamAPI_v012();
            utils = api.SteamAPI_SteamNetworkingUtils_SteamAPI_v004();
        } catch (UnsatisfiedLinkError | RuntimeException failure) {
            throw new IOException("Could not bind Steam Networking Sockets", failure);
        }
        if (sockets == null || utils == null) {
            throw new IOException("Steam Networking Sockets is unavailable after Steam initialization");
        }

        boolean initialized = false;
        try {
            api.SteamAPI_ISteamNetworkingUtils_SetGlobalCallback_SteamNetConnectionStatusChanged(
                    utils, statusCallback);
            api.SteamAPI_ISteamNetworkingUtils_InitRelayNetworkAccess(utils);
            if (gameServer) {
                pollGroup = api.SteamAPI_ISteamNetworkingSockets_CreatePollGroup(sockets);
                if (pollGroup == INVALID_HANDLE) {
                    throw new IOException("Steam could not create a dedicated P2P poll group");
                }
                listenSocket = api.SteamAPI_ISteamNetworkingSockets_CreateListenSocketP2P(
                        sockets, VIRTUAL_PORT, 0, null);
                if (listenSocket == INVALID_HANDLE) {
                    throw new IOException("Steam could not create a dedicated P2P listen socket");
                }
                LOGGER.info("Dedicated Steam P2P listen socket is ready");
            } else {
                Memory identity = SteamNetworkingMessagesTransport.newIdentity(clientRemoteSteamId);
                clientConnection = api.SteamAPI_ISteamNetworkingSockets_ConnectP2P(
                        sockets, identity, VIRTUAL_PORT, 0, null);
                if (clientConnection == INVALID_HANDLE) {
                    throw new IOException("Steam rejected the dedicated P2P connection request");
                }
                connectionsByPeer.put(clientRemoteSteamId, clientConnection);
                peersByConnection.put(clientConnection, clientRemoteSteamId);
                LOGGER.info("Started a dedicated Steam P2P connection");
            }
            initialized = true;
        } finally {
            if (!initialized) close();
        }
    }

    synchronized boolean accept(long remoteSteamId) {
        if (closed || !gameServer) return false;
        Integer connection = pendingConnectionsByPeer.remove(remoteSteamId);
        if (connection == null) {
            return connectionsByPeer.containsKey(remoteSteamId);
        }
        int result = api.SteamAPI_ISteamNetworkingSockets_AcceptConnection(sockets, connection);
        if (result != RESULT_OK) {
            closeConnection(connection, "e4steam rejected an invalid pending connection");
            return false;
        }
        if (api.SteamAPI_ISteamNetworkingSockets_SetConnectionPollGroup(
                sockets, connection, pollGroup) == 0) {
            closeConnection(connection, "e4steam could not assign the connection poll group");
            return false;
        }
        Integer previous = connectionsByPeer.put(remoteSteamId, connection);
        peersByConnection.put(connection, remoteSteamId);
        if (previous != null && previous != connection) {
            peersByConnection.remove(previous);
            closeConnection(previous, "e4steam replaced a stale connection");
        }
        return true;
    }

    synchronized int sendResult(long remoteSteamId, ByteBuffer payload, boolean unreliable)
            throws IOException {
        ensureOpen();
        Integer connection = connectionsByPeer.get(remoteSteamId);
        if (connection == null) return RESULT_NO_CONNECTION;
        if (!payload.isDirect()) {
            throw new IOException("Steam Networking Sockets requires a direct send buffer");
        }
        Pointer data = Native.getDirectBufferPointer(payload);
        if (data == null) throw new IOException("Could not access the Steam send buffer");
        data = data.share(payload.position());
        int flags = unreliable ? SEND_UNRELIABLE_NO_DELAY : SEND_RELIABLE_NO_NAGLE;
        return api.SteamAPI_ISteamNetworkingSockets_SendMessageToConnection(
                sockets, connection, data, payload.remaining(), flags, null);
    }

    synchronized boolean send(long remoteSteamId, ByteBuffer payload, boolean unreliable)
            throws IOException {
        return sendResult(remoteSteamId, payload, unreliable) == RESULT_OK;
    }

    synchronized int availablePacketSize() throws IOException {
        ensureOpen();
        if (pendingMessages.isEmpty()) {
            Pointer[] messages = new Pointer[RECEIVE_BATCH_SIZE];
            int count;
            if (gameServer) {
                count = api.SteamAPI_ISteamNetworkingSockets_ReceiveMessagesOnPollGroup(
                        sockets, pollGroup, messages, RECEIVE_BATCH_SIZE);
            } else {
                count = api.SteamAPI_ISteamNetworkingSockets_ReceiveMessagesOnConnection(
                        sockets, clientConnection, messages, RECEIVE_BATCH_SIZE);
            }
            if (count < 0 || count > RECEIVE_BATCH_SIZE) {
                throw new IOException("Steam returned an invalid P2P message count: " + count);
            }
            for (int index = 0; index < count; index++) {
                if (messages[index] == null) {
                    releasePendingMessages();
                    throw new IOException("Steam returned a null P2P message");
                }
                pendingMessages.addLast(messages[index]);
            }
        }
        Pointer message = pendingMessages.peekFirst();
        return message == null ? 0 : message.getInt(MESSAGE_SIZE_OFFSET);
    }

    synchronized Received receive(ByteBuffer target) throws IOException {
        ensureOpen();
        Pointer message = pendingMessages.pollFirst();
        if (message == null) throw new IOException("No dedicated Steam packet is available");
        try {
            int size = message.getInt(MESSAGE_SIZE_OFFSET);
            if (size < 0) throw new IOException("Steam returned a negative P2P message size");
            if (size > target.remaining()) throw new BufferOverflowException();
            Pointer data = message.getPointer(MESSAGE_DATA_OFFSET);
            if (size > 0 && data == null) throw new IOException("Steam returned a null P2P payload");
            if (size > 0) target.put(data.getByteBuffer(0, size));
            long remoteSteamId = SteamNetworkingMessagesTransport.readSteamId(
                    message.share(MESSAGE_IDENTITY_OFFSET));
            if (remoteSteamId == 0L) {
                int connection = message.getInt(MESSAGE_CONNECTION_OFFSET);
                Long mapped = peersByConnection.get(connection);
                remoteSteamId = mapped == null ? clientRemoteSteamId : mapped;
            }
            return new Received(remoteSteamId, size);
        } finally {
            api.SteamAPI_SteamNetworkingMessage_t_Release(message);
        }
    }

    synchronized void discardPendingMessage() {
        Pointer message = pendingMessages.pollFirst();
        if (message != null) api.SteamAPI_SteamNetworkingMessage_t_Release(message);
    }

    synchronized boolean hasQueuedPackets(long remoteSteamId) {
        if (closed) return false;
        Integer connection = connectionsByPeer.get(remoteSteamId);
        if (connection == null) return false;
        realTimeStatus.clear();
        int result = api.SteamAPI_ISteamNetworkingSockets_GetConnectionRealTimeStatus(
                sockets, connection, realTimeStatus, 0, null);
        if (result != RESULT_OK) return false;
        int state = realTimeStatus.getInt(STATUS_STATE_OFFSET);
        return state == STATE_CONNECTING
                || state == STATE_FINDING_ROUTE
                || realTimeStatus.getInt(STATUS_PENDING_UNRELIABLE_OFFSET) > 0
                || realTimeStatus.getInt(STATUS_PENDING_RELIABLE_OFFSET) > 0
                || realTimeStatus.getInt(STATUS_SENT_UNACKED_RELIABLE_OFFSET) > 0;
    }

    /**
     * Dispatches callbacks owned by this exact sockets interface.
     *
     * <p>SteamAPI_RunCallbacks normally dispatches these notifications too, but the
     * anonymous GameServer interface used by a headless server can have a separate
     * callback pipe. Calling ISteamNetworkingSockets::RunCallbacks is the documented
     * interface-local path and is required for accepting an incoming ConnectP2P
     * request reliably.</p>
     */
    synchronized void runCallbacks() {
        if (closed) return;
        api.SteamAPI_ISteamNetworkingSockets_RunCallbacks(sockets);

        // Also inspect the outbound handle directly. This preserves a useful failure
        // reason even on Steam client builds that do not dispatch the global callback.
        if (!gameServer && clientConnection != INVALID_HANDLE) {
            connectionInfo.clear();
            if (api.SteamAPI_ISteamNetworkingSockets_GetConnectionInfo(
                    sockets, clientConnection, connectionInfo) != 0) {
                int state = connectionInfo.getInt(CONNECTION_INFO_STATE_OFFSET);
                if (state == STATE_CLOSED_BY_PEER
                        || state == STATE_PROBLEM_DETECTED_LOCALLY || state < 0) {
                    int connection = clientConnection;
                    long remoteSteamId = SteamNetworkingMessagesTransport.readSteamId(connectionInfo);
                    if (remoteSteamId == 0L) remoteSteamId = clientRemoteSteamId;
                    int reason = connectionInfo.getInt(CONNECTION_INFO_END_REASON_OFFSET);
                    String detail = readFixedString(
                            connectionInfo, CONNECTION_INFO_END_DEBUG_OFFSET,
                            CONNECTION_INFO_END_DEBUG_SIZE);
                    forgetConnection(connection, remoteSteamId);
                    try {
                        listener.onSessionFailed(remoteSteamId, reason, detail);
                    } catch (Throwable ignored) {
                    }
                    closeConnection(connection, "e4steam released a failed connection");
                }
            }
        }
    }

    synchronized void closePeer(long remoteSteamId) {
        Integer connection = connectionsByPeer.remove(remoteSteamId);
        Integer pending = pendingConnectionsByPeer.remove(remoteSteamId);
        if (connection != null) {
            peersByConnection.remove(connection);
            closeConnection(connection, "e4steam peer session closed");
        }
        if (pending != null && !pending.equals(connection)) {
            peersByConnection.remove(pending);
            closeConnection(pending, "e4steam pending session rejected");
        }
        if (!gameServer && remoteSteamId == clientRemoteSteamId) clientConnection = 0;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (api == null || sockets == null || utils == null) return;
        try {
            api.SteamAPI_ISteamNetworkingUtils_SetGlobalCallback_SteamNetConnectionStatusChanged(
                    utils, null);
        } catch (Throwable ignored) {
        }
        releasePendingMessages();
        for (Integer connection : peersByConnection.keySet().toArray(new Integer[0])) {
            closeConnection(connection, "e4steam transport stopped");
        }
        peersByConnection.clear();
        connectionsByPeer.clear();
        pendingConnectionsByPeer.clear();
        if (listenSocket != INVALID_HANDLE) {
            try { api.SteamAPI_ISteamNetworkingSockets_CloseListenSocket(sockets, listenSocket); }
            catch (Throwable ignored) { }
            listenSocket = INVALID_HANDLE;
        }
        if (pollGroup != INVALID_HANDLE) {
            try { api.SteamAPI_ISteamNetworkingSockets_DestroyPollGroup(sockets, pollGroup); }
            catch (Throwable ignored) { }
            pollGroup = INVALID_HANDLE;
        }
        clientConnection = INVALID_HANDLE;
    }

    private synchronized void handleConnectionStatus(Pointer callback) {
        if (closed || callback == null) return;
        int connection = callback.getInt(0);
        if (connection == INVALID_HANDLE) return;
        connectionInfo.clear();
        if (api.SteamAPI_ISteamNetworkingSockets_GetConnectionInfo(
                sockets, connection, connectionInfo) == 0) return;

        long remoteSteamId = SteamNetworkingMessagesTransport.readSteamId(connectionInfo);
        if (remoteSteamId == 0L && !gameServer) remoteSteamId = clientRemoteSteamId;
        int state = connectionInfo.getInt(CONNECTION_INFO_STATE_OFFSET);
        if (gameServer && state == STATE_CONNECTING && remoteSteamId != 0L
                && !connectionsByPeer.containsKey(remoteSteamId)) {
            pendingConnectionsByPeer.put(remoteSteamId, connection);
            peersByConnection.put(connection, remoteSteamId);
            try {
                listener.onSessionRequest(remoteSteamId);
            } catch (Throwable failure) {
                pendingConnectionsByPeer.remove(remoteSteamId);
                peersByConnection.remove(connection);
                closeConnection(connection, "e4steam admission callback failed");
            }
            return;
        }
        if (state == STATE_CONNECTED) {
            if (remoteSteamId != 0L) {
                connectionsByPeer.put(remoteSteamId, connection);
                peersByConnection.put(connection, remoteSteamId);
            }
            LOGGER.info(gameServer
                    ? "Dedicated Steam P2P peer connected"
                    : "Connected to the dedicated Steam P2P server");
            return;
        }
        if (state == STATE_CLOSED_BY_PEER || state == STATE_PROBLEM_DETECTED_LOCALLY
                || state < 0) {
            int reason = connectionInfo.getInt(CONNECTION_INFO_END_REASON_OFFSET);
            String detail = readFixedString(
                    connectionInfo, CONNECTION_INFO_END_DEBUG_OFFSET,
                    CONNECTION_INFO_END_DEBUG_SIZE);
            forgetConnection(connection, remoteSteamId);
            try {
                listener.onSessionFailed(remoteSteamId, reason, detail);
            } catch (Throwable ignored) {
            }
            closeConnection(connection, "e4steam released a failed connection");
        }
    }

    private void forgetConnection(int connection, long remoteSteamId) {
        peersByConnection.remove(connection);
        if (remoteSteamId != 0L) {
            connectionsByPeer.remove(remoteSteamId, connection);
            pendingConnectionsByPeer.remove(remoteSteamId, connection);
        }
        if (!gameServer && clientConnection == connection) clientConnection = 0;
    }

    private void closeConnection(int connection, String detail) {
        if (connection == INVALID_HANDLE) return;
        try {
            api.SteamAPI_ISteamNetworkingSockets_CloseConnection(
                    sockets, connection, 0, detail, (byte) 0);
        } catch (Throwable ignored) {
        }
    }

    private void releasePendingMessages() {
        Pointer message;
        while ((message = pendingMessages.pollFirst()) != null) {
            try { api.SteamAPI_SteamNetworkingMessage_t_Release(message); }
            catch (Throwable ignored) { }
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) throw new IOException("Dedicated Steam P2P transport is closed");
    }

    private static String readFixedString(Pointer source, long offset, int maximumSize) {
        byte[] bytes = source.getByteArray(offset, maximumSize);
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) length++;
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }
}
