package link.e4steam.api.network;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;
import link.e4steam.api.Registration;
import link.e4steam.api.identity.IdentityService.PeerId;
import link.e4steam.api.session.SessionService.SessionId;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Negotiated namespaced addon channels over authenticated e4steam transport. */
public interface NetworkService {
    /** Registers one channel before freeze and returns its scoped handle. */
    ApiResult<ChannelHandle> register(ChannelDescriptor descriptor, MessageHandler handler);

    /** Returns whether protocol/channel registration has frozen for this runtime generation. */
    boolean registrationsFrozen();

    /** Channel necessity during handshake. */ enum Requirement { REQUIRED, OPTIONAL }
    /** Permitted flow direction. */ enum Direction { CLIENT_TO_HOST, HOST_TO_CLIENT, BIDIRECTIONAL }
    /** Semantics guaranteed by the selected transport. */ enum Delivery { RELIABLE_ORDERED, RELIABLE_UNORDERED, UNRELIABLE }
    /** Negotiation state. */ enum ChannelState { REGISTERED, NEGOTIATING, AVAILABLE, UNAVAILABLE, CLOSED }
    /** Backpressure result. */ enum SendStatus { ACCEPTED, QUEUE_FULL, RATE_LIMITED, UNAVAILABLE, STALE_SESSION, CLOSED }

    /** Namespaced channel id. */
    final class ChannelId {
        private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9_.-]{0,31}:[a-z][a-z0-9_./-]{0,63}$");
        private final String value;
        /** Creates an id. */ public ChannelId(String value) { this.value = ApiValidation.identifier(value, "channelId", FORMAT); }
        /** Returns namespaced id. */ public String value() { return value; }
        @Override public boolean equals(Object other) { return this == other || other instanceof ChannelId && value.equals(((ChannelId) other).value); }
        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    /** Versioned bounded channel declaration. */
    final class ChannelDescriptor {
        private final ChannelId id;
        private final int minimumVersion;
        private final int maximumVersion;
        private final Requirement requirement;
        private final Direction direction;
        private final Delivery delivery;
        private final int maximumMessageBytes;
        private final int bytesPerSecond;
        private final int queueMessages;
        private final String schemaId;

        /** Creates a channel declaration with safe budgets. */
        public ChannelDescriptor(ChannelId id, int minimumVersion, int maximumVersion,
                                 Requirement requirement, Direction direction, Delivery delivery,
                                 int maximumMessageBytes, int bytesPerSecond, int queueMessages,
                                 String schemaId) {
            this.id = Objects.requireNonNull(id, "id");
            if (minimumVersion < 1 || maximumVersion < minimumVersion || maximumVersion > 65_535) {
                throw new IllegalArgumentException("invalid protocol range");
            }
            if (maximumMessageBytes < 1 || maximumMessageBytes > ApiLimits.MAX_CHANNEL_MESSAGE_BYTES) {
                throw new IllegalArgumentException("invalid maximumMessageBytes");
            }
            if (bytesPerSecond < 1_024 || bytesPerSecond > 16 * ApiLimits.MAX_CHANNEL_MESSAGE_BYTES) {
                throw new IllegalArgumentException("invalid bytesPerSecond");
            }
            if (queueMessages < 1 || queueMessages > 1_024) throw new IllegalArgumentException("invalid queueMessages");
            this.minimumVersion = minimumVersion; this.maximumVersion = maximumVersion;
            this.requirement = Objects.requireNonNull(requirement, "requirement");
            this.direction = Objects.requireNonNull(direction, "direction");
            this.delivery = Objects.requireNonNull(delivery, "delivery");
            this.maximumMessageBytes = maximumMessageBytes; this.bytesPerSecond = bytesPerSecond;
            this.queueMessages = queueMessages;
            this.schemaId = ApiValidation.text(schemaId, "schemaId", ApiLimits.MAX_IDENTIFIER_LENGTH);
            ApiValidation.rejectSensitiveName(this.schemaId, "schemaId");
        }

        /** Returns channel id. */ public ChannelId id() { return id; }
        /** Returns minimum compatible protocol version. */ public int minimumVersion() { return minimumVersion; }
        /** Returns maximum compatible protocol version. */ public int maximumVersion() { return maximumVersion; }
        /** Returns required/optional status. */ public Requirement requirement() { return requirement; }
        /** Returns allowed direction. */ public Direction direction() { return direction; }
        /** Returns delivery semantics. */ public Delivery delivery() { return delivery; }
        /** Returns maximum decoded payload size. */ public int maximumMessageBytes() { return maximumMessageBytes; }
        /** Returns byte rate budget. */ public int bytesPerSecond() { return bytesPerSecond; }
        /** Returns queue message budget. */ public int queueMessages() { return queueMessages; }
        /** Returns schema id. */ public String schemaId() { return schemaId; }
    }

    /** Incoming message context after authentication and successful negotiation. */
    final class MessageContext {
        private final SessionId sessionId;
        private final PeerId peerId;
        private final int negotiatedVersion;
        /** Creates a safe context. */ public MessageContext(SessionId sessionId, PeerId peerId, int negotiatedVersion) {
            this.sessionId = Objects.requireNonNull(sessionId, "sessionId"); this.peerId = Objects.requireNonNull(peerId, "peerId");
            if (negotiatedVersion < 1) throw new IllegalArgumentException("invalid negotiatedVersion");
            this.negotiatedVersion = negotiatedVersion;
        }
        /** Returns session. */ public SessionId sessionId() { return sessionId; }
        /** Returns opaque peer. */ public PeerId peerId() { return peerId; }
        /** Returns negotiated protocol version. */ public int negotiatedVersion() { return negotiatedVersion; }
    }

    /** Handler invoked off native callback threads with a defensive payload copy. */
    interface MessageHandler {
        /** Handles one bounded payload; exceptions close/diagnose only this channel. */
        CompletionStage<ApiResult<Boolean>> onMessage(MessageContext context, byte[] payload);
    }

    /** Generation-safe channel handle. */
    interface ChannelHandle extends Registration {
        /** Returns declaration. */ ChannelDescriptor descriptor();
        /** Returns negotiation/lifecycle state. */ ChannelState state();
        /** Sends a defensive payload after direction and negotiation checks. */
        CompletionStage<ApiResult<SendStatus>> send(SessionId sessionId, PeerId peerId, byte[] payload);
    }
}
