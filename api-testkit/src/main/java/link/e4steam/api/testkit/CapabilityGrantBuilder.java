package link.e4steam.api.testkit;

import link.e4steam.api.capability.CapabilityId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Fluent deterministic capability-set builder for addon tests. */
public final class CapabilityGrantBuilder {
    private final LinkedHashSet<CapabilityId> capabilities = new LinkedHashSet<>();

    /** Adds one capability. */
    public CapabilityGrantBuilder grant(CapabilityId capability) {
        if (capability == null) throw new NullPointerException("capability");
        capabilities.add(capability);
        return this;
    }

    /** Returns an immutable insertion-ordered set. */
    public Set<CapabilityId> build() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(capabilities));
    }
}
