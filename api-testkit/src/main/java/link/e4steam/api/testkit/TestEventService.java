package link.e4steam.api.testkit;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Retryability;
import link.e4steam.api.Subscription;
import link.e4steam.api.event.ApiEvent;
import link.e4steam.api.event.EventListener;
import link.e4steam.api.event.EventService;
import link.e4steam.api.event.EventType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Bounded in-memory event bus with exception isolation and replay snapshots. */
public final class TestEventService implements EventService {
    private final Object lock = new Object();
    private final Map<EventType<?>, List<ListenerRegistration<?>>> listeners = new HashMap<>();
    private final Map<EventType<?>, ApiEvent> snapshots = new HashMap<>();
    private int subscriptionCount;
    private int callbackFailureCount;

    @Override
    public <T extends ApiEvent> ApiResult<Subscription> subscribe(
            EventType<T> eventType,
            EventListener<? super T> listener
    ) {
        if (eventType == null || listener == null) {
            return ApiResult.failure(error(ApiErrorCode.INVALID_ARGUMENT, "event.subscribe"));
        }
        synchronized (lock) {
            if (subscriptionCount >= ApiLimits.MAX_EVENT_SUBSCRIPTIONS) {
                return ApiResult.failure(error(ApiErrorCode.QUEUE_FULL, "event.subscribe"));
            }
            ListenerRegistration<T> registration = new ListenerRegistration<>(
                    this,
                    eventType,
                    listener
            );
            listeners.computeIfAbsent(eventType, ignored -> new ArrayList<>()).add(registration);
            subscriptionCount++;
            return ApiResult.<Subscription>success(registration);
        }
    }

    @Override
    public <T extends ApiEvent> Optional<T> lastSnapshot(EventType<T> eventType) {
        synchronized (lock) {
            ApiEvent event = snapshots.get(eventType);
            return event == null
                    ? Optional.<T>empty()
                    : Optional.of(eventType.eventClass().cast(event));
        }
    }

    /** Publishes one event outside internal locks and optionally stores it for replay. */
    public <T extends ApiEvent> void publish(EventType<T> eventType, T event, boolean replayable) {
        if (eventType == null || event == null || !eventType.eventClass().isInstance(event)) {
            throw new IllegalArgumentException("Event does not match its type");
        }
        List<ListenerRegistration<?>> callbacks;
        synchronized (lock) {
            if (replayable) snapshots.put(eventType, event);
            List<ListenerRegistration<?>> current = listeners.get(eventType);
            callbacks = current == null ? new ArrayList<ListenerRegistration<?>>() : new ArrayList<>(current);
        }
        for (ListenerRegistration<?> callback : callbacks) {
            try {
                callback.dispatch(event);
            } catch (Exception exception) {
                synchronized (lock) {
                    callbackFailureCount++;
                }
            }
        }
    }

    /** Returns isolated callback failures observed by the fake bus. */
    public int callbackFailureCount() {
        synchronized (lock) {
            return callbackFailureCount;
        }
    }

    private void remove(ListenerRegistration<?> registration) {
        synchronized (lock) {
            List<ListenerRegistration<?>> current = listeners.get(registration.eventType);
            if (current != null && current.remove(registration)) subscriptionCount--;
        }
    }

    private static ApiError error(ApiErrorCode code, String operation) {
        return new ApiError(
                code,
                code == ApiErrorCode.QUEUE_FULL
                        ? "e4steam.api.error.event_limit"
                        : "e4steam.api.error.invalid_argument",
                code == ApiErrorCode.QUEUE_FULL
                        ? Retryability.AFTER_STATE_CHANGE
                        : Retryability.PERMANENT,
                operation,
                "",
                "bounded_event_bus"
        );
    }

    private static final class ListenerRegistration<T extends ApiEvent>
            extends TestRegistration implements Subscription {
        private final TestEventService owner;
        private final EventType<T> eventType;
        private final EventListener<? super T> listener;

        private ListenerRegistration(
                TestEventService owner,
                EventType<T> eventType,
                EventListener<? super T> listener
        ) {
            this.owner = owner;
            this.eventType = eventType;
            this.listener = listener;
        }

        private void dispatch(ApiEvent event) throws Exception {
            if (!isClosed()) listener.onEvent(eventType.eventClass().cast(event));
        }

        @Override
        public void close() {
            boolean wasClosed = isClosed();
            super.close();
            if (!wasClosed) owner.remove(this);
        }
    }
}
