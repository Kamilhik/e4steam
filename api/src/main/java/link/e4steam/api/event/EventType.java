package link.e4steam.api.event;

import link.e4steam.api.ServiceKey;

import java.util.Objects;

/** Typed namespaced event key. */
public final class EventType<T extends ApiEvent> {
    private final ServiceKey<T> key;

    /** Creates a typed event key. */
    public EventType(String id, Class<T> eventClass) {
        this.key = new ServiceKey<>(id, eventClass);
    }

    /** Returns the namespaced event id. */
    public String id() { return key.id(); }

    /** Returns the immutable event DTO type. */
    public Class<T> eventClass() { return key.serviceType(); }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof EventType
                && key.equals(((EventType<?>) other).key);
    }

    @Override
    public int hashCode() { return Objects.hash(key); }

    @Override
    public String toString() { return key.toString(); }
}
