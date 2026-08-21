/* Adapted from xhyrom/e4mc-retro under Apache-2.0; see docs/RETRO_PORTING.md. */
package link.e4steam.retro.mixin;

import link.e4steam.retro.RetroBootstrap;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.integrated.IntegratedServerListenThread;
import net.minecraft.world.EnumGameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServer164Mixin {
    @Shadow private IntegratedServerListenThread theServerListeningThread;

    @Inject(method = "shareToLAN", at = @At("RETURN"))
    private void e4steam$shareToLan(EnumGameType gameType, boolean commands,
                                    CallbackInfoReturnable<String> result) {
        if (result.getReturnValue() != null) {
            try {
                RetroBootstrap.relayBound(Integer.parseInt(theServerListeningThread.func_71755_c()));
            } catch (IOException exception) {
                RetroBootstrap.relayClosed();
            }
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void e4steam$stop(CallbackInfo info) {
        RetroBootstrap.relayClosed();
    }

    @Inject(method = "initiateShutdown", at = @At("HEAD"))
    private void e4steam$shutdown(CallbackInfo info) {
        RetroBootstrap.relayClosed();
    }
}
