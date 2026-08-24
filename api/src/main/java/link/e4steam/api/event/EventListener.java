package link.e4steam.api.event;

/** Isolated callback for one immutable observational event type. */
public interface EventListener<T extends ApiEvent> {
    /** Handles an event on the documented bounded addon callback context. */
    void onEvent(T event) throws Exception;
}
