package link.e4steam.mixin;

import link.e4steam.lan.LanSettingsScreen;
import link.e4steam.lan.SteamAddressPreviewScreen;
import link.e4steam.lan.SupportScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screen's vanilla widget renderer has both PoseStack and GuiGraphics signatures. */
@Mixin(Screen.class)
public abstract class LanSettingsRenderMixin {
    // Screen.render starts drawing its own blurred background in newer games.
    // SupportScreen already owns its animated background; blurring afterwards
    // processes that panel as if it were part of the world behind the GUI.
    // Full descriptors keep this optional hook away from older overloads.
    @Inject(method={
            "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            "method_25420(Lnet/minecraft/class_332;IIF)V",
            "m_280273_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
    }, at=@At("HEAD"), cancellable=true, require=0, remap=false)
    private void e4steam$supportOwnBackground(@Coerce Object graphics, int x, int y, float delta, CallbackInfo ci) {
        if ((Object)this instanceof SupportScreen) ci.cancel();
    }

    @Inject(method="/^(render|method_25394|m_88315_|m_6305_)$/", at=@At("HEAD"), require=0, remap=false)
    private void e4steam$panel(@Coerce Object graphics, int x, int y, float delta, CallbackInfo ci) {
        if ((Object)this instanceof SupportScreen) ((SupportScreen)(Object)this).background(graphics);
        if ((Object)this instanceof LanSettingsScreen) ((LanSettingsScreen)(Object)this).renderLanPanel(graphics);
    }
    @Inject(method="/^(render|method_25394|m_88315_|m_6305_)$/", at=@At("TAIL"), require=0, remap=false)
    private void e4steam$indicators(@Coerce Object graphics, int x, int y, float delta, CallbackInfo ci) {
        if ((Object)this instanceof LanSettingsScreen) ((LanSettingsScreen)(Object)this).renderLanIndicators(graphics);
        if ((Object)this instanceof SupportScreen) ((SupportScreen)(Object)this).labels(graphics);
        if ((Object)this instanceof SteamAddressPreviewScreen) ((SteamAddressPreviewScreen)(Object)this).renderLabels(graphics);
    }

    // Minecraft 26.3 moved screen drawing to GuiGraphicsExtractor.
    @Inject(method="extractRenderState", at=@At("HEAD"), require=0, remap=false)
    private void e4steam$panelExtracted(@Coerce Object graphics, int x, int y, float delta, CallbackInfo ci) {
        if ((Object)this instanceof SupportScreen) ((SupportScreen)(Object)this).background(graphics);
        if ((Object)this instanceof LanSettingsScreen) ((LanSettingsScreen)(Object)this).renderLanPanel(graphics);
    }

    @Inject(method="extractRenderState", at=@At("TAIL"), require=0, remap=false)
    private void e4steam$indicatorsExtracted(@Coerce Object graphics, int x, int y, float delta, CallbackInfo ci) {
        if ((Object)this instanceof LanSettingsScreen) ((LanSettingsScreen)(Object)this).renderLanIndicators(graphics);
        if ((Object)this instanceof SupportScreen) ((SupportScreen)(Object)this).labels(graphics);
        if ((Object)this instanceof SteamAddressPreviewScreen) ((SteamAddressPreviewScreen)(Object)this).renderLabels(graphics);
    }
}
