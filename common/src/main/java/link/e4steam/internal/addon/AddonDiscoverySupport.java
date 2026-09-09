package link.e4steam.internal.addon;

import link.e4steam.api.addon.E4steamAddonEntrypoint;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Safe Java-service discovery used by Forge/NeoForge after their normal mod loading. */
public final class AddonDiscoverySupport {
    private static final Logger LOGGER = LoggerFactory.getLogger("e4steam-addon-api");
    private AddonDiscoverySupport() { }

    public static List<AddonCandidate> serviceLoader(ClassLoader loader) {
        ArrayList<AddonCandidate> candidates = new ArrayList<>();
        ServiceLoader<E4steamAddonEntrypoint> services = ServiceLoader.load(
                E4steamAddonEntrypoint.class, loader == null
                        ? E4steamAddonEntrypoint.class.getClassLoader() : loader);
        java.util.Iterator<E4steamAddonEntrypoint> iterator = services.iterator();
        int attempts = 0;
        while (attempts++ < link.e4steam.api.ApiLimits.MAX_REGISTRATIONS_PER_FAMILY) {
            final boolean hasNext;
            try { hasNext = iterator.hasNext(); }
            catch (ServiceConfigurationError failure) {
                LOGGER.warn("Ignored an unreadable e4steam addon service provider ({})",
                        failure.getClass().getSimpleName());
                continue;
            }
            if (!hasNext) break;
            try {
                E4steamAddonEntrypoint entrypoint = iterator.next();
                candidates.add(AddonCandidate.fromEntrypoint(entrypoint, "service-provider"));
            } catch (ServiceConfigurationError | RuntimeException failure) {
                LOGGER.warn("Ignored a malformed e4steam addon service provider ({})",
                        failure.getClass().getSimpleName());
            }
        }
        return candidates;
    }
}
