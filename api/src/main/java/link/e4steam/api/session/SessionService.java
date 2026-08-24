package link.e4steam.api.session;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;
import link.e4steam.api.Registration;
import link.e4steam.api.identity.IdentityService.PeerId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Generation-safe observation and controlled actions for the current e4steam session. */
public interface SessionService {
    /** Returns the current immutable session snapshot. */
    ApiResult<SessionSnapshot> snapshot();

    /** Returns a bounded page of opaque authenticated peers. */
    CompletionStage<ApiResult<PeerPage>> peers(SessionId sessionId, String cursor, int limit);

    /** Requests a graceful disconnect; capability and generation are checked at use time. */
    CompletionStage<ApiResult<SessionSnapshot>> disconnect(SessionId sessionId, String safeReasonCode);

    /** Completes when the current generation becomes active. */
    CompletionStage<ApiResult<SessionSnapshot>> readiness();

    /** Registers a child resource closed automatically with this session generation. */
    ApiResult<Registration> registerResource(SessionId sessionId, Registration resource);

    /** Process role inside a session. */
    enum SessionRole { NONE, INTEGRATED_HOST, GUEST, DEDICATED_SERVER_CLIENT, DEDICATED_SERVER }

    /** Session lifecycle state. */
    enum SessionState { NONE, CREATING, ACTIVE, RECONNECTING, CLOSING, CLOSED, FAILED }

    /** Opaque id bound to one immutable generation. */
    final class SessionId {
        private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{8,96}$");
        private final String value;
        private final long generation;

        /** Creates a generation-bound session id. */
        public SessionId(String value, long generation) {
            this.value = ApiValidation.identifier(value, "sessionId", FORMAT);
            if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
            this.generation = generation;
        }

        /** Returns the opaque id. */ public String value() { return value; }
        /** Returns the monotonically changing generation. */ public long generation() { return generation; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SessionId)) return false;
            SessionId id = (SessionId) other;
            return generation == id.generation && value.equals(id.value);
        }

        @Override public int hashCode() { return 31 * value.hashCode() + (int) (generation ^ generation >>> 32); }
        @Override public String toString() { return "SessionId{generation=" + generation + '}'; }
    }

    /** Immutable safe snapshot with negotiated feature ids only. */
    final class SessionSnapshot {
        private final SessionId id;
        private final SessionRole role;
        private final SessionState state;
        private final int peers;
        private final int capacity;
        private final Set<String> features;

        /** Creates a bounded session snapshot. */
        public SessionSnapshot(SessionId id, SessionRole role, SessionState state,
                               int peers, int capacity, Set<String> features) {
            this.id = Objects.requireNonNull(id, "id");
            this.role = Objects.requireNonNull(role, "role");
            this.state = Objects.requireNonNull(state, "state");
            if (peers < 0 || capacity < 0 || peers > capacity || capacity > 256) {
                throw new IllegalArgumentException("invalid peer capacity");
            }
            this.peers = peers;
            this.capacity = capacity;
            LinkedHashSet<String> copy = new LinkedHashSet<>();
            if (features == null || features.size() > ApiLimits.MAX_MAP_ENTRIES) {
                throw new IllegalArgumentException("invalid feature set");
            }
            for (String feature : features) {
                copy.add(ApiValidation.text(feature, "feature", ApiLimits.MAX_IDENTIFIER_LENGTH));
            }
            this.features = Collections.unmodifiableSet(copy);
        }

        /** Returns the generation-bound id. */ public SessionId id() { return id; }
        /** Returns this process's role. */ public SessionRole role() { return role; }
        /** Returns lifecycle state. */ public SessionState state() { return state; }
        /** Returns authenticated peer count. */ public int peers() { return peers; }
        /** Returns current capacity. */ public int capacity() { return capacity; }
        /** Returns immutable negotiated feature ids. */ public Set<String> features() { return features; }

        @Override public String toString() {
            return "SessionSnapshot{id=" + id + ", role=" + role + ", state=" + state
                    + ", peers=" + peers + '/' + capacity + '}';
        }
    }

    /** One privacy-safe peer list row. */
    final class PeerSnapshot {
        private final PeerId peerId;
        private final boolean authenticated;

        /** Creates a peer row; unauthenticated peers are never returned as admitted. */
        public PeerSnapshot(PeerId peerId, boolean authenticated) {
            this.peerId = Objects.requireNonNull(peerId, "peerId");
            this.authenticated = authenticated;
        }

        /** Returns the opaque peer id. */ public PeerId peerId() { return peerId; }
        /** Returns whether mandatory core authentication completed. */ public boolean authenticated() { return authenticated; }
    }

    /** Bounded opaque peer page. */
    final class PeerPage {
        private final List<PeerSnapshot> peers;
        private final String nextCursor;

        /** Creates a defensive page. */
        public PeerPage(List<PeerSnapshot> peers, String nextCursor) {
            this.peers = ApiValidation.immutableList(peers, ApiLimits.MAX_PAGE_SIZE, "peers");
            this.nextCursor = ApiValidation.optionalText(nextCursor, "nextCursor", 256);
        }

        /** Returns immutable rows. */ public List<PeerSnapshot> peers() { return peers; }
        /** Returns an opaque next cursor or empty text. */ public String nextCursor() { return nextCursor; }
    }
}
