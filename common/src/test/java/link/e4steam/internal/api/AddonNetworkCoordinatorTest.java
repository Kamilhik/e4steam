package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.capability.CapabilityId;
import link.e4steam.api.identity.IdentityService.PeerId;
import link.e4steam.api.network.NetworkService.ChannelDescriptor;
import link.e4steam.api.network.NetworkService.ChannelHandle;
import link.e4steam.api.network.NetworkService.ChannelId;
import link.e4steam.api.network.NetworkService.Delivery;
import link.e4steam.api.network.NetworkService.Direction;
import link.e4steam.api.network.NetworkService.Requirement;
import link.e4steam.api.network.NetworkService.SendStatus;
import link.e4steam.api.session.SessionService.SessionId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AddonNetworkCoordinatorTest {
    private static final SessionId SESSION = new SessionId("session-one", 7L);
    private static final PeerId PEER = new PeerId("peer-one");
    private static final AddonId OWNER = new AddonId("owner:addon");

    private final CoreSchedulerService scheduler = new CoreSchedulerService();
    private final FakeSessions sessions = new FakeSessions();
    private final AddonNetworkCoordinator coordinator =
            new AddonNetworkCoordinator(scheduler, sessions);

    @AfterEach
    void closeResources() {
        coordinator.close();
        scheduler.close();
    }

    @Test
    void requiredMismatchRejectsButOptionalMismatchDoesNot() {
        register(descriptor("owner-addon:required", 2, 3,
                Requirement.REQUIRED, Direction.BIDIRECTIONAL, 8), completedHandler());
        assertTrue(coordinator.hasRequiredChannels());

        AddonNetworkCoordinator.Negotiation required = coordinator.negotiate(
                SESSION, PEER, Collections.emptyList(), true);
        assertFalse(required.compatible());
        assertEquals("required-channel-incompatible", required.reasonCode());

        coordinator.close();
        AddonNetworkCoordinator optionalCoordinator =
                new AddonNetworkCoordinator(scheduler, sessions);
        try {
            optionalCoordinator.register(OWNER, descriptor("owner-addon:optional", 2, 3,
                    Requirement.OPTIONAL, Direction.BIDIRECTIONAL, 8),
                    completedHandler(), capabilities());
            assertFalse(optionalCoordinator.hasRequiredChannels());
            AddonNetworkCoordinator.Negotiation optional = optionalCoordinator.negotiate(
                    SESSION, PEER, Collections.singletonList(descriptor(
                            "owner-addon:optional", 8, 9, Requirement.OPTIONAL,
                            Direction.BIDIRECTIONAL, 8)), true);
            assertTrue(optional.compatible());
            assertTrue(optional.channels().isEmpty());
        } finally {
            optionalCoordinator.close();
        }
    }

    @Test
    void remoteRequiredChannelMissingLocallyRejects() {
        AddonNetworkCoordinator.Negotiation result = coordinator.negotiate(
                SESSION, PEER, Collections.singletonList(descriptor(
                        "remote-addon:required", 1, 1, Requirement.REQUIRED,
                        Direction.BIDIRECTIONAL, 8)), true);
        assertFalse(result.compatible());
        assertEquals("required-channel-missing", result.reasonCode());
    }

    @Test
    void messagesAreRejectedBeforeAuthNegotiationAndForStaleGeneration() {
        AtomicInteger calls = new AtomicInteger();
        ChannelDescriptor channel = descriptor("owner-addon:test", 1, 1,
                Requirement.OPTIONAL, Direction.BIDIRECTIONAL, 8);
        register(channel, (context, payload) -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(ApiResult.success(true));
        });

        ApiResult<Boolean> before = coordinator.receive(
                SESSION, PEER, channel.id().value(), 1, new byte[]{1}, true, false);
        assertError(before, ApiErrorCode.SECURITY_REJECTION);
        assertFalse(coordinator.negotiate(SESSION, PEER,
                Collections.singletonList(channel), false).compatible());

        assertTrue(coordinator.negotiate(SESSION, PEER,
                Collections.singletonList(channel), true).compatible());
        sessions.current = false;
        ApiResult<Boolean> stale = coordinator.receive(
                SESSION, PEER, channel.id().value(), 1, new byte[]{1}, true, false);
        assertError(stale, ApiErrorCode.STALE_HANDLE);
        assertEquals(0, calls.get());
    }

    @Test
    void directionAndRateLimitsAreEnforcedBeforeTransport() {
        ChannelDescriptor channel = new ChannelDescriptor(
                new ChannelId("owner-addon:host-only"), 1, 1,
                Requirement.OPTIONAL, Direction.HOST_TO_CLIENT,
                Delivery.RELIABLE_ORDERED, 1_024, 1_024, 8, "schema-v1");
        ChannelHandle handle = register(channel, completedHandler());
        assertTrue(coordinator.negotiate(SESSION, PEER,
                Collections.singletonList(channel), true).compatible());
        AtomicInteger sends = new AtomicInteger();
        coordinator.transport((session, peer, id, version, payload, reliable) -> {
            sends.incrementAndGet();
            return true;
        });

        ApiResult<Boolean> wrongDirection = coordinator.receive(
                SESSION, PEER, channel.id().value(), 1, new byte[]{1}, true, false);
        assertError(wrongDirection, ApiErrorCode.SECURITY_REJECTION);

        byte[] first = new byte[600];
        assertEquals(SendStatus.ACCEPTED, handle.send(SESSION, PEER, first)
                .toCompletableFuture().join().value().orElseThrow());
        assertEquals(SendStatus.RATE_LIMITED, handle.send(SESSION, PEER, first)
                .toCompletableFuture().join().value().orElseThrow());
        assertEquals(1, sends.get());
    }

    @Test
    void slowHandlerCannotOverflowNegotiatedQueue() throws Exception {
        CompletableFuture<ApiResult<Boolean>> blocked = new CompletableFuture<>();
        CountDownLatch entered = new CountDownLatch(1);
        ChannelDescriptor channel = new ChannelDescriptor(
                new ChannelId("owner-addon:queued"), 1, 1,
                Requirement.OPTIONAL, Direction.BIDIRECTIONAL,
                Delivery.RELIABLE_ORDERED, 1_024, 64 * 1_024, 1, "schema-v1");
        register(channel, (context, payload) -> {
            entered.countDown();
            return blocked;
        });
        assertTrue(coordinator.negotiate(SESSION, PEER,
                Collections.singletonList(channel), true).compatible());

        assertTrue(coordinator.receive(SESSION, PEER, channel.id().value(), 1,
                new byte[]{1}, true, false).isSuccess());
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        ApiResult<Boolean> overflow = coordinator.receive(SESSION, PEER,
                channel.id().value(), 1, new byte[]{2}, true, false);
        assertError(overflow, ApiErrorCode.QUEUE_FULL);
        blocked.complete(ApiResult.success(true));
    }

    @Test
    void disconnectingOnePeerDoesNotInvalidateOtherNegotiations() {
        PeerId second = new PeerId("peer-two");
        sessions.secondPeer = second;
        ChannelDescriptor channel = descriptor("owner-addon:test", 1, 1,
                Requirement.OPTIONAL, Direction.BIDIRECTIONAL, 8);
        register(channel, completedHandler());
        assertTrue(coordinator.negotiate(SESSION, PEER,
                Collections.singletonList(channel), true).compatible());
        assertTrue(coordinator.negotiate(SESSION, second,
                Collections.singletonList(channel), true).compatible());

        coordinator.closePeer(SESSION, PEER);
        assertError(coordinator.receive(SESSION, PEER, channel.id().value(), 1,
                new byte[]{1}, true, false), ApiErrorCode.SECURITY_REJECTION);
        assertTrue(coordinator.receive(SESSION, second, channel.id().value(), 1,
                new byte[]{1}, true, false).isSuccess());
    }

    private ChannelHandle register(ChannelDescriptor descriptor,
                                   link.e4steam.api.network.NetworkService.MessageHandler handler) {
        return coordinator.register(OWNER, descriptor, handler, capabilities())
                .value().orElseThrow();
    }

    private static link.e4steam.api.network.NetworkService.MessageHandler completedHandler() {
        return (context, payload) -> CompletableFuture.completedFuture(ApiResult.success(true));
    }

    private static CoreCapabilityService capabilities() {
        Set<CapabilityId> granted = Collections.singleton(Capabilities.NETWORK_CHANNEL_REGISTER);
        return new CoreCapabilityService(granted, granted);
    }

    private static ChannelDescriptor descriptor(
            String id, int minimum, int maximum, Requirement requirement,
            Direction direction, int queue) {
        return new ChannelDescriptor(new ChannelId(id), minimum, maximum, requirement,
                direction, Delivery.RELIABLE_ORDERED, 1_024, 64 * 1_024,
                queue, "schema-v1");
    }

    private static void assertError(ApiResult<?> result, ApiErrorCode expected) {
        assertFalse(result.isSuccess());
        assertEquals(expected, result.error().orElseThrow().code());
    }

    private static final class FakeSessions implements AddonNetworkCoordinator.SessionAccess {
        private volatile boolean current = true;
        private volatile PeerId secondPeer;
        @Override public boolean matches(SessionId sessionId, PeerId peerId) {
            return current && SESSION.equals(sessionId)
                    && (PEER.equals(peerId) || peerId.equals(secondPeer));
        }
        @Override public boolean localIsHost(SessionId sessionId) {
            return current && SESSION.equals(sessionId);
        }
    }
}
