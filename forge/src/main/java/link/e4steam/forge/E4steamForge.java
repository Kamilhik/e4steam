package link.e4steam.forge;

import link.e4steam.E4steamDedicated;
import link.e4steam.LoaderSupport;
import link.e4steam.MinecraftVersion;
import link.e4steam.api.runtime.RuntimeMode;
import link.e4steam.internal.addon.AddonDiscoverySupport;
import link.e4steam.internal.api.RuntimeEnvironment;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Forge entrypoint whose dedicated path has no Minecraft client dependency. */
@Mod("e4steam")
public final class E4steamForge {
    public E4steamForge() {
        MinecraftForge.EVENT_BUS.register(this);
        String loaderVersion = LoaderSupport.versionOf(net.minecraftforge.fml.loading.FMLLoader.class);
        if (!AgnosImpl.isClient()) {
            E4steamDedicated.init(
                    new RuntimeEnvironment("forge", loaderVersion, MinecraftVersion.current(),
                            RuntimeMode.DEDICATED_SERVER, true),
                    AddonDiscoverySupport.serviceLoader(getClass().getClassLoader())
            );
            return;
        }
        LoaderSupport.initializeClient("link.e4steam.forge.E4steamClientForge", loaderVersion);
    }

    @SubscribeEvent public void serverStarted(ServerStartedEvent event) {
        E4steamDedicated.minecraftReady();
    }

    @SubscribeEvent public void serverStopping(ServerStoppingEvent event) {
        E4steamDedicated.minecraftStopped();
    }

    @SubscribeEvent public void registerCommands(RegisterCommandsEvent event) {
        E4steamDedicated.registerCommands(event.getDispatcher());
    }
}
