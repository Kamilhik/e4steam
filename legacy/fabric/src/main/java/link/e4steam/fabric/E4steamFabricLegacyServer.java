package link.e4steam.fabric;

import link.e4steam.E4steamDedicated;
import link.e4steam.api.runtime.RuntimeMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Java 16 / Minecraft 1.17-1.18 server entrypoint. */
public final class E4steamFabricLegacyServer implements ModInitializer {
    @Override public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) return;
        E4steamDedicated.init(
                FabricAddonDiscovery.environment(RuntimeMode.DEDICATED_SERVER),
                FabricAddonDiscovery.discover());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> E4steamDedicated.minecraftReady());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> E4steamDedicated.minecraftStopped());
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, dedicated) -> E4steamDedicated.registerCommands(dispatcher));
    }
}
