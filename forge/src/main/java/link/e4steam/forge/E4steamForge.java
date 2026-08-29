package link.e4steam.forge;

import link.e4steam.E4steamDedicated;
import link.e4steam.MinecraftVersion;
import link.e4steam.api.runtime.RuntimeMode;
import link.e4steam.internal.addon.AddonDiscoverySupport;
import link.e4steam.internal.api.RuntimeEnvironment;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Forge entrypoint whose dedicated path has no Minecraft client dependency. */
@Mod("e4steam")
public final class E4steamForge {
    public E4steamForge() {
        ForgeServerLifecycleCompat.register(MinecraftForge.EVENT_BUS);
        MinecraftForge.EVENT_BUS.register(this);
        String loaderVersion = versionOf(net.minecraftforge.fml.loading.FMLLoader.class);
        if (!AgnosImpl.isClient()) {
            E4steamDedicated.init(
                    new RuntimeEnvironment("forge", loaderVersion, MinecraftVersion.current(),
                            RuntimeMode.DEDICATED_SERVER, true),
                    AddonDiscoverySupport.serviceLoader(getClass().getClassLoader())
            );
            return;
        }
        initializeClient(loaderVersion);
    }

    @SubscribeEvent public void registerCommands(RegisterCommandsEvent event) {
        E4steamDedicated.registerCommands(event.getDispatcher());
    }

    private static String versionOf(Class<?> type) {
        String version = type.getPackage() == null
                ? null : type.getPackage().getImplementationVersion();
        return version == null || version.trim().isEmpty() ? "unknown" : version;
    }

    private static void initializeClient(String loaderVersion) {
        try {
            Class<?> bootstrap = Class.forName(
                    "link.e4steam.forge.E4steamClientForge", true,
                    E4steamForge.class.getClassLoader());
            java.lang.reflect.Method initialize =
                    bootstrap.getDeclaredMethod("initialize", String.class);
            initialize.invoke(null, loaderVersion);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not initialize the e4steam Forge client", failure);
        }
    }
}
