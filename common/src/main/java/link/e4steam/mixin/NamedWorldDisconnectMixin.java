package link.e4steam.mixin;

import link.e4steam.MinecraftUiCompat;
import link.e4steam.Mirror;
import link.e4steam.steam.SteamNamedConnectionFailure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DisconnectedScreen.class)
public abstract class NamedWorldDisconnectMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private static Component e4steam$namedWorldReason(Component original) {
        String reason = SteamNamedConnectionFailure.take(MinecraftUiCompat.currentScreen(Minecraft.getInstance()));
        return reason == null ? original : Mirror.literal(reason);
    }
}
