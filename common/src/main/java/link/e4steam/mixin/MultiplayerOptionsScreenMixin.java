package link.e4steam.mixin;

import link.e4steam.MinecraftUiCompat;
import link.e4steam.lan.LanSettingsScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes the vanilla LAN entry to e4steam's single combined LAN/Steam form. */
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.screens.MultiplayerOptionsScreen", remap = false)
public abstract class MultiplayerOptionsScreenMixin {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true, remap = false)
    private void e4steam$openLanSettings(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() == null) return;
        ci.cancel();
        MinecraftUiCompat.setScreen(minecraft, new LanSettingsScreen(null));
    }
}
