package link.e4steam.api.udp;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;
import link.e4steam.api.Registration;
import link.e4steam.api.identity.IdentityService.PeerId;
import link.e4steam.api.session.SessionService.SessionId;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Session-authenticated virtual datagrams; no raw socket or arbitrary interface is exposed. */
public interface UdpService {
    /** Registers one namespaced virtual provider before negotiation freeze. */
    ApiResult<EndpointHandle> register(EndpointDescriptor descriptor, DatagramHandler handler);

    /** Namespaced virtual endpoint id. */
    final class EndpointId {
        private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9_.-]{0,31}:[a-z][a-z0-9_./-]{0,63}$");
        private final String value;
        /** Creates an endpoint id. */ public EndpointId(String value) { this.value = ApiValidation.identifier(value, "endpointId", FORMAT); }
        /** Returns namespaced id. */ public String value() { return value; }
        @Override public boolean equals(Object other) { return this == other || other instanceof EndpointId && value.equals(((EndpointId) other).value); }
        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    /** Virtual endpoint declaration and quotas. */
    final class EndpointDescriptor {
        private final EndpointId id; private final int maximumDatagramBytes; private final int datagramsPerSecond; private final String discriminator;
        /** Creates a bounded descriptor. */
        public EndpointDescriptor(EndpointId id, int maximumDatagramBytes, int datagramsPerSecond, String discriminator) {
            this.id = Objects.requireNonNull(id, "id");
            if (maximumDatagramBytes < 1 || maximumDatagramBytes > ApiLimits.MAX_DATAGRAM_BYTES) throw new IllegalArgumentException("invalid maximumDatagramBytes");
            if (datagramsPerSecond < 1 || datagramsPerSecond > 1_000) throw new IllegalArgumentException("invalid datagramsPerSecond");
            this.maximumDatagramBytes = maximumDatagramBytes; this.datagramsPerSecond = datagramsPerSecond;
            this.discriminator = ApiValidation.optionalText(discriminator, "discriminator", 64);
            ApiValidation.rejectSensitiveName(this.discriminator, "discriminator");
        }
        /** Returns id. */ public EndpointId id() { return id; }
        /** Returns payload limit. */ public int maximumDatagramBytes() { return maximumDatagramBytes; }
        /** Returns rate quota. */ public int datagramsPerSecond() { return datagramsPerSecond; }
        /** Returns optional protocol discriminator. */ public String discriminator() { return discriminator; }
    }

    /** Defensive session-scoped virtual datagram. */
    final class Datagram {
        private final SessionId sessionId; private final PeerId peerId; private final EndpointId endpointId; private final byte[] payload;
        /** Creates a bounded datagram. */
        public Datagram(SessionId sessionId, PeerId peerId, EndpointId endpointId, byte[] payload) {
            this.sessionId = Objects.requireNonNull(sessionId, "sessionId"); this.peerId = Objects.requireNonNull(peerId, "peerId");
            this.endpointId = Objects.requireNonNull(endpointId, "endpointId"); this.payload = ApiValidation.bytes(payload, ApiLimits.MAX_DATAGRAM_BYTES, "payload");
        }
        /** Returns session. */ public SessionId sessionId() { return sessionId; }
        /** Returns authenticated source/destination peer. */ public PeerId peerId() { return peerId; }
        /** Returns endpoint. */ public EndpointId endpointId() { return endpointId; }
        /** Returns defensive payload. */ public byte[] payload() { return payload.clone(); }
        @Override public String toString() { return "Datagram{endpoint=" + endpointId + ", bytes=" + payload.length + '}'; }
    }

    /** Handler invoked only after channel negotiation. */
    interface DatagramHandler { /** Handles one datagram. */ CompletionStage<ApiResult<Boolean>> onDatagram(Datagram datagram); }

    /** Generation-safe virtual endpoint handle. */
    interface EndpointHandle extends Registration {
        /** Returns descriptor. */ EndpointDescriptor descriptor();
        /** Returns readiness. */ boolean ready();
        /** Sends a datagram without opening an OS socket. */ CompletionStage<ApiResult<Boolean>> send(Datagram datagram);
    }
}
