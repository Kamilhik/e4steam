package link.e4steam.retro.forge.core;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import java.util.Map;

@SuppressWarnings("unused")
@IFMLLoadingPlugin.TransformerExclusions({"link.e4steam.retro.forge.core"})
@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE + 2)
public final class E4steamForgeCore implements IFMLLoadingPlugin {
    static {
        MixinBootstrap.init();
        Mixins.addConfiguration("e4steam.retro.mixins.json");
    }

    @Override public String[] getLibraryRequestClass() { return new String[0]; }
    @Override public String[] getASMTransformerClass() { return new String[0]; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) { }
}
