package link.e4steam.neoforge;

import link.e4steam.E4steamDedicated;
import link.e4steam.LoaderSupport;
import link.e4steam.MinecraftVersion;
import link.e4steam.api.runtime.RuntimeMode;
import link.e4steam.internal.addon.AddonDiscoverySupport;
import link.e4steam.internal.api.RuntimeEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** NeoForge entrypoint whose dedicated path has no Minecraft client dependency. */
@Mod("e4steam")
public final class E4steamNeoForge {
    public E4steamNeoForge() {
        NeoForge.EVENT_BUS.register(this);
        String loaderVersion = LoaderSupport.versionOf(net.neoforged.fml.loading.FMLLoader.class);
        if (!AgnosImpl.isClient()) {
            E4steamDedicated.init(
                    new RuntimeEnvironment("neoforge", loaderVersion, MinecraftVersion.current(),
                            RuntimeMode.DEDICATED_SERVER, true),
                    AddonDiscoverySupport.serviceLoader(getClass().getClassLoader())
            );
            return;
        }
        LoaderSupport.initializeClient("link.e4steam.neoforge.E4steamClientNeoForge", loaderVersion);
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
