package link.e4steam.retro.forge;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import link.e4steam.retro.RetroBuildMetadata;
import link.e4steam.retro.RetroClientLoader;
import link.e4steam.retro.RetroDedicatedBootstrap;
import link.e4steam.retro.RetroVersion;

@Mod(modid = "e4steam", name = "e4steam", version = "0.3.0",
        acceptedMinecraftVersions = RetroBuildMetadata.ACCEPTED_FORGE_RANGE,
        acceptableRemoteVersions = "*")
public final class E4steamForgeLegacy {
    @Mod.EventHandler
    public void preInitialize(FMLPreInitializationEvent event) {
        if (event.getSide() == Side.SERVER) {
            RetroDedicatedBootstrap.install(RetroVersion.minecraft());
        }
    }

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            RetroClientLoader.install("link.e4steam.retro.forge.E4steamForgeLegacyClient");
        }
    }
}
