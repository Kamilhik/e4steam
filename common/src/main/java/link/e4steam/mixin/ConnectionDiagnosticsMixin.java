package link.e4steam.mixin;

import io.netty.channel.ChannelHandlerContext;
import link.e4steam.steam.SteamConnectionBridge;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostic builds only: count decoded packets without recording their contents. */
@Mixin(Connection.class)
public abstract class ConnectionDiagnosticsMixin {
    @Shadow public abstract java.net.SocketAddress getRemoteAddress();
    @Shadow public abstract boolean isMemoryConnection();
    @Unique private final java.util.concurrent.atomic.AtomicLong e4steam$packets = new java.util.concurrent.atomic.AtomicLong();
    @Unique private final java.util.concurrent.atomic.AtomicLong e4steam$chunks = new java.util.concurrent.atomic.AtomicLong();
    @Unique private final java.util.concurrent.atomic.AtomicLong e4steam$positions = new java.util.concurrent.atomic.AtomicLong();
    @Unique private final java.util.concurrent.atomic.AtomicLong e4steam$teleportAcks = new java.util.concurrent.atomic.AtomicLong();
    @Unique private final java.util.concurrent.atomic.AtomicLong e4steam$keepalives = new java.util.concurrent.atomic.AtomicLong();
    @Unique private long e4steam$nextLog;
    @Unique private final java.util.concurrent.atomic.AtomicLong e4steam$errors = new java.util.concurrent.atomic.AtomicLong();

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void e4steam$decodedPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (!SteamConnectionBridge.NETWORK_DIAGNOSTICS) return;
        long sequence = e4steam$packets.incrementAndGet();
        // Forge retains Mojang class names. No packet fields, names, or payloads are logged.
        String name = packet.getClass().getSimpleName();
        if (!isMemoryConnection() && sequence <= 16) {
            LogManager.getLogger("e4steam").info(
                    "[E4NET-PACKET] connection={} sequence={} type={} autoRead={} active={}",
                    Integer.toHexString(System.identityHashCode(this)), sequence, name,
                    context.channel().config().isAutoRead(), context.channel().isActive());
        }
        if (name.equals("ClientboundLevelChunkWithLightPacket")) e4steam$chunks.incrementAndGet();
        if (name.equals("ClientboundPlayerPositionPacket")) e4steam$positions.incrementAndGet();
        if (name.equals("ServerboundAcceptTeleportationPacket")) e4steam$teleportAcks.incrementAndGet();
        if (name.equals("ClientboundKeepAlivePacket") || name.equals("ServerboundKeepAlivePacket"))
            e4steam$keepalives.incrementAndGet();
    }

    @Inject(method = "exceptionCaught", at = @At("HEAD"))
    private void e4steam$networkFailure(ChannelHandlerContext context, Throwable failure, CallbackInfo ci) {
        if (!SteamConnectionBridge.NETWORK_DIAGNOSTICS || isMemoryConnection()) return;
        if (e4steam$errors.incrementAndGet() > 4) return;
        // Log exception types only; decoder exception messages can include packet contents.
        Throwable cause = failure.getCause();
        LogManager.getLogger("e4steam").warn(
                "[E4NET-ERROR] connection={} exception={} cause={} autoRead={} active={}",
                Integer.toHexString(System.identityHashCode(this)), failure.getClass().getName(),
                cause == null ? "none" : cause.getClass().getName(),
                context.channel().config().isAutoRead(), context.channel().isActive());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void e4steam$logDecodedPackets(CallbackInfo ci) {
        if (!SteamConnectionBridge.NETWORK_DIAGNOSTICS || isMemoryConnection() || e4steam$packets.get() == 0) return;
        long now = System.currentTimeMillis();
        if (now < e4steam$nextLog) return;
        e4steam$nextLog = now + 5_000;
        java.net.SocketAddress remote = getRemoteAddress();
        int bridgePort = remote instanceof java.net.InetSocketAddress
                ? ((java.net.InetSocketAddress) remote).getPort() : -1;
        LogManager.getLogger("e4steam").info(
                "[E4NET-MC] connection={} bridgePort={} decoded={} chunks={} positions={} teleportAcks={} keepalives={} errors={}",
                Integer.toHexString(System.identityHashCode(this)), bridgePort, e4steam$packets.get(),
                e4steam$chunks.get(), e4steam$positions.get(), e4steam$teleportAcks.get(), e4steam$keepalives.get(), e4steam$errors.get());
    }
}
