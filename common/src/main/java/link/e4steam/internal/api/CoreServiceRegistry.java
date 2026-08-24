package link.e4steam.internal.api;

import link.e4steam.api.ServiceKey;
import link.e4steam.api.ServiceRegistry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class CoreServiceRegistry implements ServiceRegistry {
    private final Map<ServiceKey<?>, Object> services = new LinkedHashMap<>();
    <T> CoreServiceRegistry add(ServiceKey<T> key, T service) {
        if (key == null || service == null || !key.serviceType().isInstance(service)) {
            throw new IllegalArgumentException("Invalid API service registration");
        }
        services.put(key, service);
        return this;
    }
    @Override public <T> Optional<T> find(ServiceKey<T> key) {
        Object service = services.get(key);
        return service == null ? Optional.empty() : Optional.of(key.serviceType().cast(service));
    }
    @Override public Set<String> visibleServiceIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (ServiceKey<?> key : services.keySet()) ids.add(key.id());
        return Collections.unmodifiableSet(ids);
    }
}
