package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Registration;
import link.e4steam.api.ResourceScope;
import link.e4steam.api.access.AccessService;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.scheduler.ExecutionContext;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import link.e4steam.api.scheduler.TaskHandle;

final class CoreAccessService implements AccessService {
    private static final String FAMILY = "access";
    private final AddonId owner;
    private final CoreCapabilityService capabilities;
    private final CoreContributionRegistry registry;
    private final ResourceScope resources;
    private final CoreSchedulerService scheduler;
    CoreAccessService(AddonId owner, CoreCapabilityService capabilities,
                      CoreContributionRegistry registry, ResourceScope resources,
                      CoreSchedulerService scheduler) {
        this.owner = owner; this.capabilities = capabilities; this.registry = registry;
        this.resources = resources; this.scheduler = scheduler;
    }
    @Override public ApiResult<Registration> register(AccessModeProvider provider) {
        if (!capabilities.has(Capabilities.ACCESS_MODE_REGISTER)) return denied("access.register");
        if (provider == null || provider.id() == null || provider.policy() == null) return SafeApiErrors.failure(
                ApiErrorCode.INVALID_ARGUMENT, "access.register", "Validation");
        return registry.register(FAMILY, provider.id().value(), owner, provider, resources, true);
    }
    @Override public CompletionStage<ApiResult<AdmissionDecision>> evaluate(
            AccessModeId mode, AdmissionContext context) {
        if (!capabilities.has(Capabilities.ACCESS_POLICY_EVALUATE)) return completed(denied("access.evaluate"));
        if (mode == null || context == null || !context.coreAuthenticated()
                || !owner.equals(context.modeOwner())) return completed(SafeApiErrors.failure(
                ApiErrorCode.SECURITY_REJECTION, "access.evaluate", "MandatoryGate"));
        Object value = registry.find(FAMILY, mode.value());
        if (!(value instanceof AccessModeProvider)) return completed(SafeApiErrors.failure(
                ApiErrorCode.UNAVAILABLE, "access.evaluate", "ModeUnavailable"));
        CompletableFuture<ApiResult<AdmissionDecision>> result = new CompletableFuture<>();
        ApiResult<link.e4steam.api.scheduler.TaskHandle> queued = scheduler.execute(
                ExecutionContext.ADDON_WORKER, () -> {
                    try {
                        CompletionStage<AdmissionDecision> stage = ((AccessModeProvider) value).policy().evaluate(context);
                        if (stage == null) {
                            result.complete(ApiResult.success(AdmissionDecision.deny("addon-policy-failed")));
                        } else stage.whenComplete((decision, failure) -> {
                            if (failure != null || decision == null) result.complete(ApiResult.success(
                                    AdmissionDecision.deny("addon-policy-failed")));
                            else result.complete(ApiResult.success(decision));
                        });
                    } catch (VirtualMachineError | ThreadDeath fatal) {
                        throw fatal;
                    } catch (Throwable failure) {
                        result.complete(ApiResult.success(AdmissionDecision.deny("addon-policy-failed")));
                    }
                }, Duration.ofSeconds(2));
        if (!queued.isSuccess()) return completed(SafeApiErrors.failure(ApiErrorCode.QUEUE_FULL,
                "access.evaluate", "Scheduler"));
        AtomicReference<TaskHandle> timeout = new AtomicReference<>();
        ApiResult<TaskHandle> timeoutResult = scheduler.schedule(ExecutionContext.E4STEAM_CONTROL,
                () -> result.complete(ApiResult.success(AdmissionDecision.deny("addon-policy-timeout"))),
                Duration.ofSeconds(2), Duration.ofSeconds(1));
        if (!timeoutResult.isSuccess()) return completed(SafeApiErrors.failure(ApiErrorCode.QUEUE_FULL,
                "access.evaluate", "Scheduler"));
        timeout.set(timeoutResult.value().get());
        result.whenComplete((valueResult, failure) -> {
            TaskHandle task = timeout.get();
            if (task != null) task.close();
        });
        return result;
    }
    @Override public boolean registrationsFrozen() { return registry.isFrozen(); }
    private <T> ApiResult<T> denied(String operation) { return SafeApiErrors.failure(
            ApiErrorCode.CAPABILITY_DENIED, operation, "PolicyDenied"); }
    private static <T> CompletionStage<T> completed(T value) { return CompletableFuture.completedFuture(value); }
}
