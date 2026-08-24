package link.e4steam.retro.mixin;

import link.e4steam.steam.SteamRuntime;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Keeps an authenticated Steam relay alive while old Minecraft versions send
 * the initial registry and chunk burst. Ordinary LAN connections retain the
 * vanilla 15-second keep-alive interval.
 */
@Mixin(targets = "net.minecraft.server.network.ServerGamePacketListenerImpl")
public abstract class ServerGamePacketListenerRetroMixin {
    private static final long E4STEAM_KEEP_ALIVE_INTERVAL_MILLIS = 60_000L;

    @Shadow public Connection connection;

    @ModifyConstant(
            method = "tick",
            constant = @Constant(longValue = 15_000L),
            require = 1
    )
    private long e4steam$extendAuthenticatedSteamKeepAlive(long vanillaInterval) {
        return SteamRuntime.get().authenticatedMinecraftPeer(
                connection.getRemoteAddress()) == 0L
                ? vanillaInterval
                : E4STEAM_KEEP_ALIVE_INTERVAL_MILLIS;
    }
}
