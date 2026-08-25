package link.e4steam.neoforge;

import link.e4steam.E4steamClient;
import link.e4steam.LoaderSupport;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Loaded only after the neutral NeoForge entrypoint confirms a client distribution. */
public final class E4steamClientNeoForge {
    private E4steamClientNeoForge(String loaderVersion) {
        E4steamClient.init(
                LoaderSupport.clientEnvironment("neoforge", loaderVersion),
                link.e4steam.internal.addon.AddonDiscoverySupport.serviceLoader(
                        getClass().getClassLoader())
        );
        NeoForge.EVENT_BUS.register(this);
    }

    public static void initialize(String loaderVersion) {
        new E4steamClientNeoForge(loaderVersion);
    }

    @SubscribeEvent
    public void onRegisterCommandEvent(RegisterCommandsEvent event) {
        E4steamClient.registerCommands(event.getDispatcher());
    }

}
