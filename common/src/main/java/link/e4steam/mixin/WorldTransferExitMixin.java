package link.e4steam.mixin;

import link.e4steam.worldtransfer.WorldTransferClient;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Button.class)
public abstract class WorldTransferExitMixin {
    @Inject(method="/^(onPress|method_25306|m_5691_)$/",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private void e4steam$exitChoice(CallbackInfo ci) {
        if (WorldTransferClient.interceptExit((Button)(Object)this)) ci.cancel();
    }
}
