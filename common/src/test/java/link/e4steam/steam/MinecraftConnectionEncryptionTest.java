package link.e4steam.steam;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftConnectionEncryptionTest {
    @Test void plainAndPartiallyConfiguredChannelsAreNotVerified() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            Object connection = new Connection(channel);
            assertFalse(MinecraftConnectionEncryption.isEncrypted(connection));
            channel.pipeline().addLast("decrypt", new ChannelInboundHandlerAdapter());
            assertFalse(MinecraftConnectionEncryption.isEncrypted(connection));
            channel.pipeline().addLast("encrypt", new ChannelOutboundHandlerAdapter());
            assertTrue(MinecraftConnectionEncryption.isEncrypted(connection));
            channel.pipeline().remove("decrypt");
            assertFalse(MinecraftConnectionEncryption.isEncrypted(connection));
        } finally { channel.finishAndReleaseAll(); }
    }
    @Test void unconnectedChannelIsNotVerified() {
        assertFalse(MinecraftConnectionEncryption.isEncrypted(new Connection(null)));
    }
    @Test void channelFieldCanBeInheritedAndRemapped() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            channel.pipeline().addLast("decrypt", new ChannelInboundHandlerAdapter());
            channel.pipeline().addLast("encrypt", new ChannelOutboundHandlerAdapter());
            assertTrue(MinecraftConnectionEncryption.isEncrypted(new RemappedConnection(channel)));
        } finally { channel.finishAndReleaseAll(); }
    }
    @Test void unsupportedOrAmbiguousConnectionsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> MinecraftConnectionEncryption.isEncrypted(null));
        assertThrows(IllegalStateException.class, () -> MinecraftConnectionEncryption.isEncrypted(new Object()));
        assertThrows(IllegalStateException.class, () -> MinecraftConnectionEncryption.isEncrypted(new AmbiguousConnection()));
    }
    static class Connection {
        private final Channel field_11651;
        Connection(Channel channel) { field_11651 = channel; }
    }
    static class RemappedConnection extends Connection {
        RemappedConnection(Channel channel) { super(channel); }
    }
    static class AmbiguousConnection {
        private Channel first;
        private Channel second;
    }
}
