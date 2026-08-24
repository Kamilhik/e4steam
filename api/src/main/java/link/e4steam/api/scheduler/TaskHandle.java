package link.e4steam.api.scheduler;

import link.e4steam.api.ApiResult;
import link.e4steam.api.Registration;

import java.util.concurrent.CompletionStage;

/** Idempotently cancellable handle for one bounded addon task. */
public interface TaskHandle extends Registration {
    /** Returns the latest task state. */
    TaskState state();

    /** Returns a completion stage containing success or a sanitized error. */
    CompletionStage<ApiResult<TaskState>> completion();
}
