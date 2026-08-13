package link.e4steam.api.access;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;
import link.e4steam.api.Registration;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.identity.IdentityService.PeerId;
import link.e4steam.api.session.SessionService.SessionId;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Access-mode providers evaluated only after mandatory core security gates. */
public interface AccessService {
    /** Registers one namespaced custom mode before the registration freeze point. */
    ApiResult<Registration> register(AccessModeProvider provider);

    /** Requests evaluation of an already core-authenticated context. */
    CompletionStage<ApiResult<AdmissionDecision>> evaluate(AccessModeId mode, AdmissionContext context);

    /** Returns whether mandatory registration contracts have frozen. */
    boolean registrationsFrozen();

    /** Namespaced custom mode id or one of the documented core ids. */
    final class AccessModeId {
        private static final Pattern FORMAT = Pattern.compile(
                "^[a-z][a-z0-9_.-]{0,31}:[a-z][a-z0-9_.-]{0,63}$");
        private final String value;

        /** Creates a validated access-mode id. */
        public AccessModeId(String value) {
            this.value = ApiValidation.identifier(value, "accessModeId", FORMAT);
        }

        /** Returns the namespaced id. */ public String value() { return value; }
        @Override public boolean equals(Object other) { return this == other || other instanceof AccessModeId && value.equals(((AccessModeId) other).value); }
        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    /** Provider owned by one addon; callback timeouts/exceptions fail closed. */
    interface AccessModeProvider {
        /** Returns the registered mode id. */ AccessModeId id();
        /** Returns a bounded localization key. */ String displayNameKey();
        /** Returns the fail-closed policy. */ AdmissionPolicy policy();
    }

    /** Policy called after authentication, generation, protocol, capacity, ban and replay gates. */
    interface AdmissionPolicy {
        /** Produces an allow/deny/challenge decision within the core timeout. */
        CompletionStage<AdmissionDecision> evaluate(AdmissionContext context);
    }

    /** Minimal immutable context that contains no ticket, invite token or native identity. */
    final class AdmissionContext {
        private final SessionId sessionId;
        private final PeerId peerId;
        private final AddonId modeOwner;
        private final boolean coreAuthenticated;

        /** Creates an admission context after mandatory gates. */
        public AdmissionContext(SessionId sessionId, PeerId peerId, AddonId modeOwner,
                                boolean coreAuthenticated) {
            this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
            this.peerId = Objects.requireNonNull(peerId, "peerId");
            this.modeOwner = Objects.requireNonNull(modeOwner, "modeOwner");
            if (!coreAuthenticated) {
                throw new IllegalArgumentException("custom policy cannot receive an unauthenticated peer");
            }
            this.coreAuthenticated = true;
        }

        /** Returns the generation-bound session. */ public SessionId sessionId() { return sessionId; }
        /** Returns the opaque peer. */ public PeerId peerId() { return peerId; }
        /** Returns the provider owner. */ public AddonId modeOwner() { return modeOwner; }
        /** Always true because core gates run first. */ public boolean coreAuthenticated() { return coreAuthenticated; }

        @Override public String toString() { return "AdmissionContext{session=" + sessionId + ", peer=" + peerId + '}'; }
    }

    /** Fail-closed custom policy decision. */
    final class AdmissionDecision {
        /** Decision kind. */ public enum Kind { ALLOW, DENY, CHALLENGE }
        private final Kind kind;
        private final String reasonCode;
        private final String challengeId;
        private final long expiresInMillis;

        private AdmissionDecision(Kind kind, String reasonCode, String challengeId, long expiresInMillis) {
            this.kind = kind;
            this.reasonCode = reasonCode;
            this.challengeId = challengeId;
            this.expiresInMillis = expiresInMillis;
        }

        /** Creates an allow decision that cannot override a core rejection. */
        public static AdmissionDecision allow() {
            return new AdmissionDecision(Kind.ALLOW, "", "", 0L);
        }

        /** Creates a safe denial code. */
        public static AdmissionDecision deny(String reasonCode) {
            return new AdmissionDecision(Kind.DENY,
                    ApiValidation.text(reasonCode, "reasonCode", 96), "", 0L);
        }

        /** Creates an opaque bounded asynchronous challenge. */
        public static AdmissionDecision challenge(String challengeId, long expiresInMillis) {
            String id = ApiValidation.text(challengeId, "challengeId", ApiLimits.MAX_IDENTIFIER_LENGTH);
            ApiValidation.rejectSensitiveName(id, "challengeId");
            if (expiresInMillis <= 0L || expiresInMillis > ApiLimits.MAX_OPERATION_TIMEOUT_MILLIS) {
                throw new IllegalArgumentException("invalid challenge expiry");
            }
            return new AdmissionDecision(Kind.CHALLENGE, "", id, expiresInMillis);
        }

        /** Returns the decision kind. */ public Kind kind() { return kind; }
        /** Returns a safe denial code or empty text. */ public String reasonCode() { return reasonCode; }
        /** Returns an opaque non-secret challenge id or empty text. */ public String challengeId() { return challengeId; }
        /** Returns bounded expiry for a challenge. */ public long expiresInMillis() { return expiresInMillis; }

        @Override public String toString() { return "AdmissionDecision{" + kind + (reasonCode.isEmpty() ? "" : ", reason=" + reasonCode) + '}'; }
    }
}
