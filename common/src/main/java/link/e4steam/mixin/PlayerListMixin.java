package link.e4steam.mixin;

import com.mojang.authlib.GameProfile;
import link.e4steam.Config;
import link.e4steam.E4steamClient;
import link.e4steam.Mirror;
import link.e4steam.steam.SteamMinecraftIdentity;
import link.e4steam.steam.SteamRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserWhiteList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.net.SocketAddress;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Shadow public abstract UserBanList getBans();



    @Shadow public abstract MinecraftServer getServer();

    @Inject(method = "/^<init>$/", at = @At("TAIL"))
    void injectListLoads(CallbackInfo ci) {
        if (Config.INSTANCE.restoreDedicatedCommands.value()) {

            try {
                this.getBans().load();
            } catch (IOException e) {
                E4steamClient.LOGGER.warn("Failed to load user banlist: ", e);
            }

        }
    }

    @Inject(method = "/^(canPlayerLogin|method_14586|checkCanJoin|m_6418_)$/", at = @At("HEAD"), cancellable = true)
    public void allowOwnerLogin(SocketAddress socketAddress, @Coerce Object gameProfile, CallbackInfoReturnable<Component> cir) {
        long authenticatedSteamId = SteamRuntime.get().authenticatedMinecraftPeer(socketAddress);
        boolean vanillaOwnerMatch = Mirror.isSingleplayerOwnerObj(getServer(), gameProfile);
        if (SteamMinecraftIdentity.allowSingleplayerOwnerBypass(
                authenticatedSteamId,
                vanillaOwnerMatch
        )) {
            cir.setReturnValue(null);
        }
    }
    @Inject(method = "getMaxPlayers", at = @At("HEAD"), cancellable = true)
    private void e4steam$lanCapacity(CallbackInfoReturnable<Integer> cir) {
        link.e4steam.lan.LanSettings.Snapshot settings =
                link.e4steam.lan.LanSettings.active(getServer());
        if (settings != null) cir.setReturnValue(settings.maxPlayers());
    }

}
