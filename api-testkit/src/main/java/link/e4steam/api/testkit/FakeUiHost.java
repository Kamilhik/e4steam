package link.e4steam.api.testkit;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Registration;
import link.e4steam.api.Retryability;
import link.e4steam.api.ui.UiService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Deterministic declarative UI host that also models headless unavailability. */
public final class FakeUiHost implements UiService {
    private final Availability availability;
    private final Map<UiId, ActionDescriptor> actions = new LinkedHashMap<>();
    private final Map<UiId, SettingsCategory> settings = new LinkedHashMap<>();
    private final Map<UiId, StatusBadge> statuses = new LinkedHashMap<>();
    private boolean nextConfirmation = true;
    private FormResult nextFormResult = new FormResult(java.util.Collections.<String, String>emptyMap());

    /** Creates an available or headless fake host. */
    public FakeUiHost(Availability availability) {
        if (availability == null) throw new NullPointerException("availability");
        this.availability = availability;
    }

    @Override public Availability availability() { return availability; }

    @Override
    public synchronized ApiResult<Registration> registerAction(ActionDescriptor descriptor, ActionHandler handler) {
        if (descriptor == null || handler == null) throw new NullPointerException("action");
        if (availability != Availability.AVAILABLE) return unavailable("ui.action");
        if (actions.size() >= ApiLimits.MAX_REGISTRATIONS_PER_FAMILY || actions.containsKey(descriptor.id())) return unavailable("ui.action_duplicate_or_full");
        actions.put(descriptor.id(), descriptor);
        return ApiResult.<Registration>success(new TestRegistration(() -> { synchronized (FakeUiHost.this) { actions.remove(descriptor.id()); } }));
    }

    @Override
    public synchronized ApiResult<Registration> registerSettings(SettingsCategory category) {
        if (category == null) throw new NullPointerException("category");
        if (availability != Availability.AVAILABLE) return unavailable("ui.settings");
        if (settings.containsKey(category.id())) return unavailable("ui.settings_duplicate");
        settings.put(category.id(), category);
        return ApiResult.<Registration>success(new TestRegistration(() -> { synchronized (FakeUiHost.this) { settings.remove(category.id()); } }));
    }

    @Override
    public synchronized ApiResult<Registration> registerStatus(StatusBadge badge) {
        if (badge == null) throw new NullPointerException("badge");
        if (availability != Availability.AVAILABLE) return unavailable("ui.status");
        if (statuses.containsKey(badge.id())) return unavailable("ui.status_duplicate");
        statuses.put(badge.id(), badge);
        return ApiResult.<Registration>success(new TestRegistration(() -> { synchronized (FakeUiHost.this) { statuses.remove(badge.id()); } }));
    }

    @Override public CompletionStage<ApiResult<Boolean>> toast(Message message) { return availableBoolean("ui.toast"); }
    @Override public CompletionStage<ApiResult<Boolean>> confirm(Confirmation confirmation) { return availability == Availability.AVAILABLE ? CompletableFuture.completedFuture(ApiResult.success(nextConfirmation)) : CompletableFuture.completedFuture(unavailable("ui.confirm")); }
    @Override public CompletionStage<ApiResult<FormResult>> form(FormDescriptor form) { return availability == Availability.AVAILABLE ? CompletableFuture.completedFuture(ApiResult.success(nextFormResult)) : CompletableFuture.completedFuture(unavailable("ui.form")); }

    /** Sets deterministic next confirmation result. */ public void nextConfirmation(boolean value) { nextConfirmation = value; }
    /** Sets deterministic next form result. */ public void nextFormResult(FormResult value) { if (value == null) throw new NullPointerException("value"); nextFormResult = value; }
    /** Returns registered action count. */ public synchronized int actionCount() { return actions.size(); }

    private CompletionStage<ApiResult<Boolean>> availableBoolean(String operation) { return CompletableFuture.completedFuture(availability == Availability.AVAILABLE ? ApiResult.success(Boolean.TRUE) : unavailable(operation)); }
    private static <T> ApiResult<T> unavailable(String operation) { return ApiResult.failure(new ApiError(ApiErrorCode.UNAVAILABLE, "e4steam:ui.unavailable", Retryability.PERMANENT, operation, "", "testkit")); }
}
