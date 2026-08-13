package link.e4steam.internal.api;

import link.e4steam.api.ApiResult;
import link.e4steam.api.Registration;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.diagnostics.DiagnosticsService;
import link.e4steam.api.testkit.TestResourceScope;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreProviderServicesTest {
    @Test
    void diagnosticsRunsOffCallerThreadHonorsTimeoutAndUnregisters() throws Exception {
        CoreSchedulerService scheduler = new CoreSchedulerService();
        TestResourceScope resources = new TestResourceScope();
        try {
            CoreCapabilityService capabilities = new CoreCapabilityService(
                    Collections.singleton(Capabilities.DIAGNOSTICS_CONTRIBUTE),
                    Collections.singleton(Capabilities.DIAGNOSTICS_CONTRIBUTE));
            DiagnosticsService diagnostics = CoreProviderServices.diagnostics(
                    new AddonId("test:addon"), capabilities,
                    new CoreContributionRegistry(), resources, scheduler);
            AtomicReference<String> callbackThread = new AtomicReference<>();
            ApiResult<Registration> registration = diagnostics.registerContributor(
                    new DiagnosticsService.DiagnosticsContributor() {
                        @Override public String id() { return "test-addon:health"; }
                        @Override public java.util.concurrent.CompletionStage<ApiResult<DiagnosticsService.DiagnosticsSection>> contribute() {
                            callbackThread.set(Thread.currentThread().getName());
                            return CompletableFuture.completedFuture(ApiResult.success(
                                    new DiagnosticsService.DiagnosticsSection("test-addon:health",
                                            Collections.singletonMap("state", "ready"))));
                        }
                    });
            assertTrue(registration.isSuccess());

            ApiResult<DiagnosticsService.DoctorPreview> preview = diagnostics.doctorPreview(
                    new DiagnosticsService.PrivacyOptions(false, false))
                    .toCompletableFuture().get(4, TimeUnit.SECONDS);
            assertTrue(preview.isSuccess());
            assertEquals(1, preview.value().get().sections().size());
            assertTrue(callbackThread.get().startsWith("e4steam-addon-worker"));

            registration.value().get().close();
            ApiResult<DiagnosticsService.DoctorPreview> empty = diagnostics.doctorPreview(
                    new DiagnosticsService.PrivacyOptions(false, false))
                    .toCompletableFuture().get(1, TimeUnit.SECONDS);
            assertTrue(empty.isSuccess());
            assertTrue(empty.value().get().sections().isEmpty());

            diagnostics.registerContributor(new DiagnosticsService.DiagnosticsContributor() {
                @Override public String id() { return "test-addon:slow"; }
                @Override public java.util.concurrent.CompletionStage<ApiResult<DiagnosticsService.DiagnosticsSection>> contribute() {
                    return new CompletableFuture<>();
                }
            });
            ApiResult<DiagnosticsService.DoctorPreview> timed = diagnostics.doctorPreview(
                    new DiagnosticsService.PrivacyOptions(false, false))
                    .toCompletableFuture().get(4, TimeUnit.SECONDS);
            assertTrue(timed.isSuccess());
            assertTrue(timed.value().get().sections().isEmpty());
        } finally {
            resources.close();
            scheduler.close();
        }
    }
}
