package link.e4steam.fabric;

import link.e4steam.E4steamClient;
import link.e4steam.api.runtime.RuntimeMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/** Client-only Fabric bootstrap, omitted from the headless server entry graph. */
public final class E4steamClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        E4steamClient.init(
                FabricAddonDiscovery.environment(RuntimeMode.CLIENT),
                FabricAddonDiscovery.discover()
        );
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> E4steamClient.registerCommands(dispatcher)
        );
    }
}
