package link.e4steam.steam;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamNetworkingMessagesTransportTest {
    private static final long REMOTE_ID = 76561198000000001L;

    @Test
    void raisesOnlyTheSendRateCeilingBeforeRelayInitialization() {
        FakeNativeAccess access = new FakeNativeAccess();
        SteamNetworkingMessagesTransport transport = transport(access);
        assertEquals(11, access.configOption);
        assertEquals(4 * 1024 * 1024, access.configValue);
        transport.close();
    }

    @Test void temporaryBulkProfileRestoresPreviousSettingsAfterCompletionOrClose() {
        FakeNativeAccess access=new FakeNativeAccess();SteamNetworkingMessagesTransport t=transport(access);
        t.beginBulkTransfer(REMOTE_ID,0);
        assertEquals(2*1024*1024,access.settings.get(10));assertEquals(0,access.settings.get(11));
        t.endBulkTransfer(REMOTE_ID+1);assertEquals(2*1024*1024,access.settings.get(10));
        t.endBulkTransfer(REMOTE_ID);assertEquals(256*1024,access.settings.get(10));assertEquals(4*1024*1024,access.settings.get(11));
        t.beginBulkTransfer(REMOTE_ID,0);t.close();
        assertEquals(256*1024,access.settings.get(10));assertEquals(4*1024*1024,access.settings.get(11));
    }
    @Test void historicalPacketLossDoesNotThrottleARecoveredRoute() {
        FakeNativeAccess access=new FakeNativeAccess();access.connectionState=3;access.pendingReliable=100_000;
        SteamNetworkingMessagesTransport t=transport(access);t.beginBulkTransfer(REMOTE_ID,0);
        access.quality=0.5f;access.outgoing=2*1024*1024;
        t.beginBulkTransfer(REMOTE_ID,2000);t.beginBulkTransfer(REMOTE_ID,4000);
        assertEquals(4*1024*1024,access.settings.get(10));assertEquals(0,access.settings.get(11));
        t.close();assertEquals(256*1024,access.settings.get(10));
    }
    @Test void oneLatencySpikeDoesNotReduceTheRate() {
        FakeNativeAccess access=new FakeNativeAccess();access.connectionState=3;access.ping=120;access.pendingReliable=100_000;
        SteamNetworkingMessagesTransport t=transport(access);t.beginBulkTransfer(REMOTE_ID,0);
        access.ping=645;access.quality=0.5f;t.beginBulkTransfer(REMOTE_ID,2000);
        access.ping=120;t.beginBulkTransfer(REMOTE_ID,4000);
        assertEquals(2*1024*1024,access.settings.get(10));t.close();
    }
    @Test void sustainedCongestionBacksOffGraduallyWithACooldownAndCanRecover() {
        FakeNativeAccess access=new FakeNativeAccess();access.connectionState=3;access.ping=120;access.pendingReliable=100_000;
        SteamNetworkingMessagesTransport t=transport(access);t.beginBulkTransfer(REMOTE_ID,0);
        access.ping=645;access.quality=0.5f;
        t.beginBulkTransfer(REMOTE_ID,2000);t.beginBulkTransfer(REMOTE_ID,4000);
        assertEquals(1536*1024,access.settings.get(10));
        t.beginBulkTransfer(REMOTE_ID,6000);t.beginBulkTransfer(REMOTE_ID,8000);
        assertEquals(1536*1024,access.settings.get(10));
        access.ping=120;access.outgoing=1536*1024;
        t.beginBulkTransfer(REMOTE_ID,10000);t.beginBulkTransfer(REMOTE_ID,12000);
        assertEquals(1920*1024,access.settings.get(10));
        assertEquals(0,access.settings.get(11));t.close();
    }
    @Test void healthyBulkRouteCanGrowPastThePreviousEightMiBCeiling() {
        FakeNativeAccess access=new FakeNativeAccess();access.connectionState=3;access.pendingReliable=100_000;
        access.outgoing=128*1024*1024;
        SteamNetworkingMessagesTransport t=transport(access);t.beginBulkTransfer(REMOTE_ID,0);
        for(int sample=1;sample<=6;sample++)t.beginBulkTransfer(REMOTE_ID,sample*2000L);
        assertEquals(16*1024*1024,access.settings.get(10));
        assertEquals(0,access.settings.get(11));t.close();
    }
    @Test void failedBulkSetupRollsBackTheChangedCeiling() {
        FakeNativeAccess access=new FakeNativeAccess();SteamNetworkingMessagesTransport t=transport(access);
        access.failBulkMinimum=true;t.beginBulkTransfer(REMOTE_ID,0);
        assertEquals(256*1024,access.settings.get(10));assertEquals(4*1024*1024,access.settings.get(11));t.close();
    }
    @Test void closingTheBulkPeerRestoresNormalRatesImmediately() {
        FakeNativeAccess access=new FakeNativeAccess();SteamNetworkingMessagesTransport t=transport(access);
        t.beginBulkTransfer(REMOTE_ID,0);t.closePeer(REMOTE_ID);
        assertEquals(256*1024,access.settings.get(10));assertEquals(4*1024*1024,access.settings.get(11));t.close();
    }
    @Test void unreadableConfigurationIsNeverChangedForBulk() {
        FakeNativeAccess access=new FakeNativeAccess();access.readSettings=false;
        SteamNetworkingMessagesTransport t=transport(access);t.beginBulkTransfer(REMOTE_ID,0);
        assertEquals(256*1024,access.settings.get(10));assertEquals(4*1024*1024,access.settings.get(11));t.close();
    }

    @Test
    void sendsWithSteamNetworkingSocketsEquivalentFlags() throws Exception {
        FakeNativeAccess nativeAccess = new FakeNativeAccess();
        SteamNetworkingMessagesTransport transport = transport(nativeAccess);
        ByteBuffer buffer = ByteBuffer.allocateDirect(4);
        buffer.put(new byte[]{1, 2, 3, 4}).flip();

        assertTrue(transport.send(REMOTE_ID, buffer, false, 480));
        assertEquals(9, nativeAccess.sentFlags); // ReliableNoNagle.
        assertEquals(REMOTE_ID, nativeAccess.sentSteamId);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, nativeAccess.sentPayload);

        buffer.rewind();
        assertEquals(1, transport.sendResult(REMOTE_ID, buffer, false, true, 480));
        assertEquals(41, nativeAccess.sentFlags); // ReliableNoNagle + AutoRestartBrokenSession.
        assertArrayEquals(new byte[]{1, 2, 3, 4}, nativeAccess.sentPayload);

        buffer.position(1);
        assertTrue(transport.send(REMOTE_ID, buffer, true, 480));
        assertEquals(5, nativeAccess.sentFlags); // UnreliableNoDelay.
        assertArrayEquals(new byte[]{2, 3, 4}, nativeAccess.sentPayload);
        transport.close();
    }

    @Test
    void receivesAndReleasesNativeMessage() throws Exception {
        FakeNativeAccess nativeAccess = new FakeNativeAccess();
        nativeAccess.queueMessage(REMOTE_ID, new byte[]{9, 8, 7});
        SteamNetworkingMessagesTransport transport = transport(nativeAccess);

        assertEquals(3, transport.availablePacketSize(480));
        ByteBuffer target = ByteBuffer.allocateDirect(16);
        SteamNetworkingMessagesTransport.Received received = transport.receive(target, 480);

        assertEquals(REMOTE_ID, received.remoteSteamId());
        assertEquals(3, received.size());
        target.flip();
        byte[] payload = new byte[target.remaining()];
        target.get(payload);
        assertArrayEquals(new byte[]{9, 8, 7}, payload);
        assertEquals(1, nativeAccess.releaseCount);
        transport.close();
    }

    @Test
    void receivesNativeMessagesInBoundedBatchesWithoutReordering() throws Exception {
        FakeNativeAccess nativeAccess = new FakeNativeAccess();
        nativeAccess.queueMessage(REMOTE_ID, new byte[]{1});
        nativeAccess.queueMessage(REMOTE_ID + 1, new byte[]{2, 3});
        SteamNetworkingMessagesTransport transport = transport(nativeAccess);

        assertEquals(1, transport.availablePacketSize(480));
        ByteBuffer firstTarget = ByteBuffer.allocateDirect(8);
        assertEquals(REMOTE_ID, transport.receive(firstTarget, 480).remoteSteamId());
        assertEquals(2, transport.availablePacketSize(480));
        ByteBuffer secondTarget = ByteBuffer.allocateDirect(8);
        assertEquals(REMOTE_ID + 1, transport.receive(secondTarget, 480).remoteSteamId());

        assertEquals(1, nativeAccess.receiveCalls);
        assertEquals(2, nativeAccess.releaseCount);
        transport.close();
    }

    @Test
    void discardsAndReleasesOversizedForeignMessage() throws Exception {
        FakeNativeAccess nativeAccess = new FakeNativeAccess();
        nativeAccess.queueMessage(REMOTE_ID, new byte[SteamProtocol.MAX_ACCEPTED_STEAM_PACKET_SIZE + 1]);
        SteamNetworkingMessagesTransport transport = transport(nativeAccess);

        assertEquals(SteamProtocol.MAX_ACCEPTED_STEAM_PACKET_SIZE + 1, transport.availablePacketSize(480));
        transport.discardPendingMessage();

        assertEquals(1, nativeAccess.releaseCount);
        assertEquals(0, transport.availablePacketSize(480));
        transport.close();
    }

    @Test
    void forwardsSessionCallbacksAndRejectsNonSteamIdentities() {
        FakeNativeAccess nativeAccess = new FakeNativeAccess();
        List<Long> requests = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        SteamNetworkingMessagesTransport transport = new SteamNetworkingMessagesTransport(
                nativeAccess,
                new SteamNetworkingMessagesTransport.SessionListener() {
                    @Override
                    public void onSessionRequest(long remoteSteamId) {
                        requests.add(remoteSteamId);
                    }

                    @Override
                    public void onSessionFailed(long remoteSteamId, int endReason, String detail) {
                        failures.add(remoteSteamId + ":" + endReason + ":" + detail);
                    }
                }
        );

        Memory request = SteamNetworkingMessagesTransport.newIdentity(REMOTE_ID);
        nativeAccess.requestCallback.invoke(request);
        request.setInt(0, 3);
        nativeAccess.requestCallback.invoke(request);

        Memory failure = new Memory(512);
        failure.clear();
        Memory identity = SteamNetworkingMessagesTransport.newIdentity(REMOTE_ID);
        failure.write(0, identity.getByteArray(0, 136), 0, 136);
        failure.setInt(176, 5003);
        byte[] detail = "connection timed out".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        failure.write(180, detail, 0, detail.length);
        nativeAccess.failedCallback.invoke(failure);

        assertEquals(List.of(REMOTE_ID), requests);
        assertEquals(List.of(REMOTE_ID + ":5003:connection timed out"), failures);
        transport.close();
    }

    @Test
    void reportsConnectingAndQueuedSessionData() {
        FakeNativeAccess nativeAccess = new FakeNativeAccess();
        SteamNetworkingMessagesTransport transport = transport(nativeAccess);

        nativeAccess.connectionState = 1;
        assertTrue(transport.hasQueuedPackets(REMOTE_ID));
        nativeAccess.connectionState = 3;
        nativeAccess.pendingReliable = 12;
        assertTrue(transport.hasQueuedPackets(REMOTE_ID));
        nativeAccess.pendingReliable = 0;
        assertFalse(transport.hasQueuedPackets(REMOTE_ID));
        transport.close();
    }

    @Test
    void reportsReliableQueueBackpressureByBytesOrEstimatedDelay() {
        FakeNativeAccess nativeAccess = new FakeNativeAccess();
        SteamNetworkingMessagesTransport transport = transport(nativeAccess);

        nativeAccess.connectionState = 3;
        nativeAccess.pendingReliable = 256 * 1024;
        assertTrue(transport.isReliableQueueBackpressured(REMOTE_ID, 256 * 1024, 250_000L));

        nativeAccess.pendingReliable = 1;
        nativeAccess.queueMicros = 250_000L;
        assertTrue(transport.isReliableQueueBackpressured(REMOTE_ID, 256 * 1024, 250_000L));

        nativeAccess.queueMicros = 249_999L;
        assertFalse(transport.isReliableQueueBackpressured(REMOTE_ID, 256 * 1024, 250_000L));
        transport.close();
    }

    private static SteamNetworkingMessagesTransport transport(FakeNativeAccess nativeAccess) {
        return new SteamNetworkingMessagesTransport(
                nativeAccess,
                new SteamNetworkingMessagesTransport.SessionListener() {
                    @Override
                    public void onSessionRequest(long remoteSteamId) {
                    }

                    @Override
                    public void onSessionFailed(long remoteSteamId, int endReason, String detail) {
                    }
                }
        );
    }

    private static final class FakeNativeAccess implements SteamNetworkingMessagesTransport.NativeAccess {
        final java.util.Map<Integer,Integer> settings=new java.util.HashMap<>();
        boolean readSettings=true,failBulkMinimum;float quality=1,outgoing;
        FakeNativeAccess(){settings.put(10,256*1024);settings.put(11,256*1024);}
        @Override public Integer getGlobalConfigInt32(int option){return readSettings?settings.get(option):null;}
        private int configOption;
        private int configValue;
        @Override public boolean setGlobalConfigInt32(int option, int value) {
            configOption = option;
            configValue = value;if(failBulkMinimum&&option==10&&value==2*1024*1024)return false;settings.put(option,value);
            return true;
        }
        private SteamNetworkingMessagesTransport.SessionRequestCallback requestCallback;
        private SteamNetworkingMessagesTransport.SessionFailedCallback failedCallback;
        private final ArrayDeque<Memory> queuedMessages = new ArrayDeque<>();
        private final ArrayList<Memory> retainedPayloads = new ArrayList<>();
        private byte[] sentPayload;
        private long sentSteamId;
        private int sentFlags;
        private int releaseCount;
        private int connectionState;
        private int ping;
        private int pendingReliable;
        private long queueMicros;
        private int receiveCalls;

        @Override
        public int send(Pointer identity, Pointer data, int size, int flags, int channel) {
            sentSteamId = SteamNetworkingMessagesTransport.readSteamId(identity);
            sentPayload = data.getByteArray(0, size);
            sentFlags = flags;
            return 1;
        }

        @Override
        public int receive(int channel, Pointer[] messages, int maxMessages) {
            receiveCalls++;
            int count = 0;
            while (count < maxMessages && !queuedMessages.isEmpty()) {
                messages[count++] = queuedMessages.removeFirst();
            }
            return count;
        }

        @Override
        public boolean accept(Pointer identity) {
            return true;
        }

        @Override
        public void closeSession(Pointer identity) {
        }

        @Override
        public int getSessionConnectionInfo(Pointer identity, Pointer realTimeStatus) {
            realTimeStatus.setInt(4,ping);
            realTimeStatus.setFloat(8,quality);realTimeStatus.setFloat(12,quality);realTimeStatus.setFloat(20,outgoing);
            realTimeStatus.setInt(40, pendingReliable);
            realTimeStatus.setLong(48, queueMicros);
            return connectionState;
        }

        @Override
        public void initializeRelayNetworkAccess() {
        }

        @Override
        public void releaseMessage(Pointer message) {
            releaseCount++;
        }

        @Override
        public void setCallbacks(
                SteamNetworkingMessagesTransport.SessionRequestCallback request,
                SteamNetworkingMessagesTransport.SessionFailedCallback failure
        ) {
            requestCallback = request;
            failedCallback = failure;
        }

        @Override
        public void setDebugOutput(
                int detailLevel,
                SteamNetworkingMessagesTransport.DebugOutputCallback callback
        ) {
        }

        private void queueMessage(long remoteSteamId, byte[] payload) {
            Memory queuedPayload = new Memory(payload.length);
            queuedPayload.write(0, payload, 0, payload.length);
            retainedPayloads.add(queuedPayload);
            Memory queuedMessage = new Memory(216);
            queuedMessage.clear();
            queuedMessage.setPointer(0, queuedPayload);
            queuedMessage.setInt(8, payload.length);
            Memory identity = SteamNetworkingMessagesTransport.newIdentity(remoteSteamId);
            queuedMessage.write(16, identity.getByteArray(0, 136), 0, 136);
            queuedMessages.addLast(queuedMessage);
        }
    }
}
