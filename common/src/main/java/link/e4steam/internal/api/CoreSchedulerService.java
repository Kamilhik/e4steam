package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.scheduler.ExecutionContext;
import link.e4steam.api.scheduler.SchedulerService;
import link.e4steam.api.scheduler.TaskHandle;
import link.e4steam.api.scheduler.TaskState;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded production scheduler that never runs addon work on Steam native callback threads. */
final class CoreSchedulerService implements SchedulerService, AutoCloseable {
    private final ThreadLocal<ExecutionContext> current = new ThreadLocal<>();
    private final AtomicInteger outstanding = new AtomicInteger();
    private final Set<CoreTask> tasks = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers;
    private final ScheduledThreadPoolExecutor timer;
    private final AtomicBoolean closed = new AtomicBoolean();

    CoreSchedulerService() {
        workers = new ThreadPoolExecutor(2, 4, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(ApiLimits.MAX_QUEUED_TASKS), daemonFactory("e4steam-addon-worker"),
                new ThreadPoolExecutor.AbortPolicy());
        timer = new ScheduledThreadPoolExecutor(1, daemonFactory("e4steam-addon-timer"));
        timer.setRemoveOnCancelPolicy(true);
    }

    @Override public ApiResult<TaskHandle> execute(ExecutionContext context, Runnable callback, Duration timeout) {
        return schedule(context, callback, Duration.ZERO, timeout);
    }

    @Override
    public ApiResult<TaskHandle> schedule(ExecutionContext context, Runnable callback,
                                          Duration delay, Duration timeout) {
        if (context == null || callback == null || delay == null || timeout == null
                || delay.isNegative() || timeout.isZero() || timeout.isNegative()
                || timeout.toMillis() > ApiLimits.MAX_OPERATION_TIMEOUT_MILLIS) {
            return SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT, "scheduler.schedule", "Validation");
        }
        if (closed.get()) return SafeApiErrors.failure(ApiErrorCode.UNAVAILABLE, "scheduler.schedule", "Shutdown");
        int count = outstanding.incrementAndGet();
        if (count > ApiLimits.MAX_QUEUED_TASKS) {
            outstanding.decrementAndGet();
            return SafeApiErrors.failure(ApiErrorCode.QUEUE_FULL, "scheduler.schedule", "BoundedQueue");
        }
        CoreTask task = new CoreTask(context, callback, timeout.toMillis());
        tasks.add(task);
        try {
            task.delayFuture = timer.schedule(() -> {
                if (task.taskClosed.get() || closed.get()) return;
                try {
                    task.workerFuture = workers.submit(task::run);
                } catch (RejectedExecutionException failure) {
                    task.reject();
                }
            }, delay.toMillis(), TimeUnit.MILLISECONDS);
            return ApiResult.success(task);
        } catch (RejectedExecutionException failure) {
            task.reject();
            return SafeApiErrors.failure(ApiErrorCode.QUEUE_FULL, "scheduler.schedule", "BoundedQueue");
        }
    }

    @Override public boolean isCurrentContext(ExecutionContext context) { return context != null && context == current.get(); }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (CoreTask task : tasks.toArray(new CoreTask[0])) task.close();
        timer.shutdownNow();
        workers.shutdownNow();
    }

    private final class CoreTask implements TaskHandle {
        private final ExecutionContext context;
        private final Runnable callback;
        private final long timeoutMillis;
        private final AtomicBoolean taskClosed = new AtomicBoolean();
        private final CompletableFuture<ApiResult<TaskState>> completion = new CompletableFuture<>();
        private volatile TaskState state = TaskState.QUEUED;
        private volatile Future<?> delayFuture;
        private volatile Future<?> workerFuture;
        private volatile Future<?> timeoutFuture;
        private CoreTask(ExecutionContext context, Runnable callback, long timeoutMillis) {
            this.context = context; this.callback = callback; this.timeoutMillis = timeoutMillis;
        }
        private void run() {
            if (taskClosed.get()) return;
            state = TaskState.RUNNING;
            current.set(context);
            try {
                timeoutFuture = timer.schedule(this::timeout, timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException failure) {
                reject();
                current.remove();
                return;
            }
            try {
                callback.run();
                terminal(TaskState.COMPLETED, ApiResult.success(TaskState.COMPLETED));
            } catch (VirtualMachineError | ThreadDeath fatal) {
                terminal(TaskState.FAILED, SafeApiErrors.failure(ApiErrorCode.ADDON_FAILURE,
                        "scheduler.callback", fatal.getClass().getSimpleName()));
                throw fatal;
            } catch (Throwable failure) {
                terminal(TaskState.FAILED, SafeApiErrors.failure(ApiErrorCode.ADDON_FAILURE,
                        "scheduler.callback", failure.getClass().getSimpleName()));
            } finally {
                Future<?> watchdog = timeoutFuture;
                if (watchdog != null) watchdog.cancel(false);
                current.remove();
            }
        }
        private void timeout() {
            if (terminal(TaskState.TIMED_OUT, SafeApiErrors.failure(ApiErrorCode.TIMEOUT,
                    "scheduler.callback", "TimeBudget"))) {
                Future<?> worker = workerFuture;
                if (worker != null) worker.cancel(true);
            }
        }
        private void reject() {
            terminal(TaskState.FAILED, SafeApiErrors.failure(ApiErrorCode.QUEUE_FULL,
                    "scheduler.callback", "BoundedQueue"));
        }
        private boolean terminal(TaskState terminalState, ApiResult<TaskState> result) {
            if (!taskClosed.compareAndSet(false, true)) return false;
            state = terminalState;
            tasks.remove(this);
            outstanding.decrementAndGet();
            completion.complete(result);
            return true;
        }
        @Override public TaskState state() { return state; }
        @Override public CompletionStage<ApiResult<TaskState>> completion() {
            return completion.thenApply(result -> result);
        }
        @Override public void close() {
            if (!terminal(TaskState.CANCELLED, SafeApiErrors.failure(ApiErrorCode.CANCELLED,
                    "scheduler.callback", "Cancelled"))) return;
            Future<?> delayed = delayFuture; if (delayed != null) delayed.cancel(false);
            Future<?> worker = workerFuture; if (worker != null) worker.cancel(true);
            Future<?> watchdog = timeoutFuture; if (watchdog != null) watchdog.cancel(false);
        }
        @Override public boolean isClosed() { return taskClosed.get(); }
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + '-' + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
