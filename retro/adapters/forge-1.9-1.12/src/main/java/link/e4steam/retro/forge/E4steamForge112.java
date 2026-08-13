package link.e4steam.retro.forge;

import link.e4steam.retro.RetroClientLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid = "e4steam", name = "e4steam", version = "0.3.0",
        acceptableRemoteVersions = "*")
public final class E4steamForge112 {
    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            RetroClientLoader.install("link.e4steam.retro.forge.E4steamForge112Client");
        }
    }
}
