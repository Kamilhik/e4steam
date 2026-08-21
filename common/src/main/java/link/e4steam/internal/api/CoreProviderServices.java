package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Registration;
import link.e4steam.api.ResourceScope;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.diagnostics.DiagnosticsService;
import link.e4steam.api.modpack.ModpackService;
import link.e4steam.api.skin.SkinService;
import link.e4steam.api.scheduler.ExecutionContext;
import link.e4steam.api.scheduler.TaskHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

final class CoreProviderServices {
    private CoreProviderServices() { }

    static ModpackService modpacks(AddonId owner, CoreCapabilityService capabilities,
                                   CoreContributionRegistry registry, ResourceScope resources,
                                   CoreSchedulerService scheduler) {
        return new ModpackService() {
            private volatile ModpackProvider local;
            @Override public ApiResult<Registration> registerProvider(ModpackProvider provider) {
                if (!capabilities.has(Capabilities.MODPACK_INSPECT)) return denied("modpack.register");
                if (provider == null) return invalid("modpack.register");
                ApiResult<Registration> result = registry.register("modpack", provider.id(), owner,
                        provider, resources, false);
                if (!result.isSuccess()) return result;
                local = provider;
                CoreRegistration scoped = new CoreRegistration(() -> {
                    result.value().get().close();
                    if (local == provider) local = null;
                });
                resources.own(scoped);
                return ApiResult.success(scoped);
            }
            @Override public CompletionStage<ApiResult<CompatibilityReport>> inspect(
                    ModpackManifest manifest, Environment environment) {
                if (!capabilities.has(Capabilities.MODPACK_INSPECT)) return completed(denied("modpack.inspect"));
                ModpackProvider provider = local;
                if (provider == null) return completed(unavailable("modpack.inspect"));
                return invoke(() -> provider.inspect(manifest, environment),
                        "modpack.inspect", scheduler);
            }
            @Override public CompletionStage<ApiResult<InstallPlan>> plan(
                    ModpackManifest manifest, CompatibilityReport report) {
                if (!capabilities.has(Capabilities.MODPACK_STAGE)) return completed(denied("modpack.plan"));
                ModpackProvider provider = local;
                if (provider == null) return completed(unavailable("modpack.plan"));
                return invoke(() -> provider.plan(manifest, report), "modpack.plan", scheduler);
            }
        };
    }

    static SkinService skins(AddonId owner, CoreCapabilityService capabilities,
                             CoreContributionRegistry registry, ResourceScope resources,
                             CoreSchedulerService scheduler) {
        return new SkinService() {
            private volatile SkinProvider local;
            @Override public ApiResult<Registration> registerProvider(SkinProvider provider) {
                if (!capabilities.has(Capabilities.SKINS_PROVIDE)) return denied("skin.register");
                if (provider == null) return invalid("skin.register");
                ApiResult<Registration> result = registry.register("skin", provider.id(), owner,
                        provider, resources, false);
                if (!result.isSuccess()) return result;
                local = provider;
                CoreRegistration scoped = new CoreRegistration(() -> {
                    result.value().get().close();
                    if (local == provider) local = null;
                });
                resources.own(scoped);
                return ApiResult.success(scoped);
            }
            @Override public CompletionStage<ApiResult<SkinResult>> resolve(SkinRequest request) {
                if (!capabilities.has(Capabilities.SKINS_PROVIDE)) return completed(denied("skin.resolve"));
                SkinProvider provider = local;
                if (provider == null) return completed(unavailable("skin.resolve"));
                return invoke(() -> provider.resolve(request), "skin.resolve", scheduler);
            }
        };
    }

    static DiagnosticsService diagnostics(AddonId owner, CoreCapabilityService capabilities,
                                           CoreContributionRegistry registry, ResourceScope resources,
                                           CoreSchedulerService scheduler) {
        return new DiagnosticsService() {
            private final List<DiagnosticsContributor> local = new CopyOnWriteArrayList<>();
            @Override public ApiResult<HealthSnapshot> health() {
                return ApiResult.success(new HealthSnapshot(Collections.singletonList(
                        new ComponentHealth("e4steam:addon-api", Health.HEALTHY, "ready"))));
            }
            @Override public ApiResult<Registration> registerContributor(DiagnosticsContributor contributor) {
                if (!capabilities.has(Capabilities.DIAGNOSTICS_CONTRIBUTE)) return denied("diagnostics.register");
                if (contributor == null) return invalid("diagnostics.register");
                if (local.size() >= 128) return SafeApiErrors.failure(
                        ApiErrorCode.QUEUE_FULL, "diagnostics.register", "ContributorLimit");
                ApiResult<Registration> result = registry.register("diagnostics", contributor.id(), owner,
                        contributor, resources, false);
                if (!result.isSuccess()) return result;
                local.add(contributor);
                CoreRegistration scoped = new CoreRegistration(() -> {
                    result.value().get().close();
                    local.remove(contributor);
                });
                resources.own(scoped);
                return ApiResult.success(scoped);
            }
            @Override public CompletionStage<ApiResult<DoctorPreview>> doctorPreview(PrivacyOptions options) {
                if (options == null) return completed(invalid("diagnostics.preview"));
                ArrayList<CompletableFuture<ApiResult<DiagnosticsSection>>> pending = new ArrayList<>();
                for (DiagnosticsContributor contributor : local) {
                    CompletionStage<ApiResult<DiagnosticsSection>> stage = invoke(
                            contributor::contribute, "diagnostics.contribute", scheduler);
                    pending.add(stage.toCompletableFuture());
                }
                CompletableFuture<ApiResult<DoctorPreview>> preview = new CompletableFuture<>();
                CompletableFuture<?>[] futures = pending.toArray(new CompletableFuture<?>[0]);
                CompletableFuture.allOf(futures).whenComplete((ignored, failure) -> {
                    ArrayList<DiagnosticsSection> sections = new ArrayList<>();
                    int bytes = 0;
                    for (CompletableFuture<ApiResult<DiagnosticsSection>> contribution : pending) {
                        ApiResult<DiagnosticsSection> result = contribution.getNow(null);
                        if (result == null || !result.isSuccess()) continue;
                        DiagnosticsSection section = result.value().get();
                        int sectionBytes = estimatedBytes(section);
                        if (sectionBytes > 2 * 1_048_576 - bytes) break;
                        sections.add(section);
                        bytes += sectionBytes;
                    }
                    preview.complete(ApiResult.success(new DoctorPreview(sections,
                            Collections.singletonList("secrets-and-personal-data-excluded"), bytes)));
                });
                return preview;
            }
        };
    }

    private static <T> CompletionStage<ApiResult<T>> invoke(
            Supplier<CompletionStage<ApiResult<T>>> callback,
            String operation,
            CoreSchedulerService scheduler) {
        CompletableFuture<ApiResult<T>> result = new CompletableFuture<>();
        ApiResult<TaskHandle> invocation = scheduler.execute(ExecutionContext.ADDON_WORKER, () -> {
            try {
                CompletionStage<ApiResult<T>> stage = callback.get();
                if (stage == null) {
                    result.complete(SafeApiErrors.failure(
                            ApiErrorCode.ADDON_FAILURE, operation, "NullStage"));
                    return;
                }
                stage.whenComplete((value, failure) -> result.complete(failure == null && value != null
                        ? value : SafeApiErrors.failure(ApiErrorCode.ADDON_FAILURE, operation,
                        failure == null ? "NullResult" : safeCategory(failure))));
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                result.complete(addonFailure(operation, failure));
            }
        }, Duration.ofSeconds(1));
        if (!invocation.isSuccess()) return completed(SafeApiErrors.failure(
                ApiErrorCode.QUEUE_FULL, operation, "Scheduler"));
        AtomicReference<TaskHandle> timeout = new AtomicReference<>();
        ApiResult<TaskHandle> timeoutResult = scheduler.schedule(ExecutionContext.E4STEAM_CONTROL,
                () -> result.complete(SafeApiErrors.failure(ApiErrorCode.TIMEOUT,
                        operation, "Timeout")), Duration.ofSeconds(2), Duration.ofSeconds(1));
        if (timeoutResult.isSuccess()) timeout.set(timeoutResult.value().get());
        else result.complete(SafeApiErrors.failure(ApiErrorCode.QUEUE_FULL, operation, "Scheduler"));
        result.whenComplete((value, failure) -> {
            TaskHandle task = timeout.get();
            if (task != null) task.close();
        });
        return result;
    }

    private static int estimatedBytes(DiagnosticsService.DiagnosticsSection section) {
        int total = section.id().getBytes(StandardCharsets.UTF_8).length;
        for (java.util.Map.Entry<String, String> field : section.fields().entrySet()) {
            total += field.getKey().getBytes(StandardCharsets.UTF_8).length;
            total += field.getValue().getBytes(StandardCharsets.UTF_8).length;
        }
        return total;
    }
    private static <T> ApiResult<T> denied(String operation) { return SafeApiErrors.failure(
            ApiErrorCode.CAPABILITY_DENIED, operation, "PolicyDenied"); }
    private static <T> ApiResult<T> invalid(String operation) { return SafeApiErrors.failure(
            ApiErrorCode.INVALID_ARGUMENT, operation, "Validation"); }
    private static <T> ApiResult<T> unavailable(String operation) { return SafeApiErrors.failure(
            ApiErrorCode.UNAVAILABLE, operation, "ProviderUnavailable"); }
    private static <T> ApiResult<T> addonFailure(String operation, Throwable failure) { return SafeApiErrors.failure(
            ApiErrorCode.ADDON_FAILURE, operation, safeCategory(failure)); }
    private static String safeCategory(Throwable failure) {
        String value = failure == null ? "AddonFailure" : failure.getClass().getSimpleName();
        return value.matches("[A-Za-z0-9_$.-]{1,64}") ? value : "AddonFailure";
    }
    private static <T> CompletionStage<T> completed(T value) { return CompletableFuture.completedFuture(value); }
}
