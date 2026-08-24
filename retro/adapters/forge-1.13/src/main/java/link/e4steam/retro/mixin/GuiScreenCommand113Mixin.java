package link.e4steam.retro.mixin;

import link.e4steam.retro.RetroBootstrap;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps e4steam commands on the physical client in Minecraft 1.13.2. */
@Mixin(GuiScreen.class)
public abstract class GuiScreenCommand113Mixin {
    @Inject(
            method = "sendChatMessage(Ljava/lang/String;Z)V",
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
