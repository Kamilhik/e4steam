package link.e4steam.retro.fabric;

import link.e4steam.retro.RetroDedicatedBootstrap;
import link.e4steam.retro.RetroVersion;
import net.fabricmc.api.DedicatedServerModInitializer;

public final class E4steamFabricDedicated implements DedicatedServerModInitializer {
    @Override public void onInitializeServer() {
        RetroDedicatedBootstrap.install(RetroVersion.minecraft());
    }
}
