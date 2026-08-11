package link.e4steam.example;

import link.e4steam.api.ApiResult;
import link.e4steam.api.Subscription;
import link.e4steam.api.addon.AddonContext;
import link.e4steam.api.addon.E4steamAddon;
import link.e4steam.api.event.RuntimeReadyEvent;
import link.e4steam.api.runtime.RuntimeSnapshot;
import link.e4steam.api.scheduler.ExecutionContext;
import link.e4steam.api.scheduler.TaskHandle;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Neutral compile-checked addon proving the loader-independent foundation. */
public final class ExampleAddon implements E4steamAddon {
    private final AtomicReference<RuntimeSnapshot> lastRuntime = new AtomicReference<>();
    private final AtomicInteger workerCallbacks = new AtomicInteger();

    @Override
    public void initialize(AddonContext context) {
        if (context == null) throw new NullPointerException("context");
        lastRuntime.set(context.api().runtime().snapshot());

        ApiResult<Subscription> subscription = context.api().events().subscribe(
                RuntimeReadyEvent.TYPE,
                event -> lastRuntime.set(event.snapshot())
        );
        if (!subscription.isSuccess()) {
            throw new IllegalStateException("Example event subscription was rejected");
        }
        context.resources().own(subscription.value().get());

        ApiResult<TaskHandle> task = context.api().scheduler().execute(
                ExecutionContext.ADDON_WORKER,
                workerCallbacks::incrementAndGet,
                Duration.ofSeconds(1)
        );
        if (!task.isSuccess()) {
            throw new IllegalStateException("Example worker task was rejected");
        }
        context.resources().own(task.value().get());
    }

    /** Returns the last safe runtime snapshot observed by the example. */
    public RuntimeSnapshot lastRuntime() { return lastRuntime.get(); }

    /** Returns the number of deterministic worker callbacks completed. */
    public int workerCallbacks() { return workerCallbacks.get(); }
}
