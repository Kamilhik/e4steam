package link.e4steam.retro.forge;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import link.e4steam.retro.RetroClientLoader;

@Mod(modid = "e4steam", name = "e4steam", version = "0.3.0",
        acceptableRemoteVersions = "*")
public final class E4steamForgeLegacy {
    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            RetroClientLoader.install("link.e4steam.retro.forge.E4steamForgeLegacyClient");
        }
    }
}
