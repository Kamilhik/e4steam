package link.e4steam.api.dedicated;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Headless dedicated-server observation and capability-checked administration. */
public interface DedicatedServerService {
    /** Returns a safe snapshot with no login token, auth ticket or credential-bearing descriptor. */
    ApiResult<DedicatedServerSnapshot> snapshot();

    /** Returns the validated configuration with secret values removed. */
    ApiResult<DedicatedConfigSnapshot> config();

    /** Waits for transport, ingress guard and Minecraft readiness. */
    CompletionStage<ApiResult<DedicatedServerSnapshot>> readiness();

    /** Requests a capability-checked graceful drain and stop. */
    CompletionStage<ApiResult<DedicatedServerSnapshot>> drain(String safeReasonCode);

    /** Proposes bounded publication; core/user config can still deny it. */
    CompletionStage<ApiResult<PublicationPlan>> proposePublication(PublicationProposal proposal);

    /** Dedicated state machine. */
    enum DedicatedServerState {
        OFF, CONFIG_VALIDATED, NATIVES_READY, STEAM_INITIALIZING, STEAM_LOGGING_ON,
        TRANSPORT_READY, MINECRAFT_READY, ACCEPTING, DRAINING, STOPPED, FAILED
    }

    /** Built-in dedicated access modes; public requires an external provider. */
    enum DedicatedAccessMode { PRIVATE, WHITELIST, UNLISTED, CUSTOM }

    /** Non-player reference to server-process authority. */
    final class ServerAuthorityRef {
        private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{8,96}$");
        private final String opaqueId;
        private final long generation;

        /** Creates a generation-bound non-credential authority reference. */
        public ServerAuthorityRef(String opaqueId, long generation) {
            this.opaqueId = ApiValidation.identifier(opaqueId, "authorityId", FORMAT);
            if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
            this.generation = generation;
        }

        /** Returns an opaque non-player id. */ public String opaqueId() { return opaqueId; }
        /** Returns the runtime generation. */ public long generation() { return generation; }
        @Override public String toString() { return "ServerAuthorityRef{generation=" + generation + '}'; }
    }

    /** Privacy-safe dedicated runtime snapshot. */
    final class DedicatedServerSnapshot {
        private final DedicatedServerState state;
        private final ServerAuthorityRef authority;
        private final DedicatedAccessMode accessMode;
        private final boolean ingressGuarded;
        private final boolean publicationActive;
        private final int players;
        private final int capacity;
        private final String failureCategory;

        /** Creates an immutable dedicated snapshot. */
        public DedicatedServerSnapshot(DedicatedServerState state, ServerAuthorityRef authority,
                                       DedicatedAccessMode accessMode, boolean ingressGuarded,
                                       boolean publicationActive, int players, int capacity,
                                       String failureCategory) {
            this.state = Objects.requireNonNull(state, "state");
            this.authority = Objects.requireNonNull(authority, "authority");
            this.accessMode = Objects.requireNonNull(accessMode, "accessMode");
            if (players < 0 || capacity < 1 || players > capacity || capacity > 256) {
                throw new IllegalArgumentException("invalid capacity");
            }
            if (state == DedicatedServerState.ACCEPTING && !ingressGuarded) {
                throw new IllegalArgumentException("accepting requires ingress guard");
            }
            this.ingressGuarded = ingressGuarded;
            this.publicationActive = publicationActive;
            this.players = players;
            this.capacity = capacity;
            this.failureCategory = ApiValidation.optionalText(failureCategory, "failureCategory", 96);
        }

        /** Returns lifecycle state. */ public DedicatedServerState state() { return state; }
        /** Returns process authority, never a player. */ public ServerAuthorityRef authority() { return authority; }
        /** Returns access mode. */ public DedicatedAccessMode accessMode() { return accessMode; }
        /** Returns whether direct vanilla ingress is fail-closed. */ public boolean ingressGuarded() { return ingressGuarded; }
        /** Returns whether an approved external publication is active. */ public boolean publicationActive() { return publicationActive; }
        /** Returns player count. */ public int players() { return players; }
        /** Returns capacity. */ public int capacity() { return capacity; }
        /** Returns a sanitized failure category. */ public String failureCategory() { return failureCategory; }

        @Override public String toString() {
            return "DedicatedServerSnapshot{state=" + state + ", guarded=" + ingressGuarded
                    + ", publication=" + publicationActive + ", players=" + players + '/' + capacity + '}';
        }
    }

    /** Secret-free typed dedicated configuration. */
    final class DedicatedConfigSnapshot {
        private final int schemaVersion;
        private final DedicatedAccessMode accessMode;
        private final int maxPeers;
        private final boolean publicationEnabled;
        private final String loginMode;

        /** Creates a redacted configuration snapshot. */
        public DedicatedConfigSnapshot(int schemaVersion, DedicatedAccessMode accessMode, int maxPeers,
                                       boolean publicationEnabled, String loginMode) {
            if (schemaVersion < 1) throw new IllegalArgumentException("invalid schemaVersion");
            if (maxPeers < 1 || maxPeers > 256) throw new IllegalArgumentException("invalid maxPeers");
            this.schemaVersion = schemaVersion;
            this.accessMode = Objects.requireNonNull(accessMode, "accessMode");
            this.maxPeers = maxPeers;
            this.publicationEnabled = publicationEnabled;
            this.loginMode = ApiValidation.text(loginMode, "loginMode", 32);
        }

        /** Returns schema version. */ public int schemaVersion() { return schemaVersion; }
        /** Returns access mode. */ public DedicatedAccessMode accessMode() { return accessMode; }
        /** Returns maximum Steam peers. */ public int maxPeers() { return maxPeers; }
        /** Returns explicit publication opt-in. */ public boolean publicationEnabled() { return publicationEnabled; }
        /** Returns ANONYMOUS or a redacted secret-source mode name. */ public String loginMode() { return loginMode; }

        @Override public String toString() {
            return "DedicatedConfigSnapshot{schema=" + schemaVersion + ", access=" + accessMode
                    + ", maxPeers=" + maxPeers + ", publication=" + publicationEnabled
                    + ", loginMode=" + loginMode + '}';
        }
    }

    /** Safe external publication proposal with no credentials or raw IP. */
    final class PublicationProposal {
        private final String providerId;
        private final String displayName;
        private final Set<String> tags;

        /** Creates a bounded publication proposal. */
        public PublicationProposal(String providerId, String displayName, Set<String> tags) {
            this.providerId = ApiValidation.text(providerId, "providerId", ApiLimits.MAX_IDENTIFIER_LENGTH);
            this.displayName = ApiValidation.text(displayName, "displayName", ApiLimits.MAX_DISPLAY_NAME_LENGTH);
            if (tags == null || tags.size() > 16) throw new IllegalArgumentException("invalid tags");
            LinkedHashSet<String> copy = new LinkedHashSet<>();
            for (String tag : tags) copy.add(ApiValidation.text(tag, "tag", 32));
            this.tags = Collections.unmodifiableSet(copy);
        }

        /** Returns provider id. */ public String providerId() { return providerId; }
        /** Returns bounded display name. */ public String displayName() { return displayName; }
        /** Returns immutable public tags. */ public Set<String> tags() { return tags; }
    }

    /** Immutable core-validated plan that still requires explicit application. */
    final class PublicationPlan {
        private final boolean allowed;
        private final String reasonCode;

        /** Creates a publication plan. */
        public PublicationPlan(boolean allowed, String reasonCode) {
            this.allowed = allowed;
            this.reasonCode = ApiValidation.optionalText(reasonCode, "reasonCode", 96);
        }

        /** Returns whether core/config/capability validation permits publication. */ public boolean allowed() { return allowed; }
        /** Returns a safe denial code or empty text. */ public String reasonCode() { return reasonCode; }
    }
}
