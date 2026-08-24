package link.e4steam.retro.forge;

import link.e4steam.retro.RetroClientLoader;
import net.minecraftforge.fml.common.Mod;

@Mod("e4steam")
public final class E4steamForge113 {
    public E4steamForge113() {
        if (RetroClientLoader.isModernForgeClient()) {
            RetroClientLoader.install("link.e4steam.retro.forge.E4steamForge113Client");
        }
    }
}
