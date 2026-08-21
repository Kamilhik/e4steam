package link.e4steam.api.testkit;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Retryability;
import link.e4steam.api.scheduler.ExecutionContext;
import link.e4steam.api.scheduler.SchedulerService;
import link.e4steam.api.scheduler.TaskHandle;
import link.e4steam.api.scheduler.TaskState;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Virtual-time bounded scheduler for repeatable addon lifecycle tests. */
public final class DeterministicScheduler implements SchedulerService {
    private final List<TestTask> tasks = new ArrayList<>();
    private long nowMillis;
    private long nextSequence;
    private ExecutionContext currentContext;

    @Override
    public ApiResult<TaskHandle> execute(
            ExecutionContext context,
            Runnable callback,
            Duration timeout
    ) {
        return schedule(context, callback, Duration.ZERO, timeout);
    }

    @Override
    public synchronized ApiResult<TaskHandle> schedule(
            ExecutionContext context,
            Runnable callback,
            Duration delay,
            Duration timeout
    ) {
        if (context == null || callback == null || delay == null || timeout == null) {
            return invalid("scheduler.schedule");
        }
        if (delay.isNegative() || timeout.isZero() || timeout.isNegative()
                || timeout.toMillis() > ApiLimits.MAX_LIFECYCLE_CALLBACK_MILLIS) {
            return invalid("scheduler.schedule");
        }
        if (tasks.size() >= ApiLimits.MAX_QUEUED_TASKS) {
            return ApiResult.failure(new ApiError(
                    ApiErrorCode.QUEUE_FULL,
                    "e4steam.api.error.scheduler_full",
                    Retryability.AFTER_STATE_CHANGE,
                    "scheduler.schedule",
                    "",
                    "bounded_queue"
            ));
        }
        TestTask task = new TestTask(
                context,
                callback,
                nowMillis + delay.toMillis(),
                timeout.toMillis(),
                nextSequence++
        );
        tasks.add(task);
        return ApiResult.<TaskHandle>success(task);
    }

    @Override
    public synchronized boolean isCurrentContext(ExecutionContext context) {
        return context != null && context == currentContext;
    }

    /** Advances virtual time and runs all newly due tasks in deterministic order. */
    public void advance(Duration duration) {
        if (duration == null || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be non-negative");
        }
        synchronized (this) {
            nowMillis += duration.toMillis();
        }
        runUntilIdle();
    }

    /** Runs every task due at the current virtual time. */
    public void runUntilIdle() {
        while (true) {
            TestTask next;
            synchronized (this) {
                next = tasks.stream()
                        .filter(task -> !task.isClosed() && task.dueMillis <= nowMillis)
                        .min(Comparator.comparingLong((TestTask task) -> task.dueMillis)
                                .thenComparingLong(task -> task.sequence))
                        .orElse(null);
                if (next == null) return;
                tasks.remove(next);
                currentContext = next.context;
            }
            try {
                next.runNow();
            } finally {
                synchronized (this) {
                    currentContext = null;
                }
            }
        }
    }

    /** Returns queued, not-yet-closed task count. */
    public synchronized int queuedTaskCount() {
        int count = 0;
        for (TestTask task : tasks) if (!task.isClosed()) count++;
        return count;
    }

    private static <T> ApiResult<T> invalid(String operation) {
        return ApiResult.failure(new ApiError(
                ApiErrorCode.INVALID_ARGUMENT,
                "e4steam.api.error.invalid_argument",
                Retryability.PERMANENT,
                operation,
                "",
                "validation"
        ));
    }

    private static final class TestTask implements TaskHandle {
        private final ExecutionContext context;
        private final Runnable callback;
        private final long dueMillis;
        private final long timeoutMillis;
        private final long sequence;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final CompletableFuture<ApiResult<TaskState>> completion = new CompletableFuture<>();
        private volatile TaskState state = TaskState.QUEUED;

        private TestTask(
                ExecutionContext context,
                Runnable callback,
                long dueMillis,
                long timeoutMillis,
                long sequence
        ) {
            this.context = context;
            this.callback = callback;
            this.dueMillis = dueMillis;
            this.timeoutMillis = timeoutMillis;
            this.sequence = sequence;
        }

        private void runNow() {
            if (closed.get()) return;
            state = TaskState.RUNNING;
            long started = System.nanoTime();
            try {
                callback.run();
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
                state = elapsedMillis > timeoutMillis ? TaskState.TIMED_OUT : TaskState.COMPLETED;
                if (state == TaskState.COMPLETED) {
                    completion.complete(ApiResult.success(state));
                } else {
                    completion.complete(ApiResult.failure(new ApiError(
                            ApiErrorCode.TIMEOUT,
                            "e4steam.api.error.task_timeout",
                            Retryability.PERMANENT,
                            "scheduler.callback",
                            "",
                            "time_budget"
                    )));
                }
            } catch (Throwable throwable) {
                state = TaskState.FAILED;
                completion.complete(ApiResult.failure(new ApiError(
                        ApiErrorCode.ADDON_FAILURE,
                        "e4steam.api.error.addon_callback",
                        Retryability.PERMANENT,
                        "scheduler.callback",
                        "",
                        throwable.getClass().getSimpleName()
                )));
            } finally {
                closed.set(true);
            }
        }

        @Override
        public TaskState state() { return state; }

        @Override
        public CompletionStage<ApiResult<TaskState>> completion() { return completion; }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                state = TaskState.CANCELLED;
                completion.complete(ApiResult.failure(new ApiError(
                        ApiErrorCode.CANCELLED,
                        "e4steam.api.error.task_cancelled",
                        Retryability.PERMANENT,
                        "scheduler.callback",
                        "",
                        "cancelled"
                )));
            }
        }

        @Override
        public boolean isClosed() { return closed.get(); }
    }
}
