package link.e4steam.mixin;

import com.mojang.authlib.GameProfile;
import link.e4steam.steam.SteamMinecraftIdentity;
import link.e4steam.steam.SteamRuntime;
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

    @Redirect(
            method = "handleHello",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;usesAuthentication()Z"
            )
    )
    private boolean e4steam$useSteamAuthentication(MinecraftServer server) {
        long authenticatedSteamId = SteamRuntime.get()
                .authenticatedMinecraftPeer(connection.getRemoteAddress());
        return authenticatedSteamId == 0 && server.usesAuthentication();
    }

    /**
     * Legacy versions create their offline profile at the beginning of
     * handleAcceptedLogin(). Bind the complete profile first so vanilla does
     * not replace it with a UUID derived from the client-supplied name.
     */
    @Inject(method = "handleAcceptedLogin", at = @At("HEAD"))
    private void e4steam$bindLegacyProfileToSteamIdentity(CallbackInfo ci) {
        long authenticatedSteamId = SteamRuntime.get()
                .authenticatedMinecraftPeer(connection.getRemoteAddress());
        if (authenticatedSteamId == 0) {
            return;
        }
        gameProfile = new GameProfile(
                SteamMinecraftIdentity.uuid(authenticatedSteamId),
                SteamMinecraftIdentity.safeName(authenticatedSteamId)
        );
    }

    /** Forge negotiation may need longer than the vanilla 600-tick budget. */
    @ModifyConstant(method = "tick", constant = @Constant(intValue = 600), require = 0)
    private int e4steam$extendAuthenticatedSteamLoginTimeout(int vanillaTimeout) {
        long authenticatedSteamId = SteamRuntime.get()
                .authenticatedMinecraftPeer(connection.getRemoteAddress());
        return authenticatedSteamId == 0 ? vanillaTimeout : E4STEAM_LOGIN_TIMEOUT_TICKS;
    }
}
