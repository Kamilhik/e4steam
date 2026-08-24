package link.e4steam.retro.mixin;

import link.e4steam.retro.RetroBootstrap;
import link.e4steam.steam.SteamAccessMode;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the Steam access selector to the 1.14-1.15 LAN screen. */
@Mixin(ShareToLanScreen.class)
public abstract class ShareToLanScreenStringMixin extends Screen {
    protected ShareToLanScreenStringMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void e4steam$addAccessModeButton(CallbackInfo info) {
        addButton(new Button(
                width / 2 - 155,
                height - 52,
                310,
                20,
                e4steam$accessModeLabel(RetroBootstrap.selectedAccessMode()),
                button -> button.setMessage(e4steam$accessModeLabel(
                        RetroBootstrap.cycleAccessMode()))
        ));
    }

    private static String e4steam$accessModeLabel(SteamAccessMode mode) {
        return I18n.get("text.e4steam_minecraft.accessMode") + ": "
                + I18n.get(mode.translationKey());
    }
}
