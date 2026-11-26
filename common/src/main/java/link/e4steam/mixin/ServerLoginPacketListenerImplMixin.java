package link.e4steam.mixin;

import com.mojang.authlib.GameProfile;
import link.e4steam.steam.SteamMinecraftIdentity;
import link.e4steam.steam.SteamMinecraftAuthentication;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.chat.Component;

/** Uses the authenticated Steam identity instead of Mojang auth for Steam bridge guests only. */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {
    private static final int E4STEAM_LOGIN_TIMEOUT_TICKS = 2_400;

    @Shadow @Final private Connection connection;
    @Shadow @Final private MinecraftServer server;
    private long e4steam$authenticatedSteamId;
    private boolean e4steam$identityRejected;

    private long e4steam$authenticatedSteamId() {
        long current = e4steam$authenticatedSteamId;
        if (current != 0L) return current;
        current = SteamMinecraftAuthentication.authenticatedPeer(connection.getRemoteAddress());
        if (current != 0L) e4steam$authenticatedSteamId = current;
        return current;
    }

    @Redirect(
            method = "handleHello",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;usesAuthentication()Z"
            )
    )
    private boolean e4steam$useSteamAuthentication(MinecraftServer server) {
        long authenticatedSteamId = e4steam$authenticatedSteamId();
        return authenticatedSteamId == 0 ? server.usesAuthentication() : SteamMinecraftAuthentication.usesMinecraftSession(connection.getRemoteAddress());
    }

    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void e4steam$rejectDirectDedicatedLogin(CallbackInfo ci) {
        if (e4steam$authenticatedSteamId() != 0L
                || !SteamMinecraftAuthentication.rejectUntrustedDedicatedIngress(
                        connection.getRemoteAddress())) return;
        connection.disconnect(Component.literal(
                "This server requires an authenticated e4steam connection"));
        ci.cancel();
    }

    /** Steam identities have no Mojang-signed profile key; retain vanilla policy for other ingress. */
    @Redirect(method="/^(handleAcceptedLogin|acceptPlayer|method_14384|m_10055_)$/", remap=false,
            at=@At(value="INVOKE", target="Lnet/minecraft/server/MinecraftServer;enforceSecureProfile()Z", remap=true), require=0)
    private boolean e4steam$requireMojangProfileKey(MinecraftServer server) {
        return (e4steam$authenticatedSteamId() == 0L || e4steam$verifiedSession() && server.usesAuthentication()) && server.enforceSecureProfile();
    }

    /** Binds the UUID to Steam while preserving the Minecraft nickname. */
    @ModifyVariable(
            method = "startClientVerification",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private GameProfile e4steam$bindProfileToSteamIdentity(GameProfile original) {
        long authenticatedSteamId = e4steam$authenticatedSteamId();
        if (authenticatedSteamId == 0) {
            return original;
        }
        try {return link.e4steam.steam.SteamLoginIdentity.resolve(server,original,authenticatedSteamId,e4steam$verifiedSession());}
        catch(java.io.IOException unavailable){e4steam$identityRejected=true;connection.disconnect(Component.literal(unavailable.getMessage()));return original;}
    }

    /** The pre-1.20.2 login state machine stores its accepted profile in a field. */
    @Inject(method="/^(handleAcceptedLogin|acceptPlayer|method_14384|m_10055_)$/", at=@At("HEAD"), cancellable=true, require=0, remap=false)
    private void e4steam$bindOlderProfile(CallbackInfo ci) {
        long peer=e4steam$authenticatedSteamId();
        if (peer==0L) return;
        java.lang.reflect.Field field=link.e4steam.lan.LanReflection.field(this,"gameProfile","profile","field_14160","f_10021_");
        if (field==null) return;
        try {
            GameProfile original=(GameProfile)field.get(this);
            try {field.set(this,link.e4steam.steam.SteamLoginIdentity.resolve(server,original,peer,e4steam$verifiedSession()));}
            catch(java.io.IOException unavailable){connection.disconnect(Component.literal(unavailable.getMessage()));ci.cancel();return;}
            if(e4steam$verifiedSession() && server.usesAuthentication())return;
            // 1.19/1.19.2 keys sign the Mojang UUID, not the authenticated Steam UUID.
            java.lang.reflect.Field key=link.e4steam.lan.LanReflection.field(this,
                    "profilePublicKeyData", "publicKeyData", "field_39743");
            if (key == null) {
                for (java.lang.reflect.Field candidate : this.getClass().getDeclaredFields()) {
                    String type=candidate.getType().getTypeName();
                    if (type.endsWith("ProfilePublicKey$Data") || type.equals("net.minecraft.class_7428$class_7443")) {
                        candidate.setAccessible(true); key=candidate; break;
                    }
                }
            }
            if (key != null) key.set(this,null);
        } catch (IllegalAccessException failure) { throw new IllegalStateException("Cannot bind accepted Steam profile",failure); }
    }

    /** Forge can send a large registry snapshot before accepting the player. */
    // Forge renamed this tick between 1.18.2 and 1.19; use both runtime aliases.
    @ModifyConstant(method = "/^(tick|method_18784|m_10050_|m_9933_)$/", remap = false,
            constant = @Constant(intValue = 600), require = 0)
    private int e4steam$extendAuthenticatedSteamLoginTimeout(int vanillaTimeout) {
        long authenticatedSteamId = e4steam$authenticatedSteamId();
        return authenticatedSteamId == 0 ? vanillaTimeout : E4STEAM_LOGIN_TIMEOUT_TICKS;
    }
    @Inject(method="startClientVerification",at=@At("HEAD"),cancellable=true,require=0)
    private void e4steam$stopRejectedIdentity(GameProfile profile,CallbackInfo ci) {if(e4steam$identityRejected || !connection.isConnected())ci.cancel();}
    @Inject(method="verifyLoginAndFinishConnectionSetup",at=@At("HEAD"),cancellable=true,require=0)
    private void e4steam$rejectFailedVerification(GameProfile profile,CallbackInfo ci) {if(e4steam$identityRejected)ci.cancel();}
    @Inject(method="/^(tick|method_18784|m_10050_|m_9933_)$/",remap=false,
            at=@At("HEAD"),cancellable=true,require=1)
    private void e4steam$stopFailedLogin(CallbackInfo ci) {if(e4steam$identityRejected)ci.cancel();}
    private boolean e4steam$verifiedSession() {
        return SteamMinecraftAuthentication.usesMinecraftSession(connection.getRemoteAddress())
                && link.e4steam.steam.MinecraftConnectionEncryption.isEncrypted(connection);
    }

}
