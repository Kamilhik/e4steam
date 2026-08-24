package link.e4steam.api.event;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiValidation;

/** Coalescible bounded progress event for staging and other long operations. */
public final class ProgressEvent implements ApiEvent {
    /** Typed event key. */ public static final EventType<ProgressEvent> TYPE = new EventType<>("e4steam:progress", ProgressEvent.class);
    private final long occurredAtEpochMillis; private final String operationId; private final long completed; private final long total; private final String safeCode;
    /** Creates a progress event. */ public ProgressEvent(long occurredAtEpochMillis, String operationId, long completed, long total, String safeCode) { if (occurredAtEpochMillis < 0 || completed < 0 || total < 0 || completed > total) throw new IllegalArgumentException("invalid progress"); this.occurredAtEpochMillis = occurredAtEpochMillis; this.operationId = ApiValidation.text(operationId, "operationId", ApiLimits.MAX_IDENTIFIER_LENGTH); ApiValidation.rejectSensitiveName(this.operationId, "operationId"); this.completed = completed; this.total = total; this.safeCode = ApiValidation.optionalText(safeCode, "safeCode", 96); }
    @Override public long occurredAtEpochMillis() { return occurredAtEpochMillis; }
    /** Returns opaque operation id. */ public String operationId() { return operationId; }
    /** Returns completed units. */ public long completed() { return completed; }
    /** Returns total units. */ public long total() { return total; }
    /** Returns safe status code. */ public String safeCode() { return safeCode; }
}
