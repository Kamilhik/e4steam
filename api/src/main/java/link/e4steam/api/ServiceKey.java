package link.e4steam.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** Typed namespaced key used to add services without growing the root interface. */
public final class ServiceKey<T> {
    private static final Pattern IDENTIFIER = Pattern.compile(
            "^[a-z][a-z0-9_.-]{0,31}:[a-z][a-z0-9_./-]{0,63}$"
    );

    private final String id;
    private final Class<T> serviceType;

    /** Creates a typed service key. */
    public ServiceKey(String id, Class<T> serviceType) {
        this.id = ApiValidation.identifier(id, "service id", IDENTIFIER);
        this.serviceType = Objects.requireNonNull(serviceType, "serviceType");
    }

    /** Returns the namespaced service id. */
    public String id() { return id; }

    /** Returns the loader-independent service interface type. */
    public Class<T> serviceType() { return serviceType; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServiceKey)) return false;
        ServiceKey<?> key = (ServiceKey<?>) other;
        return id.equals(key.id) && serviceType.equals(key.serviceType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, serviceType);
    }

    @Override
    public String toString() { return id; }
}
