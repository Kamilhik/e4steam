package link.e4steam.fabric;

import link.e4steam.MinecraftVersion;
import link.e4steam.api.addon.E4steamAddonEntrypoint;
import link.e4steam.api.runtime.RuntimeMode;
import link.e4steam.internal.addon.AddonCandidate;
import link.e4steam.internal.api.RuntimeEnvironment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.util.ArrayList;
import java.util.List;

final class FabricAddonDiscovery {
    private FabricAddonDiscovery() { }

    static List<AddonCandidate> discover() {
        ArrayList<AddonCandidate> candidates = new ArrayList<>();
        for (EntrypointContainer<E4steamAddonEntrypoint> container : FabricLoader.getInstance()
                .getEntrypointContainers("e4steam", E4steamAddonEntrypoint.class)) {
            try {
                candidates.add(AddonCandidate.fromEntrypoint(container.getEntrypoint(),
                        container.getProvider().getMetadata().getId()));
            } catch (RuntimeException ignored) {
                // A malformed optional entry point cannot prevent the core mod from starting.
            }
        }
        return candidates;
    }

    static RuntimeEnvironment environment(RuntimeMode mode) {
        String loaderVersion = FabricLoader.getInstance().getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        return new RuntimeEnvironment("fabric", loaderVersion, MinecraftVersion.current(),
                mode, !System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .contains("windows"));
    }
}
