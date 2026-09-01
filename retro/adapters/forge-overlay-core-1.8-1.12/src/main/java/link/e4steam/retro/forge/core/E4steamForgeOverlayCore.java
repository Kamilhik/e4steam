package link.e4steam.retro.forge.core;

import link.e4steam.retro.RetroForgeOverlayBootstrap;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Map;

/** Runs the Unix overlay relaunch before Minecraft creates its LWJGL Display. */
@IFMLLoadingPlugin.TransformerExclusions({"link.e4steam.retro.forge.core"})
@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE + 3)
public final class E4steamForgeOverlayCore implements IFMLLoadingPlugin {
    public E4steamForgeOverlayCore() {
        installOnClient();
    }

    private static void installOnClient() {
        if (FMLLaunchHandler.side() == Side.CLIENT) {
            RetroForgeOverlayBootstrap.install();
        }
    }

    @Override public String[] getASMTransformerClass() {
        return new String[] {
                "link.e4steam.retro.forge.core.E4steamForgeSplashTransformer",
                "link.e4steam.retro.forge.core.E4steamForgeGlContextTransformer"
        };
    }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) { installOnClient(); }
    @Override public String getAccessTransformerClass() { return null; }
}
