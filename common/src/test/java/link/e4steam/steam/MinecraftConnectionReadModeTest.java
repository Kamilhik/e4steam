package link.e4steam.steam;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftConnectionReadModeTest {
    @Test void localProtocolTransitionCannotLeaveAutoReadEnabledWithoutReadInterest() {
        Loop loop = new Loop(true);
        // Deterministically reproduce DefaultChannelConfig's off-loop callback
        // interleaving with the NIO read-pending clear used by older vanilla.
        loop.config.beforeOffLoopClear = () -> loop.onLoop(() -> loop.config.setAutoRead(true));
        loop.config.setAutoRead(false);
        loop.drain();
        assertTrue(loop.config.isAutoRead());
        assertFalse(loop.readInterest, "The regression fixture must reproduce the lost read interest");

        // Now perform the same disable + queued protocol-enable through the fix.
        assertSame(loop.config, MinecraftConnectionReadMode.update(loop.channel, loop.config, false));
        loop.tasks.add(() -> loop.config.setAutoRead(true));
        loop.drain();
        assertTrue(loop.config.isAutoRead());
        assertTrue(loop.readInterest, "The local login must remain able to receive the server response");
    }

    @Test void eventLoopCallUpdatesImmediatelyWithoutAnExtraTask() {
        Loop loop = new Loop(true);
        loop.onLoop(() -> MinecraftConnectionReadMode.update(loop.channel, loop.config, false));
        assertFalse(loop.config.isAutoRead());
        assertFalse(loop.readInterest);
        assertTrue(loop.tasks.isEmpty());
    }

    @Test void ordinaryRemoteConnectionsRetainVanillaReadModeBehavior() {
        Loop loop = new Loop(false);
        MinecraftConnectionReadMode.update(loop.channel, loop.config, false);
        assertFalse(loop.config.isAutoRead());
    }

    static final class Loop implements InvocationHandler {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        final EventLoop eventLoop;
        final Channel channel;
        final Config config;
        final boolean loopback;
        boolean inside;
        boolean readInterest = true;

        Loop(boolean loopback) {
            this.loopback = loopback;
            eventLoop = (EventLoop) Proxy.newProxyInstance(EventLoop.class.getClassLoader(), new Class<?>[]{EventLoop.class}, this);
            channel = (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(), new Class<?>[]{Channel.class}, this);
            config = new Config(this);
        }
        void onLoop(Runnable action) {
            boolean previous = inside;
            inside = true;
            try { action.run(); } finally { inside = previous; }
        }
        void drain() {
            while (!tasks.isEmpty()) onLoop(tasks.remove());
        }
        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "metadata": return new ChannelMetadata(false);
                case "eventLoop": return eventLoop;
                case "inEventLoop": return inside;
                case "remoteAddress": return new InetSocketAddress(loopback ? "127.0.0.1" : "192.0.2.1", 25565);
                case "config": return config;
                case "execute": tasks.add((Runnable) args[0]); return null;
                case "read": readInterest = true; return channel;
                case "toString": return "ReadModeRegressionChannel";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                default: throw new AssertionError("Unexpected test channel operation: " + method.getName());
            }
        }
    }

    static final class Config extends DefaultChannelConfig {
        final Loop loop;
        Runnable beforeOffLoopClear;
        Config(Loop loop) { super(loop.channel); this.loop = loop; }
        @Override protected void autoReadCleared() {
            if (loop.inside) {
                loop.readInterest = false;
            } else {
                Runnable interleave = beforeOffLoopClear;
                beforeOffLoopClear = null;
                if (interleave != null) interleave.run();
                loop.tasks.add(() -> loop.readInterest = false);
            }
        }
    }
}
