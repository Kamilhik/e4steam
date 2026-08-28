package link.e4steam.retro.mixin;

import com.mojang.authlib.GameProfile;
import link.e4steam.steam.SteamMinecraftIdentity;
import link.e4steam.steam.RetroSteamAuthentication;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Uses the Steam-authenticated localhost identity on Minecraft 1.14-1.16.
 * Unauthenticated LAN connections retain vanilla Mojang authentication.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerRetroMixin {
    private static final int E4STEAM_LOGIN_TIMEOUT_TICKS = 2_400;

    @Shadow @Final public Connection connection;
    @Shadow private GameProfile gameProfile;

    private long e4steam$authenticatedSteamId;

    private long e4steam$authenticatedSteamId() {
        long current = e4steam$authenticatedSteamId;
        if (current != 0L) return current;
        current = RetroSteamAuthentication.authenticatedPeer(
                connection.getRemoteAddress());
        if (current != 0L) e4steam$authenticatedSteamId = current;
        return current;
    }

    @Redirect(
            method = "handleHello",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;usesAuthentication()Z"
            )
    )
    private boolean e4steam$useMojangAuthentication(MinecraftServer server) {
        return e4steam$authenticatedSteamId() == 0L
                && server.usesAuthentication();
    }

    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void e4steam$rejectDirectDedicatedLogin(CallbackInfo info) {
        if (e4steam$authenticatedSteamId() != 0L
                || !RetroSteamAuthentication.rejectUntrustedDedicatedIngress(
                        connection.getRemoteAddress())) return;
        connection.disconnect(new TextComponent(
                "This server requires an authenticated e4steam connection"));
        info.cancel();
    }

    @Inject(method = "handleAcceptedLogin", at = @At("HEAD"))
    private void e4steam$bindSteamIdentity(CallbackInfo info) {
        long authenticatedSteamId = e4steam$authenticatedSteamId();
        if (authenticatedSteamId == 0L || gameProfile == null) return;
        gameProfile = new GameProfile(
                SteamMinecraftIdentity.uuid(authenticatedSteamId),
                SteamMinecraftIdentity.preserveMinecraftName(
                        gameProfile.getName())
        );
    }

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 600))
    private int e4steam$extendSteamLoginTimeout(int vanillaTimeout) {
        return e4steam$authenticatedSteamId() == 0L
                ? vanillaTimeout
                : E4STEAM_LOGIN_TIMEOUT_TICKS;
    }
}
