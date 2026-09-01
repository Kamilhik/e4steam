package link.e4steam.retro.forge;

import link.e4steam.retro.RetroClientLoader;
import link.e4steam.retro.RetroBuildMetadata;
import link.e4steam.retro.RetroDedicatedBootstrap;
import link.e4steam.retro.RetroVersion;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid = "e4steam", name = "e4steam", version = "0.3.1",
        acceptedMinecraftVersions = RetroBuildMetadata.ACCEPTED_FORGE_RANGE,
        acceptableRemoteVersions = "*")
public final class E4steamForge {
    @Mod.EventHandler
    public void preInitialize(FMLPreInitializationEvent event) {
        if (event.getSide() == Side.SERVER) {
            RetroDedicatedBootstrap.install(RetroVersion.minecraft());
        }
    }

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            RetroClientLoader.install("link.e4steam.retro.forge.E4steamForgeClient");
        }
    }
}
