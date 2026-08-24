package link.e4steam.forge;

import link.e4steam.E4steamClient;
import link.e4steam.MinecraftVersion;
import link.e4steam.api.runtime.RuntimeMode;
import link.e4steam.internal.api.RuntimeEnvironment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Loaded only after the neutral Forge entrypoint confirms a client distribution. */
public final class E4steamClientForge {
    private E4steamClientForge(String loaderVersion) {
        E4steamClient.init(
                new RuntimeEnvironment("forge", loaderVersion, MinecraftVersion.current(),
                        RuntimeMode.CLIENT, !System.getProperty("os.name", "")
                        .toLowerCase(java.util.Locale.ROOT).contains("windows")),
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
