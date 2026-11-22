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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayDeque;

/**
 * Packet-oriented transport backed by ISteamNetworkingMessages, the
 * connectionless facade over Steam Networking Sockets.
 */
final class SteamNetworkingMessagesTransport implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger("e4steam");
    private static final int STEAM_IDENTITY_TYPE = 16;
    private static final int STEAM_IDENTITY_SIZE = 136;
    private static final int STEAM_IDENTITY_VALUE_SIZE = Long.BYTES;

    private static final int RESULT_OK = 1;
    private static final int CONFIG_SEND_RATE_MIN = 10;
    private static final int CONFIG_SEND_RATE_MAX = 11;
    // Steam's SendRateMax=0 means no upper rate clamp. Keep congestion feedback active.
    private static final int BULK_RATE_INITIAL = 2*1024*1024, BULK_RATE_MAX = 0;
    private long bulkPeer,nextBulkTune,nextBulkDecrease;
    private Integer savedRateMin,savedRateMax;
    private boolean bulkUnavailable;
    private int bulkFloor,bulkGoodSamples,bulkBadSamples,bulkBaselinePing;
    private boolean bulkHadCongestion;
    private static final int SEND_RATE_CEILING = 4 * 1024 * 1024;
    private static final int SEND_NO_NAGLE = 1;
    private static final int SEND_NO_DELAY = 4;
    private static final int SEND_RELIABLE = 8;
    private static final int SEND_AUTO_RESTART_BROKEN_SESSION = 32;
    private static final int SEND_UNRELIABLE_NO_DELAY = SEND_NO_NAGLE | SEND_NO_DELAY;
    private static final int SEND_RELIABLE_NO_NAGLE = SEND_RELIABLE | SEND_NO_NAGLE;

    private static final int CONNECTION_STATE_CONNECTING = 1;
    private static final int CONNECTION_STATE_FINDING_ROUTE = 2;

    private static final long IDENTITY_TYPE_OFFSET = 0;
    private static final long IDENTITY_SIZE_OFFSET = 4;
    private static final long IDENTITY_VALUE_OFFSET = 8;

    private static final long MESSAGE_DATA_OFFSET = 0;
    private static final long MESSAGE_SIZE_OFFSET = 8;
    private static final long MESSAGE_IDENTITY_OFFSET = 16;

    private static final long STATUS_PENDING_UNRELIABLE_OFFSET = 36;
    private static final long STATUS_PENDING_RELIABLE_OFFSET = 40;
    private static final long STATUS_SENT_UNACKED_RELIABLE_OFFSET = 44;
    private static final long STATUS_QUEUE_TIME_OFFSET = 48;
    private static final long REAL_TIME_STATUS_SIZE = 128;

    private static final long CONNECTION_INFO_END_REASON_OFFSET = 176;
    private static final long CONNECTION_INFO_END_DEBUG_OFFSET = 180;
    private static final int CONNECTION_INFO_END_DEBUG_SIZE = 128;

    static final class Received {
        private final long remoteSteamId;
        private final int size;

        Received(long remoteSteamId, int size) {
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

    interface SessionRequestCallback extends Callback {
        void invoke(Pointer request);
    }

    interface SessionFailedCallback extends Callback {
        void invoke(Pointer failure);
    }

    interface DebugOutputCallback extends Callback {
        void invoke(int type, String message);
    }

    interface NativeAccess {
        default boolean setGlobalConfigInt32(int option, int value) { return false; }
        default Integer getGlobalConfigInt32(int option) { return null; }
        int send(Pointer identity, Pointer data, int size, int flags, int channel);

        int receive(int channel, Pointer[] messages, int maxMessages);

        boolean accept(Pointer identity);

        void closeSession(Pointer identity);

        int getSessionConnectionInfo(Pointer identity, Pointer realTimeStatus);

        void initializeRelayNetworkAccess();

        void releaseMessage(Pointer message);

        void setCallbacks(SessionRequestCallback request, SessionFailedCallback failure);

        void setDebugOutput(int detailLevel, DebugOutputCallback callback);
    }

    private final NativeAccess nativeAccess;
    private final SessionListener listener;
    private final Map<Long, Memory> identities = new HashMap<>();
    private final Memory realTimeStatus = new Memory(REAL_TIME_STATUS_SIZE);
    private final SessionRequestCallback requestCallback = this::handleSessionRequest;
    private final SessionFailedCallback failedCallback = this::handleSessionFailed;
    private final DebugOutputCallback debugOutputCallback = this::handleDebugOutput;

    private static final int RECEIVE_BATCH_SIZE = 32;
    private final ArrayDeque<Pointer> pendingMessages = new ArrayDeque<>(RECEIVE_BATCH_SIZE);
    private boolean closed;

    static SteamNetworkingMessagesTransport open(Path steamApiLibrary, SessionListener listener)
            throws IOException {
        return new SteamNetworkingMessagesTransport(
                new JnaNativeAccess(steamApiLibrary, false),
                listener
        );
    }

    static SteamNetworkingMessagesTransport openGameServer(
            Path steamApiLibrary,
            SessionListener listener
    ) throws IOException {
        return new SteamNetworkingMessagesTransport(
                new JnaNativeAccess(steamApiLibrary, true),
                listener
        );
    }

    SteamNetworkingMessagesTransport(NativeAccess nativeAccess, SessionListener listener) {
        this.nativeAccess = Objects.requireNonNull(nativeAccess, "nativeAccess");
        this.listener = Objects.requireNonNull(listener, "listener");
        try {
            // Only raise the ceiling, never the minimum: Steam retains congestion
            // control on slower routes. Apply before any peer session is created.
            boolean configured = nativeAccess.setGlobalConfigInt32(CONFIG_SEND_RATE_MAX, SEND_RATE_CEILING);
            LOGGER.info("Steam send-rate ceiling: requested={} bytes/s, applied={}", SEND_RATE_CEILING, configured);
            nativeAccess.setCallbacks(requestCallback, failedCallback);
            nativeAccess.setDebugOutput(4, debugOutputCallback);
            nativeAccess.initializeRelayNetworkAccess();
        } catch (RuntimeException | Error exception) {
            try {
                nativeAccess.setCallbacks(null, null);
                nativeAccess.setDebugOutput(0, null);
            } catch (Throwable ignored) {
            }
            throw exception;
        }
    }

    boolean send(long remoteSteamId, ByteBuffer payload, boolean unreliable, int channel)
            throws IOException {
        return sendResult(remoteSteamId, payload, unreliable, channel) == RESULT_OK;
    }

    int sendResult(long remoteSteamId, ByteBuffer payload, boolean unreliable, int channel)
            throws IOException {
        return sendResult(remoteSteamId, payload, unreliable, false, channel);
    }

    int sendResult(
            long remoteSteamId,
            ByteBuffer payload,
            boolean unreliable,
            boolean autoRestartBrokenSession,
            int channel
    ) throws IOException {
        ensureOpen();
        if (unreliable && autoRestartBrokenSession) {
            throw new IOException("A datagram cannot restart a broken reliable Steam session");
        }
        if (!payload.isDirect()) {
            throw new IOException("Steam Networking Messages requires a direct send buffer");
        }
        int size = payload.remaining();
        Pointer data = Native.getDirectBufferPointer(payload);
        if (data == null) {
            throw new IOException("Could not access the direct Steam send buffer");
        }
        data = data.share(payload.position());
        int flags = unreliable ? SEND_UNRELIABLE_NO_DELAY : SEND_RELIABLE_NO_NAGLE;
        if (autoRestartBrokenSession) {
            flags |= SEND_AUTO_RESTART_BROKEN_SESSION;
        }
        return nativeAccess.send(identity(remoteSteamId), data, size, flags, channel);
    }

    int availablePacketSize(int channel) throws IOException {
        ensureOpen();
        if (pendingMessages.isEmpty()) {
            Pointer[] messages = new Pointer[RECEIVE_BATCH_SIZE];
            int count = nativeAccess.receive(channel, messages, RECEIVE_BATCH_SIZE);
            if (count < 0 || count > RECEIVE_BATCH_SIZE) {
                throw new IOException("Steam returned an invalid received-message count: " + count);
            }
            if (count == 0) {
                return 0;
            }
            for (int index = 0; index < count; index++) {
                if (messages[index] == null) {
                    while (!pendingMessages.isEmpty()) {
                        nativeAccess.releaseMessage(pendingMessages.removeFirst());
                    }
                    throw new IOException("Steam returned a null received message");
                }
                pendingMessages.addLast(messages[index]);
            }
        }
        Pointer pendingMessage = pendingMessages.peekFirst();
        int size = pendingMessage.getInt(MESSAGE_SIZE_OFFSET);
        if (size == 0) {
            nativeAccess.releaseMessage(pendingMessages.removeFirst());
        }
        return size;
    }

    Received receive(ByteBuffer target, int channel) throws IOException {
        ensureOpen();
        Pointer message = pendingMessages.pollFirst();
        if (message == null) {
            throw new IOException("No Steam Networking Messages packet is available on channel " + channel);
        }

        try {
            int size = message.getInt(MESSAGE_SIZE_OFFSET);
            if (size < 0) {
                throw new IOException("Steam returned a negative message size: " + size);
            }
            if (size > target.remaining()) {
                throw new BufferOverflowException();
            }
            Pointer data = message.getPointer(MESSAGE_DATA_OFFSET);
            if (size > 0 && data == null) {
                throw new IOException("Steam returned a null message payload");
            }
            if (size > 0) {
                target.put(data.getByteBuffer(0, size));
            }
            return new Received(readSteamId(message.share(MESSAGE_IDENTITY_OFFSET)), size);
        } finally {
            nativeAccess.releaseMessage(message);
        }
    }

    void discardPendingMessage() throws IOException {
        ensureOpen();
        Pointer message = pendingMessages.pollFirst();
        if (message != null) {
            nativeAccess.releaseMessage(message);
        }
    }

    boolean accept(long remoteSteamId) {
        ensureOpenUnchecked();
        return nativeAccess.accept(identity(remoteSteamId));
    }

    void closePeer(long remoteSteamId) {
        endBulkTransfer(remoteSteamId);
        if (closed) {
            return;
        }
        Memory identity = identities.remove(remoteSteamId);
        nativeAccess.closeSession(identity == null ? newIdentity(remoteSteamId) : identity);
    }

    boolean hasQueuedPackets(long remoteSteamId) {
        if (closed) {
            return false;
        }
        realTimeStatus.clear();
        int state = nativeAccess.getSessionConnectionInfo(identity(remoteSteamId), realTimeStatus);
        return state == CONNECTION_STATE_CONNECTING
                || state == CONNECTION_STATE_FINDING_ROUTE
                || realTimeStatus.getInt(STATUS_PENDING_UNRELIABLE_OFFSET) > 0
                || realTimeStatus.getInt(STATUS_PENDING_RELIABLE_OFFSET) > 0
                || realTimeStatus.getInt(STATUS_SENT_UNACKED_RELIABLE_OFFSET) > 0;
    }

    /**
     * Stops the Java bridge from blindly filling Steam's native reliable queue.
     * Steam accepts reliable messages before they reach the wire, so a successful
     * send alone is not evidence that the peer can consume another Forge chunk
     * burst without adding seconds of latency.
     */
    boolean isReliableQueueBackpressured(
            long remoteSteamId,
            int maxPendingBytes,
            long maxQueueMicros
    ) {
        if (closed) {
            return true;
        }
        realTimeStatus.clear();
        int state = nativeAccess.getSessionConnectionInfo(identity(remoteSteamId), realTimeStatus);
        if (state <= 0) {
            return false;
        }
        int pendingReliable = realTimeStatus.getInt(STATUS_PENDING_RELIABLE_OFFSET);
        long queueMicros = realTimeStatus.getLong(STATUS_QUEUE_TIME_OFFSET);
        return pendingReliable >= maxPendingBytes
                || (queueMicros >= 0L && queueMicros >= maxQueueMicros);
    }

    /** Temporary bulk profile; called only on the Steam worker while a saved world is sent. */
    void beginBulkTransfer(long peer,long now) {
        if(closed||bulkUnavailable)return;
        if(bulkPeer==peer){tuneBulkTransfer(peer,now);return;}
        if(bulkPeer!=0)endBulkTransfer(bulkPeer);
        Integer minimum=nativeAccess.getGlobalConfigInt32(CONFIG_SEND_RATE_MIN);
        Integer maximum=nativeAccess.getGlobalConfigInt32(CONFIG_SEND_RATE_MAX);
        if(minimum==null || maximum==null){bulkUnavailable=true;LOGGER.warn("World transfer bulk profile unavailable; using normal Steam settings");return;}
        // Never change settings we cannot restore.
        savedRateMin=minimum;savedRateMax=maximum;bulkPeer=peer;
        realTimeStatus.clear();nativeAccess.getSessionConnectionInfo(identity(peer),realTimeStatus);
        bulkBaselinePing=Math.max(1,realTimeStatus.getInt(4));
        LOGGER.info("World transfer Steam rate before bulk: {}",diagnosticState(peer));
        if(!nativeAccess.setGlobalConfigInt32(CONFIG_SEND_RATE_MAX,BULK_RATE_MAX)
                || !nativeAccess.setGlobalConfigInt32(CONFIG_SEND_RATE_MIN,BULK_RATE_INITIAL)) {
            endBulkTransfer(peer);bulkUnavailable=true;return;
        }
        bulkFloor=BULK_RATE_INITIAL;bulkGoodSamples=bulkBadSamples=0;bulkHadCongestion=false;
        nextBulkTune=now+2000;nextBulkDecrease=now;
        LOGGER.info("World transfer bulk profile v2 enabled: initial={} bytes/s, maximum=unlimited",bulkFloor);
    }
    private void tuneBulkTransfer(long peer,long now) {
        if(now<nextBulkTune)return;nextBulkTune=now+2000;
        realTimeStatus.clear();int state=nativeAccess.getSessionConnectionInfo(identity(peer),realTimeStatus);
        if(state!=3)return;
        int ping=realTimeStatus.getInt(4),pending=realTimeStatus.getInt(STATUS_PENDING_RELIABLE_OFFSET);
        float local=realTimeStatus.getFloat(8),remote=realTimeStatus.getFloat(12),wire=realTimeStatus.getFloat(20);
        boolean loss=(local>=0&&local<0.90f)||(remote>=0&&remote<0.90f);
        boolean delayed=ping>Math.max(200,bulkBaselinePing*3)&&pending>64*1024;
        boolean struggling=loss&&ping>Math.max(200,bulkBaselinePing*2)
                &&wire<bulkFloor*0.5f&&pending>64*1024;
        // Quality is a rolling historical estimate. Never repeatedly penalize old loss
        // after RTT recovers, or one brief spike can pin an otherwise good route at 128 KiB/s.
        if(delayed||struggling) {
            bulkGoodSamples=0;
            if(++bulkBadSamples>=2&&now>=nextBulkDecrease) {
                int slower=Math.max(128*1024,bulkFloor-bulkFloor/4);
                if(nativeAccess.setGlobalConfigInt32(CONFIG_SEND_RATE_MIN,slower))bulkFloor=slower;
                bulkBadSamples=0;bulkHadCongestion=true;nextBulkDecrease=now+6000;
            }
        } else if(ping<=Math.max(200,bulkBaselinePing*2)
                &&wire>=bulkFloor*0.5f&&pending>24*1024) {
            bulkBadSamples=0;
            if(++bulkGoodSamples>=2) {
                // Saturate only the int32 minimum-rate hint; never impose a maximum.
                long increase=bulkHadCongestion?Math.max(64*1024,bulkFloor/4):bulkFloor;
                int faster=(int)Math.min(Integer.MAX_VALUE,bulkFloor+increase);
                if(now>=nextBulkDecrease&&nativeAccess.setGlobalConfigInt32(CONFIG_SEND_RATE_MIN,faster)) {
                    bulkFloor=faster;bulkGoodSamples=0;
                }
            }
        } else bulkGoodSamples=bulkBadSamples=0;
        LOGGER.info("World transfer Steam bulk: floor={} {}",bulkFloor,diagnosticState(peer));
    }
    void endBulkTransfer(long peer) {
        if(bulkPeer==0 || peer!=bulkPeer)return;
        if(savedRateMin!=null)nativeAccess.setGlobalConfigInt32(CONFIG_SEND_RATE_MIN,savedRateMin);
        if(savedRateMax!=null)nativeAccess.setGlobalConfigInt32(CONFIG_SEND_RATE_MAX,savedRateMax);
        bulkPeer=0;savedRateMin=savedRateMax=null;bulkFloor=bulkGoodSamples=bulkBadSamples=0;
        nextBulkDecrease=0;bulkHadCongestion=false;
        LOGGER.info("World transfer bulk profile restored normal Steam settings");
    }

    String diagnosticState(long remoteSteamId) {
        if (closed) return "closed";
        realTimeStatus.clear();
        int state = nativeAccess.getSessionConnectionInfo(identity(remoteSteamId), realTimeStatus);
        return "state=" + state + ",pingMs=" + realTimeStatus.getInt(4)
                + ",qualityLocal=" + realTimeStatus.getFloat(8)
                + ",qualityRemote=" + realTimeStatus.getFloat(12)
                + ",outBytesPerSec=" + realTimeStatus.getFloat(20)
                + ",sendBytesPerSec=" + realTimeStatus.getInt(32)
                + ",pendingReliable=" + realTimeStatus.getInt(STATUS_PENDING_RELIABLE_OFFSET)
                + ",unackedReliable=" + realTimeStatus.getInt(STATUS_SENT_UNACKED_RELIABLE_OFFSET)
                + ",queueMicros=" + realTimeStatus.getLong(STATUS_QUEUE_TIME_OFFSET);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        endBulkTransfer(bulkPeer);
        closed = true;
        nativeAccess.setCallbacks(null, null);
        nativeAccess.setDebugOutput(0, null);
        Pointer message;
        while ((message = pendingMessages.pollFirst()) != null) {
            nativeAccess.releaseMessage(message);
        }
        for (Memory identity : identities.values()) {
            nativeAccess.closeSession(identity);
        }
        identities.clear();
    }

    private void handleSessionRequest(Pointer request) {
        if (closed || request == null) {
            return;
        }
        long remoteSteamId = readSteamId(request);
        if (remoteSteamId != 0) {
            try {
                listener.onSessionRequest(remoteSteamId);
            } catch (Throwable throwable) {
                LOGGER.error("Steam session-request callback failed", throwable);
            }
        }
    }

    private void handleSessionFailed(Pointer failure) {
        if (closed || failure == null) {
            return;
        }
        long remoteSteamId = readSteamId(failure);
        if (remoteSteamId == 0) {
            return;
        }
        int endReason = failure.getInt(CONNECTION_INFO_END_REASON_OFFSET);
        String detail = readFixedString(
                failure,
                CONNECTION_INFO_END_DEBUG_OFFSET,
                CONNECTION_INFO_END_DEBUG_SIZE
        );
        try {
            listener.onSessionFailed(remoteSteamId, endReason, detail);
        } catch (Throwable throwable) {
            LOGGER.error("Steam session-failure callback failed", throwable);
        }
    }

    private void handleDebugOutput(int type, String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        String clean = message.trim();
        if (type <= 2) {
            LOGGER.warn("Steam Networking Sockets: {}", clean);
        } else {
            LOGGER.debug("Steam Networking Sockets: {}", clean);
        }
    }

    private Memory identity(long remoteSteamId) {
        return identities.computeIfAbsent(remoteSteamId, SteamNetworkingMessagesTransport::newIdentity);
    }

    static Memory newIdentity(long remoteSteamId) {
        Memory identity = new Memory(STEAM_IDENTITY_SIZE);
        identity.clear();
        identity.setInt(IDENTITY_TYPE_OFFSET, STEAM_IDENTITY_TYPE);
        identity.setInt(IDENTITY_SIZE_OFFSET, STEAM_IDENTITY_VALUE_SIZE);
        identity.setLong(IDENTITY_VALUE_OFFSET, remoteSteamId);
        return identity;
    }

    static long readSteamId(Pointer identity) {
        if (identity == null
                || identity.getInt(IDENTITY_TYPE_OFFSET) != STEAM_IDENTITY_TYPE
                || identity.getInt(IDENTITY_SIZE_OFFSET) != STEAM_IDENTITY_VALUE_SIZE) {
            return 0;
        }
        return identity.getLong(IDENTITY_VALUE_OFFSET);
    }

    private static String readFixedString(Pointer source, long offset, int maximumSize) {
        byte[] bytes = source.getByteArray(offset, maximumSize);
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            length++;
        }
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Steam Networking Messages transport is closed");
        }
    }

    private void ensureOpenUnchecked() {
        if (closed) {
            throw new IllegalStateException("Steam Networking Messages transport is closed");
        }
    }

    private interface FlatApi extends Library {
        byte SteamAPI_ISteamNetworkingUtils_SetGlobalConfigValueInt32(Pointer self, int option, int value);
        int SteamAPI_ISteamNetworkingUtils_GetConfigValue(Pointer self,int option,int scope,Pointer scopeObject,Pointer type,Pointer result,Pointer size);
        Pointer SteamAPI_SteamNetworkingMessages_SteamAPI_v002();

        Pointer SteamAPI_SteamGameServerNetworkingMessages_SteamAPI_v002();

        Pointer SteamAPI_SteamNetworkingUtils_SteamAPI_v004();

        int SteamAPI_ISteamNetworkingMessages_SendMessageToUser(
                Pointer self,
                Pointer identity,
                Pointer data,
                int size,
                int flags,
                int channel
        );

        int SteamAPI_ISteamNetworkingMessages_ReceiveMessagesOnChannel(
                Pointer self,
                int channel,
                Pointer[] messages,
                int maxMessages
        );

        byte SteamAPI_ISteamNetworkingMessages_AcceptSessionWithUser(Pointer self, Pointer identity);

        byte SteamAPI_ISteamNetworkingMessages_CloseSessionWithUser(Pointer self, Pointer identity);

        int SteamAPI_ISteamNetworkingMessages_GetSessionConnectionInfo(
                Pointer self,
                Pointer identity,
                Pointer connectionInfo,
                Pointer realTimeStatus
        );

        void SteamAPI_ISteamNetworkingUtils_InitRelayNetworkAccess(Pointer self);

        void SteamAPI_ISteamNetworkingUtils_SetGlobalCallback_MessagesSessionRequest(
                Pointer self,
                SessionRequestCallback callback
        );

        void SteamAPI_ISteamNetworkingUtils_SetGlobalCallback_MessagesSessionFailed(
                Pointer self,
                SessionFailedCallback callback
        );

        void SteamAPI_ISteamNetworkingUtils_SetDebugOutputFunction(
                Pointer self,
                int detailLevel,
                DebugOutputCallback callback
        );

        void SteamAPI_SteamNetworkingMessage_t_Release(Pointer message);
    }

    private static final class JnaNativeAccess implements NativeAccess {
        private final FlatApi api;
        private final Pointer messages;
        private final Pointer utils;

        private JnaNativeAccess(Path steamApiLibrary, boolean gameServer) throws IOException {
            Objects.requireNonNull(steamApiLibrary, "steamApiLibrary");
            try {
                // loadLibrary exists in both Minecraft's old bundled JNA 3.x
                // and current JNA. Native.load was added later and crashes on
                // Forge 1.7.10/1.8.9 when the launcher provides JNA first.
                api = (FlatApi) Native.loadLibrary(
                        steamApiLibrary.toAbsolutePath().normalize().toString(),
                        FlatApi.class
                );
                messages = gameServer
                        ? api.SteamAPI_SteamGameServerNetworkingMessages_SteamAPI_v002()
                        : api.SteamAPI_SteamNetworkingMessages_SteamAPI_v002();
                utils = api.SteamAPI_SteamNetworkingUtils_SteamAPI_v004();
            } catch (UnsatisfiedLinkError | RuntimeException exception) {
                throw new IOException("Could not bind Steam Networking Messages", exception);
            }
            if (messages == null || utils == null) {
                throw new IOException("Steam Networking Messages is unavailable after SteamAPI initialization");
            }
        }

        @Override
        public boolean setGlobalConfigInt32(int option, int value) {
            try {
                return api.SteamAPI_ISteamNetworkingUtils_SetGlobalConfigValueInt32(utils, option, value) != 0;
            } catch (UnsatisfiedLinkError unavailable) {
                return false;
            }
        }

        @Override public Integer getGlobalConfigInt32(int option) {
            try {
                Memory type=new Memory(4),value=new Memory(4),size=new Memory(Native.POINTER_SIZE);
                if(Native.POINTER_SIZE==8)size.setLong(0,4);else size.setInt(0,4);
                int result=api.SteamAPI_ISteamNetworkingUtils_GetConfigValue(utils,option,1,null,type,value,size);
                return result>0 && type.getInt(0)==1?value.getInt(0):null;
            } catch(UnsatisfiedLinkError unavailable){return null;}
        }

        @Override
        public int send(Pointer identity, Pointer data, int size, int flags, int channel) {
            return api.SteamAPI_ISteamNetworkingMessages_SendMessageToUser(
                    messages,
                    identity,
                    data,
                    size,
                    flags,
                    channel
            );
        }

        @Override
        public int receive(int channel, Pointer[] output, int maxMessages) {
            return api.SteamAPI_ISteamNetworkingMessages_ReceiveMessagesOnChannel(
                    messages,
                    channel,
                    output,
                    maxMessages
            );
        }

        @Override
        public boolean accept(Pointer identity) {
            return api.SteamAPI_ISteamNetworkingMessages_AcceptSessionWithUser(messages, identity) != 0;
        }

        @Override
        public void closeSession(Pointer identity) {
            api.SteamAPI_ISteamNetworkingMessages_CloseSessionWithUser(messages, identity);
        }

        @Override
        public int getSessionConnectionInfo(Pointer identity, Pointer realTimeStatus) {
            return api.SteamAPI_ISteamNetworkingMessages_GetSessionConnectionInfo(
                    messages,
                    identity,
                    null,
                    realTimeStatus
            );
        }

        @Override
        public void initializeRelayNetworkAccess() {
            api.SteamAPI_ISteamNetworkingUtils_InitRelayNetworkAccess(utils);
        }

        @Override
        public void releaseMessage(Pointer message) {
            api.SteamAPI_SteamNetworkingMessage_t_Release(message);
        }

        @Override
        public void setCallbacks(SessionRequestCallback request, SessionFailedCallback failure) {
            api.SteamAPI_ISteamNetworkingUtils_SetGlobalCallback_MessagesSessionRequest(utils, request);
            api.SteamAPI_ISteamNetworkingUtils_SetGlobalCallback_MessagesSessionFailed(utils, failure);
        }

        @Override
        public void setDebugOutput(int detailLevel, DebugOutputCallback callback) {
            api.SteamAPI_ISteamNetworkingUtils_SetDebugOutputFunction(utils, detailLevel, callback);
        }
    }
}
