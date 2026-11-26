package link.e4steam.steam;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/** Serializes local login protocol/read-mode transitions on Netty's event loop. */
public final class MinecraftConnectionReadMode {
    private MinecraftConnectionReadMode() { }

    public static ChannelConfig update(Channel channel, ChannelConfig config, boolean autoRead) {
        SocketAddress remote = channel.remoteAddress();
        boolean loopback = remote instanceof InetSocketAddress
                && ((InetSocketAddress) remote).getAddress() != null
                && ((InetSocketAddress) remote).getAddress().isLoopbackAddress();
        if (loopback && !channel.eventLoop().inEventLoop()) {
            // Older vanilla disables reads on the connecting thread, then queues
            // its protocol change. Netty's off-thread clearReadPending can race
            // that change: autoRead becomes true while OP_READ remains cleared.
            // Queue both operations on the same loop instead of extending timeouts.
            channel.eventLoop().execute(() -> config.setAutoRead(autoRead));
            return config;
        }
        return config.setAutoRead(autoRead);
    }
}
