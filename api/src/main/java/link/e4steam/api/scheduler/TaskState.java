package link.e4steam.api.scheduler;

/** Observable state of an addon-owned scheduled task. */
public enum TaskState {
    /** Accepted into a bounded queue. */
    QUEUED,
    /** Callback is running outside internal locks and native threads. */
    RUNNING,
    /** Callback completed successfully. */
    COMPLETED,
    /** Callback failed in isolation. */
    FAILED,
    /** Caller or parent scope cancelled the task. */
    CANCELLED,
    /** Callback exceeded its declared time budget. */
    TIMED_OUT,
    /** Bounded executor rejected the task before it ran. */
    REJECTED
}
