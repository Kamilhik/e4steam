package link.e4steam.api;

import java.util.Optional;
import java.util.Set;

/** Read-only registry of API services available in the current runtime mode. */
public interface ServiceRegistry {
    /** Returns a typed service when available and granted to this addon scope. */
    <T> Optional<T> find(ServiceKey<T> key);

    /** Returns immutable ids of services visible to this addon scope. */
    Set<String> visibleServiceIds();
}
