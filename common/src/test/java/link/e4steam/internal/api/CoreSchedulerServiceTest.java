package link.e4steam.internal.api;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.scheduler.ExecutionContext;
import link.e4steam.api.scheduler.TaskHandle;
import link.e4steam.api.scheduler.TaskState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreSchedulerServiceTest {
    @Test
    void timeoutCompletesAndInterruptsSlowAddonTask() throws Exception {
        CoreSchedulerService scheduler = new CoreSchedulerService();
        try {
            ApiResult<TaskHandle> queued = scheduler.execute(
                    ExecutionContext.ADDON_WORKER,
                    () -> {
                        try {
                            Thread.sleep(10_000L);
                        } catch (InterruptedException expected) {
                            Thread.currentThread().interrupt();
                        }
                    },
                    Duration.ofMillis(50L)
            );

            ApiResult<TaskState> completion = queued.value().get().completion()
                    .toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertFalse(completion.isSuccess());
            assertEquals(TaskState.TIMED_OUT, queued.value().get().state());
        } finally {
            scheduler.close();
        }
    }

    @Test
    void queueIsBoundedAndShutdownCancelsEveryDelayedTask() {
        CoreSchedulerService scheduler = new CoreSchedulerService();
        List<TaskHandle> accepted = new ArrayList<>();
        for (int index = 0; index < ApiLimits.MAX_QUEUED_TASKS; index++) {
            ApiResult<TaskHandle> result = scheduler.schedule(
                    ExecutionContext.ADDON_WORKER,
                    () -> { },
                    Duration.ofHours(1),
                    Duration.ofSeconds(1)
            );
            assertTrue(result.isSuccess());
            accepted.add(result.value().get());
        }

        assertFalse(scheduler.schedule(
                ExecutionContext.ADDON_WORKER,
                () -> { },
                Duration.ofHours(1),
                Duration.ofSeconds(1)
        ).isSuccess());

        scheduler.close();
        for (TaskHandle task : accepted) {
            assertEquals(TaskState.CANCELLED, task.state());
            assertFalse(task.completion().toCompletableFuture().join().isSuccess());
        }
    }

    @Test
    void callerCannotCompleteTheSchedulersInternalFuture() {
        CoreSchedulerService scheduler = new CoreSchedulerService();
        try {
            TaskHandle task = scheduler.schedule(
                    ExecutionContext.ADDON_WORKER,
                    () -> { },
                    Duration.ofHours(1),
                    Duration.ofSeconds(1)).value().get();

            task.completion().toCompletableFuture().complete(
                    ApiResult.success(TaskState.COMPLETED));

            assertEquals(TaskState.QUEUED, task.state());
            task.close();
            assertEquals(TaskState.CANCELLED, task.state());
        } finally {
            scheduler.close();
        }
    }
}
