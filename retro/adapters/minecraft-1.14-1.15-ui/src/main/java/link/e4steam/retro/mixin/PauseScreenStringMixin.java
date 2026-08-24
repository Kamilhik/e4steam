package link.e4steam.retro.mixin;

import link.e4steam.E4steamClient;
import link.e4steam.retro.RetroBootstrap;
import link.e4steam.steam.SteamSession;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the Steam invitation button to the 1.14-1.15 pause screen. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenStringMixin extends Screen {
    protected PauseScreenStringMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void e4steam$addInviteButton(CallbackInfo info) {
        SteamSession session = E4steamClient.session;
        if (session == null
                || session.state() == SteamSession.State.STOPPED
                || session.state() == SteamSession.State.STOPPING
                || session.state() == SteamSession.State.UNHEALTHY) {
            return;
        }
        addButton(new Button(
                Math.max(4, width - 154),
                6,
                150,
                20,
                I18n.get("text.e4steam_minecraft.inviteFriends"),
                button -> RetroBootstrap.handleClientCommand("/e4steam invite")
        ));
    }
}
