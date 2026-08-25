package link.e4steam.forge;

import link.e4steam.E4steamClient;
import link.e4steam.LoaderSupport;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Loaded only after the neutral Forge entrypoint confirms a client distribution. */
public final class E4steamClientForge {
    private E4steamClientForge(String loaderVersion) {
        E4steamClient.init(
                LoaderSupport.clientEnvironment("forge", loaderVersion),
                link.e4steam.internal.addon.AddonDiscoverySupport.serviceLoader(
                        getClass().getClassLoader())
        );
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static void initialize(String loaderVersion) {
        new E4steamClientForge(loaderVersion);
    }

    @SubscribeEvent
    public void onRegisterCommandEvent(RegisterCommandsEvent event) {
        E4steamClient.registerCommands(event.getDispatcher());
    }

}
