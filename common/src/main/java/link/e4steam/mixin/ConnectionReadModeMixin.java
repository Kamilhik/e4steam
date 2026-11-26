package link.e4steam.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import link.e4steam.steam.MinecraftConnectionReadMode;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Older vanilla changes read mode off-loop while connecting to a local Steam bridge. */
@Mixin(Connection.class)
public abstract class ConnectionReadModeMixin {
    @Shadow private Channel channel;

    // The old sendPacket method has different listener arguments across branches.
    // Newer protocol implementations no longer contain this call (require=0).
    @Redirect(method="/^(sendPacket|method_10764|m_129520_)$/", remap=false,
            at=@At(value="INVOKE", target="Lio/netty/channel/ChannelConfig;setAutoRead(Z)Lio/netty/channel/ChannelConfig;", remap=false),
            require=0)
    private ChannelConfig e4steam$serializeLocalReadMode(ChannelConfig config, boolean autoRead) {
        return MinecraftConnectionReadMode.update(channel, config, autoRead);
    }
}
