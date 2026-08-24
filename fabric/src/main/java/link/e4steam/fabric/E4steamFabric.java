package link.e4steam.fabric;

import link.e4steam.E4steamDedicated;
import link.e4steam.api.runtime.RuntimeMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/** Common-side Fabric entrypoint. It never references Minecraft client classes. */
public final class E4steamFabric implements ModInitializer {
    @Override public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) return;
        E4steamDedicated.init(
                FabricAddonDiscovery.environment(RuntimeMode.DEDICATED_SERVER),
                FabricAddonDiscovery.discover()
        );
        ServerLifecycleEvents.SERVER_STARTED.register(server -> E4steamDedicated.minecraftReady());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> E4steamDedicated.minecraftStopped());
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        E4steamDedicated.registerCommands(dispatcher));
    }
}
