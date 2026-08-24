package link.e4steam.retro.mixin;

import link.e4steam.retro.RetroBootstrap;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps e4steam client commands out of the integrated server command parser. */
@Mixin(Screen.class)
public abstract class ScreenCommand114Mixin {
    @Inject(
            method = "sendMessage(Ljava/lang/String;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void e4steam$handleClientCommand(
            String message,
            boolean addToRecentChat,
            CallbackInfo info
    ) {
        if (RetroBootstrap.handleClientCommand(message)) {
            info.cancel();
        }
    }
}
