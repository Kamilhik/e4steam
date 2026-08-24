package link.e4steam.mixin;

import link.e4steam.steam.SteamMinecraftAuthentication;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Keeps only authenticated Steam guests alive during the initial chunk burst. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LegacyServerGamePacketListenerImplMixin {
    private static final long E4STEAM_KEEP_ALIVE_INTERVAL_MILLIS = 60_000L;

    @Shadow @Final public Connection connection;

    @ModifyConstant(
            method = "tick",
            constant = @Constant(longValue = 15_000L),
            require = 1
    )
    private long e4steam$extendAuthenticatedSteamKeepAlive(long vanillaInterval) {
        return SteamMinecraftAuthentication.authenticatedPeer(
                connection.getRemoteAddress()) == 0L
                ? vanillaInterval
                : E4STEAM_KEEP_ALIVE_INTERVAL_MILLIS;
    }
}
