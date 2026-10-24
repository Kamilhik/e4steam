package link.e4steam.mixin;

import link.e4steam.lan.LanSettings;
import link.e4steam.steam.SteamMinecraftAuthentication;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Integrated-only capacity and occupancy; dedicated servers retain their own settings. */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerLanMixin {
    // This override first exists on 26.x. Earlier versions read PlayerList.maxPlayers.
    @Inject(method = "/^getMaxPlayers$/", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void e4steam$modernCapacity(CallbackInfoReturnable<Integer> cir) {
        LanSettings.Snapshot settings = LanSettings.active(this);
        if (settings != null) cir.setReturnValue(settings.maxPlayers());
    }

    @Inject(method = "/^unpublishServer$/", at = @At("RETURN"), require = 0, remap = false)
    private void e4steam$stopUnpublishedWorld(CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        LanSettings.closed();
        link.e4steam.steam.SteamSession session = link.e4steam.E4steamClient.session;
        if (session != null) {
            session.stop();
            if (link.e4steam.E4steamClient.session == session) link.e4steam.E4steamClient.session = null;
        }
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void e4steam$countLanPlayers(CallbackInfo ci) {
        if (LanSettings.active(this) == null) return;
        MinecraftServer server = (MinecraftServer) (Object) this;
        int ordinary = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection == null || SteamMinecraftAuthentication.authenticatedPeer(
                    link.e4steam.lan.LanPlayerConnection.remoteAddress(player.connection)) == 0) ordinary++;
        }
        LanSettings.nonSteamPlayers(this, ordinary);
    }
}
