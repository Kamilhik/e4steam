package link.e4steam.internal.addon;

import link.e4steam.api.ApiVersion;
import link.e4steam.api.ApiVersionRange;
import link.e4steam.api.addon.AddonContext;
import link.e4steam.api.addon.AddonDescriptor;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.addon.E4steamAddonEntrypoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AddonDiscoverySupportTest {
    @TempDir Path temporaryDirectory;

    @Test
    void discoversForgeStyleServiceProvider() throws Exception {
        Path service = temporaryDirectory.resolve(
                "META-INF/services/" + E4steamAddonEntrypoint.class.getName());
        Files.createDirectories(service.getParent());
        Files.write(service, Collections.singletonList(TestEntrypoint.class.getName()),
                StandardCharsets.UTF_8);

        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[]{temporaryDirectory.toUri().toURL()},
                getClass().getClassLoader())) {
            List<AddonCandidate> candidates = AddonDiscoverySupport.serviceLoader(loader);
            assertEquals(1, candidates.size());
            assertEquals("test:service-loader",
                    candidates.get(0).descriptor().id().value());
            assertEquals("service-provider", candidates.get(0).sourceModId());
        }
    }

    public static final class TestEntrypoint implements E4steamAddonEntrypoint {
        public TestEntrypoint() { }

        @Override public AddonDescriptor descriptor() {
            return new AddonDescriptor(
                    new AddonId("test:service-loader"),
                    "Service loader test",
                    ApiVersion.parse("1.0.0"),
                    new ApiVersionRange(ApiVersion.parse("1.0.0"),
                            ApiVersion.parse("2.0.0")),
                    Collections.emptyList(), Collections.emptySet());
        }

        @Override public void initialize(AddonContext context) { }
    }
}
