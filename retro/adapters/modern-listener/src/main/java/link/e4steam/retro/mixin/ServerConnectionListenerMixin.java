/* Adapted from xhyrom/e4mc-retro under Apache-2.0; see docs/RETRO_PORTING.md. */
package link.e4steam.retro.mixin;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ServerChannel;
import link.e4steam.Agnos;
import link.e4steam.E4steamClient;
import link.e4steam.retro.RetroBootstrap;
import link.e4steam.retro.RetroDedicatedBootstrap;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

@Mixin(ServerConnectionListener.class)
public abstract class ServerConnectionListenerMixin {
    @Shadow public abstract void startTcpServerListener(InetAddress address, int port)
            throws IOException;
    @Unique private static final ThreadLocal<Boolean> E4STEAM_ADDING_RELAY =
            new ThreadLocal<Boolean>() {
                @Override protected Boolean initialValue() { return Boolean.FALSE; }
            };

    @Inject(method = "startTcpServerListener", at = @At("HEAD"))
    private void e4steam$addRelay(InetAddress address, int port, CallbackInfo info)
            throws IOException {
        if (Agnos.isClient() && !E4STEAM_ADDING_RELAY.get().booleanValue()) {
            E4STEAM_ADDING_RELAY.set(Boolean.TRUE);
            try { startTcpServerListener(InetAddress.getLoopbackAddress(), 0); }
            finally { E4STEAM_ADDING_RELAY.set(Boolean.FALSE); }
        }
    }

    @Redirect(method = "startTcpServerListener", at = @At(value = "INVOKE", target =
            "Lio/netty/bootstrap/ServerBootstrap;localAddress(Ljava/net/InetAddress;I)Lio/netty/bootstrap/AbstractBootstrap;"))
    private AbstractBootstrap<ServerBootstrap, ServerChannel> e4steam$loopbackOnly(
            ServerBootstrap bootstrap, InetAddress address, int port) {
        if (E4STEAM_ADDING_RELAY.get().booleanValue()) {
            return bootstrap.localAddress(InetAddress.getLoopbackAddress(), 0);
        }
        return !Agnos.isClient() && RetroDedicatedBootstrap.enabled()
                ? bootstrap.localAddress(InetAddress.getLoopbackAddress(), port)
                : bootstrap.localAddress(address, port);
    }

    @Redirect(method = "startTcpServerListener", at = @At(value = "INVOKE", target =
            "Lio/netty/bootstrap/ServerBootstrap;bind()Lio/netty/channel/ChannelFuture;"))
    private ChannelFuture e4steam$publishRelay(ServerBootstrap bootstrap) {
        ChannelFuture future = bootstrap.bind();
        if (E4STEAM_ADDING_RELAY.get().booleanValue()) {
            future.addListener(new ChannelFutureListener() {
                @Override public void operationComplete(ChannelFuture completed) {
                    if (completed.isSuccess()) {
                        InetSocketAddress local = (InetSocketAddress) completed.channel().localAddress();
                        RetroBootstrap.relayBound(local.getPort());
                    } else {
                        E4steamClient.LOGGER.error("Could not bind the e4steam loopback relay",
                                completed.cause());
                    }
                }
            });
        } else if (!Agnos.isClient() && RetroDedicatedBootstrap.enabled()) {
            future.addListener(new ChannelFutureListener() {
                @Override public void operationComplete(ChannelFuture completed) {
                    if (completed.isSuccess()) {
                        InetSocketAddress local = (InetSocketAddress) completed.channel().localAddress();
                        RetroDedicatedBootstrap.minecraftListening(
                                local.getAddress(), local.getPort());
                        RetroDedicatedBootstrap.minecraftReady();
                    } else {
                        E4steamClient.LOGGER.error("Could not bind the e4steam dedicated listener",
                                completed.cause());
                    }
                }
            });
        }
        return future;
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void e4steam$closeRelay(CallbackInfo info) {
        if (Agnos.isClient()) RetroBootstrap.relayClosed();
        else RetroDedicatedBootstrap.minecraftStopped();
    }
}
