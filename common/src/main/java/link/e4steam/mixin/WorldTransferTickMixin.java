package link.e4steam.mixin;
import link.e4steam.worldtransfer.WorldTransferClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class WorldTransferTickMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void e4steam$transferTick(CallbackInfo ci) {
        WorldTransferClient.tick((Minecraft)(Object)this);
    }
}
