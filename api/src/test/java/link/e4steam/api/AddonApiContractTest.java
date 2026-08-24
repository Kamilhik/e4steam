package link.e4steam.api;

import link.e4steam.api.addon.AddonDependency;
import link.e4steam.api.addon.AddonDescriptor;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.capability.CapabilityId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonApiContractTest {
    @Test
    void validatesNamespacedAddonAndDottedCapabilityIds() {
        assertEquals("demo:example", new AddonId("demo:example").value());
        assertEquals("session.observe", new CapabilityId("session.observe").value());
        assertThrows(IllegalArgumentException.class, () -> new AddonId("example"));
        assertThrows(IllegalArgumentException.class, () -> new CapabilityId("SESSION"));
    }

    @Test
    void descriptorDefensivelyCopiesCollections() {
        List<AddonDependency> dependencies = new ArrayList<>();
        Set<CapabilityId> capabilities = new LinkedHashSet<>();
        capabilities.add(Capabilities.SESSION_OBSERVE);
        AddonDescriptor descriptor = new AddonDescriptor(
                new AddonId("demo:example"),
                "Example",
                ApiVersion.parse("1.0.0"),
                new ApiVersionRange(ApiVersion.parse("0.1.0"), ApiVersion.parse("1.0.0")),
                dependencies,
                capabilities
        );
        dependencies.add(new AddonDependency(
                new AddonId("demo:later"),
                new ApiVersionRange(ApiVersion.parse("1.0.0"), ApiVersion.parse("2.0.0")),
                false
        ));
        capabilities.clear();

        assertTrue(descriptor.dependencies().isEmpty());
        assertEquals(1, descriptor.requestedCapabilities().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> descriptor.requestedCapabilities().clear()
        );
    }

    @Test
    void descriptorRejectsSelfDependencyAndDuplicateDependency() {
        AddonId id = new AddonId("demo:example");
        AddonDependency self = new AddonDependency(
                id,
                new ApiVersionRange(ApiVersion.parse("1.0.0"), ApiVersion.parse("2.0.0")),
                true
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AddonDescriptor(
                        id,
                        "Example",
                        ApiVersion.parse("1.0.0"),
                        new ApiVersionRange(ApiVersion.parse("0.1.0"), ApiVersion.parse("1.0.0")),
                        java.util.Collections.singletonList(self),
                        java.util.Collections.<CapabilityId>emptySet()
                )
        );
    }
}
