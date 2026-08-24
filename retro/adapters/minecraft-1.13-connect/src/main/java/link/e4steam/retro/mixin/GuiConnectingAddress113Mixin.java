package link.e4steam.retro.mixin;

import link.e4steam.E4steamClient;
import link.e4steam.steam.SteamAddress;
import net.minecraft.client.gui.GuiConnecting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes a typed e4steam address into the Steam bridge before Minecraft tries DNS/TCP. */
@Mixin(GuiConnecting.class)
public abstract class GuiConnectingAddress113Mixin {
    @Inject(method = "connect", at = @At("HEAD"), cancellable = true)
    private void e4steam$connectSteamAddress(String host, int port, CallbackInfo ci) {
        if (!SteamAddress.tryParse(host).isPresent()) {
            return;
        }

        ci.cancel();
        E4steamClient.acceptDirectSteamInvite(host, "Steam host");
    }
}
