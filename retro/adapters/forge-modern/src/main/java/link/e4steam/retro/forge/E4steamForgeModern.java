package link.e4steam.retro.forge;

import link.e4steam.retro.RetroClientLoader;
import link.e4steam.retro.RetroVersion;
import net.minecraftforge.fml.common.Mod;

@Mod("e4steam")
public final class E4steamForgeModern {
    public E4steamForgeModern() {
        if (RetroClientLoader.isModernForgeClient()) {
            String adapter = RetroVersion.minecraft().startsWith("1.14")
                    ? "link.e4steam.retro.forge.E4steamForge114Client"
                    : "link.e4steam.retro.forge.E4steamForgeModernClient";
            RetroClientLoader.install(adapter);
        }
    }
}
