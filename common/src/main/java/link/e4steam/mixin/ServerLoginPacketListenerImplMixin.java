package link.e4steam.mixin;

import link.e4steam.steam.SteamRuntime;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Uses the authenticated Steam identity instead of Mojang auth for Steam bridge guests only. */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {
    private static final int E4STEAM_LOGIN_TIMEOUT_TICKS = 2_400;

    @Shadow @Final private Connection connection;

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

    /** Forge can send a large registry snapshot before accepting the player. */
    @ModifyConstant(method = "tick", constant = @Constant(intValue = 600), require = 0)
    private int e4steam$extendAuthenticatedSteamLoginTimeout(int vanillaTimeout) {
        long authenticatedSteamId = SteamRuntime.get()
                .authenticatedMinecraftPeer(connection.getRemoteAddress());
        return authenticatedSteamId == 0 ? vanillaTimeout : E4STEAM_LOGIN_TIMEOUT_TICKS;
    }
}
