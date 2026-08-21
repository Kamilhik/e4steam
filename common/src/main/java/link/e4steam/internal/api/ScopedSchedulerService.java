package link.e4steam.internal.api;

import link.e4steam.api.ApiResult;
import link.e4steam.api.ResourceScope;
import link.e4steam.api.scheduler.ExecutionContext;
import link.e4steam.api.scheduler.SchedulerService;
import link.e4steam.api.scheduler.TaskHandle;

import java.time.Duration;

final class ScopedSchedulerService implements SchedulerService {
    private final CoreSchedulerService delegate;
    private final ResourceScope resources;
    ScopedSchedulerService(CoreSchedulerService delegate, ResourceScope resources) {
        this.delegate = delegate; this.resources = resources;
    }
    @Override public ApiResult<TaskHandle> execute(ExecutionContext context, Runnable callback, Duration timeout) {
        return own(delegate.execute(context, callback, timeout));
    }
    @Override public ApiResult<TaskHandle> schedule(ExecutionContext context, Runnable callback,
                                                    Duration delay, Duration timeout) {
        return own(delegate.schedule(context, callback, delay, timeout));
    }
    @Override public boolean isCurrentContext(ExecutionContext context) { return delegate.isCurrentContext(context); }
    private ApiResult<TaskHandle> own(ApiResult<TaskHandle> result) {
        if (result.isSuccess()) resources.own(result.value().get());
        return result;
    }
}
