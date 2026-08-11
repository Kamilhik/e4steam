package link.e4steam.api.runtime;

import java.util.concurrent.CompletionStage;

/** Safe runtime snapshots and non-busy-waiting readiness notification. */
public interface RuntimeService {
    /** Returns the latest immutable snapshot and is safe before Steam startup. */
    RuntimeSnapshot snapshot();

    /**
     * Completes when the current runtime generation becomes ready, or fails
     * with a sanitized implementation exception category.
     */
    CompletionStage<RuntimeSnapshot> readiness();
}
