package link.e4steam.api.testkit;

import link.e4steam.api.ServiceKey;
import link.e4steam.api.ServiceRegistry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory type-checked service registry for API contract tests. */
public final class TestServiceRegistry implements ServiceRegistry {
    private final Map<ServiceKey<?>, Object> services = new LinkedHashMap<>();

    /** Registers one fake service before the test begins. */
    public <T> TestServiceRegistry register(ServiceKey<T> key, T service) {
        if (key == null || service == null) throw new NullPointerException("service");
        if (!key.serviceType().isInstance(service)) {
            throw new IllegalArgumentException("Service does not implement its key type");
        }
        services.put(key, service);
        return this;
    }

    @Override
    public <T> Optional<T> find(ServiceKey<T> key) {
        Object service = services.get(key);
        return service == null ? Optional.<T>empty() : Optional.of(key.serviceType().cast(service));
    }

    @Override
    public Set<String> visibleServiceIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (ServiceKey<?> key : services.keySet()) ids.add(key.id());
        return Collections.unmodifiableSet(ids);
    }
}
