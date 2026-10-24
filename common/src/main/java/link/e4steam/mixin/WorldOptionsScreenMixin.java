package link.e4steam.mixin;

import link.e4steam.MinecraftUiCompat;
import link.e4steam.Mirror;
import link.e4steam.lan.LanSettingsScreen;
import link.e4steam.lan.WorldOptionsScreenSupport;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the 26.3 LAN-related world-options page with e4steam's LAN tab. */
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.screens.WorldOptionsScreen", remap = false)
public abstract class WorldOptionsScreenMixin extends Screen {
    @Shadow @Final private Screen lastScreen;
    private boolean e4steam$showWorldTab;

    protected WorldOptionsScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true, remap = false)
    private void e4steam$openLanTab(CallbackInfo ci) {
        if (minecraft.getSingleplayerServer() == null) return;
        if (WorldOptionsScreenSupport.consumeNativeWorldTab(this)) {
            e4steam$showWorldTab = true;
            return;
        }

        ci.cancel();
        MinecraftUiCompat.setScreen(minecraft, new LanSettingsScreen(lastScreen, this));
    }

    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void e4steam$addLanTab(CallbackInfo ci) {
        if (!e4steam$showWorldTab) return;
        e4steam$showWorldTab = false;
        int availableWidth = Math.max(1, width - 24);
        int tabWidth = Math.min(168, Math.max(70, (availableWidth - 8) / 2));
        int left = (width - tabWidth * 2 - 8) / 2;
        addRenderableWidget(MinecraftUiCompat.button(
                Mirror.translatable("text.e4steam_minecraft.lan.tabLan"),
                button -> MinecraftUiCompat.setScreen(minecraft,
                        new LanSettingsScreen(lastScreen, this)),
                left, 6, tabWidth, 20));
        Button worldTab = addRenderableWidget(MinecraftUiCompat.button(
                Mirror.translatable("text.e4steam_minecraft.lan.tabWorld"), button -> { },
                left + tabWidth + 8, 6, tabWidth, 20));
        worldTab.active = false;
    }
}
