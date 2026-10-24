package link.e4steam.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Completes the Steam join only after Minecraft has entered the game protocol. */
@Mixin(ClientPacketListener.class)
public abstract class ClientJoinLifecycleMixin {
    @Inject(method = "handleLogin", at = @At("TAIL"), require = 0)
    private void e4steam$gameLoginCompleted(CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            link.e4steam.steam.SteamRuntime.get().clientMinecraftJoined(
                    link.e4steam.lan.LanPlayerConnection.clientRemoteAddress(this));
        }
    }

}
