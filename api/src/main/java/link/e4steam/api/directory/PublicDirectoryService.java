package link.e4steam.api.directory;

import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/**
 * Generic, loader-independent bridge between a directory addon and e4steam's
 * authenticated Steam transport. The service never exposes a raw Steam auth
 * ticket, connection secret, IP address, or native Steamworks object.
 */
public interface PublicDirectoryService {
    /** Returns the current safe availability category. */
    ApiResult<DirectoryAvailability> availability();

    /** Returns a non-secret opaque target for the active host generation. */
    CompletionStage<ApiResult<PublicationTarget>> publicationTarget(PublicationTargetRequest request);

    /** Performs registry-bound publisher attestation inside core. */
    CompletionStage<ApiResult<AttestationReceipt>> attest(AttestationChallenge challenge);

    /** Begins a generation-safe connection through e4steam core. */
    CompletionStage<ApiResult<JoinOperation>> join(JoinRequest request);

    /** Cancels a pending join idempotently and closes its temporary resources. */
    CompletionStage<ApiResult<JoinOperation>> cancel(JoinOperationId operationId);

    /** Returns a safe snapshot for one join operation. */
    ApiResult<JoinOperation> joinSnapshot(JoinOperationId operationId);

    /** Safe reason why this bridge can or cannot currently operate. */
    enum DirectoryAvailability {
        AVAILABLE,
        NO_ACTIVE_SESSION,
        STEAM_UNAVAILABLE,
        UNSUPPORTED
    }

    /** Type of Steam-backed target represented by a directory entry. */
    enum TargetKind { INTEGRATED_WORLD, DEDICATED_SERVER }

    /** Registry action to which an attestation is cryptographically bound. */
    enum AttestationAction { PUBLISH_INTEGRATED, PUBLISH_DEDICATED, UPDATE, DELETE }

    /** Public join lifecycle. */
    enum JoinState {
        IDLE,
        RESOLVING,
        JOINING_STEAM_TARGET,
        AUTHENTICATING,
        CONNECTING_MINECRAFT,
        ACTIVE,
        CANCELLED,
        STALE,
        FULL,
        INCOMPATIBLE,
        FAILED
    }

    /**
     * Canonical origin of one registry, without path, query or credentials.
     * HTTPS is mandatory except for an explicit loopback-only development registry.
     */
    final class DirectoryOrigin {
        private final String value;

        /** Creates and canonicalizes a secure registry origin. */
        public DirectoryOrigin(String value) {
            String checked = ApiValidation.text(value, "directoryOrigin", 256);
            try {
                URI uri = new URI(checked);
                boolean secure = "https".equalsIgnoreCase(uri.getScheme());
                boolean loopbackDevelopment = "http".equalsIgnoreCase(uri.getScheme())
                        && loopbackLiteral(uri.getHost());
                if ((!secure && !loopbackDevelopment)
                        || uri.getHost() == null
                        || uri.getHost().isEmpty()
                        || uri.getUserInfo() != null
                        || uri.getQuery() != null
                        || uri.getFragment() != null
                        || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
                    throw new IllegalArgumentException(
                            "directoryOrigin must be HTTPS or loopback HTTP");
                }
                int port = uri.getPort();
                this.value = new URI(secure ? "https" : "http", null,
                        uri.getHost().toLowerCase(java.util.Locale.ROOT),
                        port, null, null, null).toASCIIString();
            } catch (URISyntaxException failure) {
                throw new IllegalArgumentException("directoryOrigin has an invalid format");
            }
        }

        /** Returns the canonical registry origin. */ public String value() { return value; }
        @Override public boolean equals(Object other) { return this == other || other instanceof DirectoryOrigin && value.equals(((DirectoryOrigin) other).value); }
        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return "DirectoryOrigin{" + value + '}'; }

        private static boolean loopbackLiteral(String host) {
            return host != null && (host.equalsIgnoreCase("localhost")
                    || host.equals("127.0.0.1") || host.equals("::1")
                    || host.equals("0:0:0:0:0:0:0:1"));
        }
    }

    /** Opaque, non-secret connection target understood only by the core bridge. */
    final class OpaqueTargetRef {
        private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{12,512}$");
        private final String value;
        /** Creates a bounded opaque target reference. */ public OpaqueTargetRef(String value) { this.value = checked(value, "targetRef", FORMAT); }
        /** Returns the opaque value for registry transport. */ public String value() { return value; }
        @Override public boolean equals(Object other) { return this == other || other instanceof OpaqueTargetRef && value.equals(((OpaqueTargetRef) other).value); }
        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return "OpaqueTargetRef{redacted}"; }
    }

    /** Requests the active target of one host type for one registry. */
    final class PublicationTargetRequest {
        private final TargetKind kind;
        private final DirectoryOrigin origin;
        /** Creates a request. */ public PublicationTargetRequest(TargetKind kind, DirectoryOrigin origin) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.origin = Objects.requireNonNull(origin, "origin");
        }
        /** Returns target kind. */ public TargetKind kind() { return kind; }
        /** Returns registry origin. */ public DirectoryOrigin origin() { return origin; }
    }

    /** Generation-bound safe publication target. */
    final class PublicationTarget {
        private final TargetKind kind;
        private final OpaqueTargetRef targetRef;
        private final long generation;
        /** Creates a target. */ public PublicationTarget(TargetKind kind, OpaqueTargetRef targetRef, long generation) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.targetRef = Objects.requireNonNull(targetRef, "targetRef");
            if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
            this.generation = generation;
        }
        /** Returns target kind. */ public TargetKind kind() { return kind; }
        /** Returns opaque target. */ public OpaqueTargetRef targetRef() { return targetRef; }
        /** Returns runtime generation. */ public long generation() { return generation; }
        @Override public String toString() { return "PublicationTarget{kind=" + kind + ", generation=" + generation + '}'; }
    }

    /** One registry challenge; core binds proof to every field and the expiry. */
    final class AttestationChallenge {
        // Registry-generated values are namespaced (for example `chal.` and
        // `nonce.`), so the public boundary must accept the protocol's dot
        // separator while still rejecting whitespace, paths and credentials.
        private static final Pattern OPAQUE = Pattern.compile("^[A-Za-z0-9._-]{16,256}$");
        private final DirectoryOrigin origin;
        private final String challengeId;
        private final String nonce;
        private final AttestationAction action;
        private final long expiresAtEpochMillis;
        /** Creates a bounded challenge. */
        public AttestationChallenge(DirectoryOrigin origin, String challengeId, String nonce,
                                    AttestationAction action, long expiresAtEpochMillis) {
            this.origin = Objects.requireNonNull(origin, "origin");
            this.challengeId = checked(challengeId, "challengeId", OPAQUE);
            this.nonce = checked(nonce, "nonce", OPAQUE);
            this.action = Objects.requireNonNull(action, "action");
            if (expiresAtEpochMillis <= 0L) throw new IllegalArgumentException("invalid expiry");
            this.expiresAtEpochMillis = expiresAtEpochMillis;
        }
        /** Returns registry origin. */ public DirectoryOrigin origin() { return origin; }
        /** Returns opaque challenge id. */ public String challengeId() { return challengeId; }
        /** Returns one-use nonce. */ public String nonce() { return nonce; }
        /** Returns bound action. */ public AttestationAction action() { return action; }
        /** Returns absolute expiry. */ public long expiresAtEpochMillis() { return expiresAtEpochMillis; }
        @Override public String toString() { return "AttestationChallenge{origin=" + origin + ", action=" + action + ", expires=" + expiresAtEpochMillis + '}'; }
    }

    /** Registry-issued, bounded receipt; never contains the raw Steam ticket. */
    final class AttestationReceipt {
        private static final Pattern OPAQUE = Pattern.compile("^[A-Za-z0-9._:-]{12,512}$");
        private final String ownerRef;
        private final String receiptRef;
        private final long expiresAtEpochMillis;
        /** Creates a receipt. */ public AttestationReceipt(String ownerRef, String receiptRef, long expiresAtEpochMillis) {
            this.ownerRef = checked(ownerRef, "ownerRef", OPAQUE);
            this.receiptRef = checked(receiptRef, "receiptRef", OPAQUE);
            if (expiresAtEpochMillis <= 0L) throw new IllegalArgumentException("invalid expiry");
            this.expiresAtEpochMillis = expiresAtEpochMillis;
        }
        /** Returns privacy-safe owner reference. */ public String ownerRef() { return ownerRef; }
        /** Returns one-use registry receipt. */ public String receiptRef() { return receiptRef; }
        /** Returns receipt expiry. */ public long expiresAtEpochMillis() { return expiresAtEpochMillis; }
        @Override public String toString() { return "AttestationReceipt{redacted, expires=" + expiresAtEpochMillis + '}'; }
    }

    /** Opaque one-use handle minted by the registry. */
    final class JoinHandleRef {
        // Registry handles use a namespaced `join.` prefix. Keep the value
        // opaque while accepting the protocol separator used by the registry.
        private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9._-]{16,256}$");
        private final String value;
        /** Creates a handle reference. */ public JoinHandleRef(String value) { this.value = checked(value, "joinHandle", FORMAT); }
        /** Returns the opaque value for core. */ public String value() { return value; }
        @Override public boolean equals(Object other) { return this == other || other instanceof JoinHandleRef && value.equals(((JoinHandleRef) other).value); }
        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return "JoinHandleRef{redacted}"; }
    }

    /** Requests a core-controlled join. */
    final class JoinRequest {
        private final OpaqueTargetRef target;
        private final JoinHandleRef handle;
        /** Creates a request. */ public JoinRequest(OpaqueTargetRef target, JoinHandleRef handle) {
            this.target = Objects.requireNonNull(target, "target");
            this.handle = Objects.requireNonNull(handle, "handle");
        }
        /** Returns opaque target. */ public OpaqueTargetRef target() { return target; }
        /** Returns one-use handle. */ public JoinHandleRef handle() { return handle; }
        @Override public String toString() { return "JoinRequest{redacted}"; }
    }

    /** Opaque local operation id, not a connection credential. */
    final class JoinOperationId {
        private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{12,96}$");
        private final String value;
        /** Creates an operation id. */ public JoinOperationId(String value) { this.value = checked(value, "operationId", FORMAT); }
        /** Returns the local id. */ public String value() { return value; }
        @Override public boolean equals(Object other) { return this == other || other instanceof JoinOperationId && value.equals(((JoinOperationId) other).value); }
        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return "JoinOperationId{" + value + '}'; }
    }

    /** Immutable, sanitized join snapshot. */
    final class JoinOperation {
        private final JoinOperationId id;
        private final JoinState state;
        private final String detailCode;
        /** Creates a snapshot. */ public JoinOperation(JoinOperationId id, JoinState state, String detailCode) {
            this.id = Objects.requireNonNull(id, "id");
            this.state = Objects.requireNonNull(state, "state");
            String detail = ApiValidation.optionalText(detailCode, "detailCode", 96);
            if (!detail.isEmpty() && !detail.matches("[a-z][a-z0-9_.-]{0,95}")) {
                throw new IllegalArgumentException("detailCode has an invalid format");
            }
            this.detailCode = detail;
        }
        /** Returns operation id. */ public JoinOperationId id() { return id; }
        /** Returns current state. */ public JoinState state() { return state; }
        /** Returns a localization-safe detail code. */ public String detailCode() { return detailCode; }
        @Override public String toString() { return "JoinOperation{id=" + id + ", state=" + state + ", detail=" + detailCode + '}'; }
    }

    /** Validates an opaque wire value without interpreting it. */
    static String checked(String value, String field, Pattern pattern) {
        if (value == null) throw new NullPointerException(field);
        String checked = value.trim();
        if (checked.length() > 512 || !pattern.matcher(checked).matches()) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return checked;
    }
}
