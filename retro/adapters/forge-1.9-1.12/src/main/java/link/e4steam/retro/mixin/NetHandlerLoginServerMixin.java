package link.e4steam.retro.mixin;

import com.mojang.authlib.GameProfile;
import link.e4steam.steam.SteamMinecraftIdentity;
import link.e4steam.steam.RetroSteamAuthentication;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetHandlerLoginServer;
import net.minecraft.util.text.TextComponentString;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Steam-authenticated login bridge for Forge 1.9.x through 1.12.x. */
@Mixin(NetHandlerLoginServer.class)
public abstract class NetHandlerLoginServerMixin {
    @Shadow @Final public NetworkManager networkManager;
    @Shadow private GameProfile loginGameProfile;

    private long e4steam$authenticatedSteamId;

    private long e4steam$authenticatedSteamId() {
        long current = e4steam$authenticatedSteamId;
        if (current != 0L) return current;
        current = RetroSteamAuthentication.authenticatedPeer(
                networkManager.getRemoteAddress());
        if (current != 0L) e4steam$authenticatedSteamId = current;
        return current;
    }

    @Redirect(
            method = "processLoginStart",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;isServerInOnlineMode()Z"
            )
    )
    private boolean e4steam$useMojangAuthentication(MinecraftServer server) {
        return e4steam$authenticatedSteamId() == 0L
                && server.isServerInOnlineMode();
    }

    @Inject(method = "processLoginStart", at = @At("HEAD"), cancellable = true)
    private void e4steam$rejectDirectDedicatedLogin(CallbackInfo info) {
        if (e4steam$authenticatedSteamId() != 0L
                || !RetroSteamAuthentication.rejectUntrustedDedicatedIngress(
                        networkManager.getRemoteAddress())) return;
        networkManager.closeChannel(new TextComponentString(
                "This server requires an authenticated e4steam connection"));
        info.cancel();
    }

    @Inject(method = "tryAcceptPlayer", at = @At("HEAD"))
    private void e4steam$bindSteamIdentity(CallbackInfo info) {
        long authenticatedSteamId = e4steam$authenticatedSteamId();
        if (authenticatedSteamId == 0L || loginGameProfile == null) return;
        loginGameProfile = new GameProfile(
                SteamMinecraftIdentity.uuid(authenticatedSteamId),
                SteamMinecraftIdentity.preserveMinecraftName(
                        loginGameProfile.getName())
        );
    }
}
