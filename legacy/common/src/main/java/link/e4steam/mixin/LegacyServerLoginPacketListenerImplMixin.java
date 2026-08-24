package link.e4steam.mixin;

import com.mojang.authlib.GameProfile;
import link.e4steam.steam.SteamMinecraftIdentity;
import link.e4steam.steam.SteamMinecraftAuthentication;
import net.minecraft.network.Connection;
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

/** Login hooks for the Minecraft 1.17-1.18.2 login state machine. */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class LegacyServerLoginPacketListenerImplMixin {
    private static final int E4STEAM_LOGIN_TIMEOUT_TICKS = 2_400;

    @Shadow @Final private Connection connection;
    @Shadow public GameProfile gameProfile;
    private long e4steam$authenticatedSteamId;

    private long e4steam$authenticatedSteamId() {
        long current = e4steam$authenticatedSteamId;
        if (current != 0L) return current;
        current = SteamMinecraftAuthentication.authenticatedPeer(connection.getRemoteAddress());
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
    private boolean e4steam$useSteamAuthentication(MinecraftServer server) {
        long authenticatedSteamId = e4steam$authenticatedSteamId();
        return authenticatedSteamId == 0 && server.usesAuthentication();
    }

    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void e4steam$rejectDirectDedicatedLogin(CallbackInfo ci) {
        if (e4steam$authenticatedSteamId() != 0L
                || !SteamMinecraftAuthentication.rejectUntrustedDedicatedIngress(
                        connection.getRemoteAddress())) return;
        connection.disconnect(new net.minecraft.network.chat.TextComponent(
                "This server requires an authenticated e4steam connection"));
        ci.cancel();
    }

    /** Binds the UUID to Steam while preserving the Minecraft nickname. */
    @Inject(method = "handleAcceptedLogin", at = @At("HEAD"))
    private void e4steam$bindLegacyProfileToSteamIdentity(CallbackInfo ci) {
        long authenticatedSteamId = e4steam$authenticatedSteamId();
        if (authenticatedSteamId == 0) {
            return;
        }
        String minecraftName = gameProfile.getName();
        gameProfile = new GameProfile(
                SteamMinecraftIdentity.uuid(authenticatedSteamId),
                SteamMinecraftIdentity.preserveMinecraftName(minecraftName)
        );
    }

    /** Forge negotiation may need longer than the vanilla 600-tick budget. */
    @ModifyConstant(method = "tick", constant = @Constant(intValue = 600), require = 0)
    private int e4steam$extendAuthenticatedSteamLoginTimeout(int vanillaTimeout) {
        long authenticatedSteamId = e4steam$authenticatedSteamId();
        return authenticatedSteamId == 0 ? vanillaTimeout : E4STEAM_LOGIN_TIMEOUT_TICKS;
    }
}
