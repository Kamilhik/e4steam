package link.e4steam.api.event;

import link.e4steam.api.ApiResult;
import link.e4steam.api.Subscription;

import java.util.Optional;

/** Typed observational event bus with bounded scoped subscriptions. */
public interface EventService {
    /** Subscribes in deterministic registration order. */
    <T extends ApiEvent> ApiResult<Subscription> subscribe(
            EventType<T> eventType,
            EventListener<? super T> listener
    );

    /** Returns an explicitly replayable state snapshot when one exists. */
    <T extends ApiEvent> Optional<T> lastSnapshot(EventType<T> eventType);
}
