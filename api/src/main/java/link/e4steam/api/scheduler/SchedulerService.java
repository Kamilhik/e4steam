package link.e4steam.api.scheduler;

import link.e4steam.api.ApiResult;

import java.time.Duration;

/** Capability-scoped bounded scheduler for all addon callbacks and work. */
public interface SchedulerService {
    /** Queues immediate work in a named context with a finite callback budget. */
    ApiResult<TaskHandle> execute(
            ExecutionContext context,
            Runnable callback,
            Duration timeout
    );

    /** Queues delayed work without blocking a Minecraft or Steam thread. */
    ApiResult<TaskHandle> schedule(
            ExecutionContext context,
            Runnable callback,
            Duration delay,
            Duration timeout
    );

    /** Returns whether the caller already runs in the named context. */
    boolean isCurrentContext(ExecutionContext context);
}
