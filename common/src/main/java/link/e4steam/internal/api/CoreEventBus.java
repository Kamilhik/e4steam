package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ResourceScope;
import link.e4steam.api.Subscription;
import link.e4steam.api.event.ApiEvent;
import link.e4steam.api.event.EventListener;
import link.e4steam.api.event.EventService;
import link.e4steam.api.event.EventType;
import link.e4steam.api.scheduler.ExecutionContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Typed bounded event bus with deterministic registration order and isolated callbacks. */
final class CoreEventBus {
    private final Object lock = new Object();
    private final CoreSchedulerService scheduler;
    private final Map<EventType<?>, List<Listener<?>>> listeners = new HashMap<>();
    private final Map<EventType<?>, ApiEvent> snapshots = new HashMap<>();
    private int subscriptions;

    CoreEventBus(CoreSchedulerService scheduler) { this.scheduler = scheduler; }

    EventService scoped(ResourceScope resources) {
        return new EventService() {
            @Override public <T extends ApiEvent> ApiResult<Subscription> subscribe(
                    EventType<T> type, EventListener<? super T> callback) {
                if (type == null || callback == null) return SafeApiErrors.failure(
                        ApiErrorCode.INVALID_ARGUMENT, "event.subscribe", "Validation");
                Listener<T> registration;
                synchronized (lock) {
                    if (subscriptions >= ApiLimits.MAX_EVENT_SUBSCRIPTIONS) return SafeApiErrors.failure(
                            ApiErrorCode.QUEUE_FULL, "event.subscribe", "BoundedSubscriptions");
                    registration = new Listener<>(type, callback);
                    listeners.computeIfAbsent(type, ignored -> new ArrayList<>()).add(registration);
                    subscriptions++;
                }
                resources.own(registration);
                return ApiResult.success(registration);
            }
            @Override public <T extends ApiEvent> Optional<T> lastSnapshot(EventType<T> type) {
                synchronized (lock) {
                    ApiEvent value = snapshots.get(type);
                    return value == null ? Optional.empty() : Optional.of(type.eventClass().cast(value));
                }
            }
        };
    }

    <T extends ApiEvent> void publish(EventType<T> type, T event, boolean replayable) {
        if (type == null || event == null || !type.eventClass().isInstance(event)) {
            throw new IllegalArgumentException("Event type mismatch");
        }
        List<Listener<?>> copy;
        synchronized (lock) {
            if (replayable) snapshots.put(type, event);
            copy = new ArrayList<>(listeners.getOrDefault(type, java.util.Collections.emptyList()));
        }
        for (Listener<?> listener : copy) {
            scheduler.execute(ExecutionContext.ADDON_WORKER, () -> listener.dispatch(event), Duration.ofSeconds(2));
        }
    }

    private final class Listener<T extends ApiEvent> extends CoreRegistration implements Subscription {
        private final EventType<T> type;
        private final EventListener<? super T> callback;
        private Listener(EventType<T> type, EventListener<? super T> callback) {
            super(null); this.type = type; this.callback = callback;
        }
        private void dispatch(ApiEvent event) {
            if (isClosed()) return;
            try { callback.onEvent(type.eventClass().cast(event)); }
            catch (Exception ignored) { }
        }
        @Override public void close() {
            if (isClosed()) return;
            super.close();
            synchronized (lock) {
                List<Listener<?>> current = listeners.get(type);
                if (current != null && current.remove(this)) subscriptions--;
            }
        }
    }
}
