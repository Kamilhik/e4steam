package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Registration;
import link.e4steam.api.ResourceScope;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.runtime.RuntimeMode;
import link.e4steam.api.ui.UiService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class CoreUiService implements UiService {
    private final AddonId owner; private final CoreCapabilityService capabilities;
    private final CoreContributionRegistry registry; private final ResourceScope resources;
    private final Availability availability;
    CoreUiService(AddonId owner, CoreCapabilityService capabilities,
                  CoreContributionRegistry registry, ResourceScope resources, RuntimeMode mode) {
        this.owner = owner; this.capabilities = capabilities; this.registry = registry; this.resources = resources;
        this.availability = mode == RuntimeMode.DEDICATED_SERVER ? Availability.HEADLESS : Availability.AVAILABLE;
    }
    @Override public Availability availability() { return availability; }
    @Override public ApiResult<Registration> registerAction(ActionDescriptor descriptor, ActionHandler handler) {
        if (!allowed()) return denied("ui.action.register");
        if (availability != Availability.AVAILABLE) return unavailable("ui.action.register");
        if (descriptor == null || handler == null) return invalid("ui.action.register");
        return registry.register("ui-action", descriptor.id().value(), owner,
                new Object[] {descriptor, handler}, resources, false);
    }
    @Override public ApiResult<Registration> registerSettings(SettingsCategory category) {
        if (!allowed()) return denied("ui.settings.register");
        if (availability != Availability.AVAILABLE) return unavailable("ui.settings.register");
        if (category == null) return invalid("ui.settings.register");
        return registry.register("ui-settings", category.id().value(), owner, category, resources, false);
    }
    @Override public ApiResult<Registration> registerStatus(StatusBadge badge) {
        if (!allowed()) return denied("ui.status.register");
        if (availability != Availability.AVAILABLE) return unavailable("ui.status.register");
        if (badge == null) return invalid("ui.status.register");
        return registry.register("ui-status", badge.id().value(), owner, badge, resources, false);
    }
    @Override public CompletionStage<ApiResult<Boolean>> toast(Message message) {
        return completed(availability == Availability.AVAILABLE && allowed()
                ? SafeApiErrors.failure(ApiErrorCode.UNAVAILABLE, "ui.toast", "AdapterNotRendered")
                : unavailable("ui.toast"));
    }
    @Override public CompletionStage<ApiResult<Boolean>> confirm(Confirmation confirmation) {
        return completed(availability == Availability.AVAILABLE && allowed()
                ? SafeApiErrors.failure(ApiErrorCode.UNAVAILABLE, "ui.confirm", "AdapterNotRendered")
                : unavailable("ui.confirm"));
    }
    @Override public CompletionStage<ApiResult<FormResult>> form(FormDescriptor form) {
        return completed(availability == Availability.AVAILABLE && allowed()
                ? SafeApiErrors.failure(ApiErrorCode.UNAVAILABLE, "ui.form", "AdapterNotRendered")
                : unavailable("ui.form"));
    }
    private boolean allowed() { return capabilities.has(Capabilities.UI_CONTRIBUTE); }
    private <T> ApiResult<T> denied(String operation) { return SafeApiErrors.failure(
            ApiErrorCode.CAPABILITY_DENIED, operation, "PolicyDenied"); }
    private <T> ApiResult<T> unavailable(String operation) { return SafeApiErrors.failure(
            ApiErrorCode.UNAVAILABLE, operation, availability.name()); }
    private <T> ApiResult<T> invalid(String operation) { return SafeApiErrors.failure(
            ApiErrorCode.INVALID_ARGUMENT, operation, "Validation"); }
    private static <T> CompletionStage<T> completed(T value) { return CompletableFuture.completedFuture(value); }
}
