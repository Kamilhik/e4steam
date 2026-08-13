package link.e4steam.api.event;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiValidation;

/** Bounded observational event for non-cancellable lifecycle categories. */
public final class LifecycleEvent implements ApiEvent {
    /** Typed event key. */ public static final EventType<LifecycleEvent> TYPE = new EventType<>("e4steam:lifecycle", LifecycleEvent.class);
    /** Stable event category. */
    public enum Kind {
        RUNTIME_FAILURE, ADDON_ACTIVATED, ADDON_DISABLED, ADDON_FAILED,
        WORLD_OPENING, WORLD_OPENED, WORLD_CLOSING, WORLD_CLOSED,
        LOBBY_CREATED, LOBBY_UPDATED, LOBBY_JOINED, LOBBY_LEFT,
        PEER_DISCOVERED, PEER_AUTHENTICATING, PEER_ACCEPTED, PEER_REJECTED, PEER_DISCONNECTED,
        ACCESS_EVALUATION_STARTED, ACCESS_EVALUATION_COMPLETED,
        CHANNEL_NEGOTIATED, CHANNEL_UNAVAILABLE, CHANNEL_CLOSED,
        CONFIG_CHANGED, WORLD_SETTINGS_PLAN_CREATED, WORLD_SETTINGS_APPLIED, WORLD_SETTINGS_REJECTED,
        MODPACK_MANIFEST_AVAILABLE, MODPACK_COMPATIBILITY_RESULT,
        SKIN_REQUESTED, SKIN_RESOLVED, SKIN_REJECTED, SKIN_CACHE_UPDATED,
        DIAGNOSTICS_WARNING, DIAGNOSTICS_HEALTH_CHANGED
    }
    private final long occurredAtEpochMillis; private final Kind kind; private final String subjectId; private final String safeCode;
    /** Creates a privacy-safe event. */ public LifecycleEvent(long occurredAtEpochMillis, Kind kind, String subjectId, String safeCode) { if (occurredAtEpochMillis < 0) throw new IllegalArgumentException("invalid time"); if (kind == null) throw new NullPointerException("kind"); this.occurredAtEpochMillis = occurredAtEpochMillis; this.kind = kind; this.subjectId = ApiValidation.optionalText(subjectId, "subjectId", ApiLimits.MAX_IDENTIFIER_LENGTH); this.safeCode = ApiValidation.optionalText(safeCode, "safeCode", 96); ApiValidation.rejectSensitiveName(this.subjectId, "subjectId"); }
    @Override public long occurredAtEpochMillis() { return occurredAtEpochMillis; }
    /** Returns category. */ public Kind kind() { return kind; }
    /** Returns opaque/namespaced subject id. */ public String subjectId() { return subjectId; }
    /** Returns safe machine-readable result code. */ public String safeCode() { return safeCode; }
    @Override public String toString() { return "LifecycleEvent{kind=" + kind + ", subject='" + subjectId + "', code='" + safeCode + "'}"; }
}
