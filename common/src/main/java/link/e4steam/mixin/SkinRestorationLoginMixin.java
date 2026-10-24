package link.e4steam.mixin;

import link.e4steam.skin.SkinRestoration;
import link.e4steam.lan.LanReflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Field;

/** Async cosmetic fallback; existing skin providers take priority. */
@Mixin(targets = "net.minecraft.server.network.ServerLoginPacketListenerImpl")
public abstract class SkinRestorationLoginMixin {
    @Unique private final SkinRestoration.LoginState e4steam$skins = new SkinRestoration.LoginState();

    @Inject(method="/^(handleAcceptedLogin|acceptPlayer|method_14384|m_10055_)$/", at=@At("HEAD"), cancellable=true, require=0, remap=false)
    private void e4steam$prepareLegacySkin(CallbackInfo ci) {
        try {
            Field field=LanReflection.field(this,"gameProfile","profile","field_14160","f_10021_");
            if (field==null) return;
            Object original=field.get(this);
            if (!e4steam$skins.prepare(original)) { ci.cancel(); return; }
            if (e4steam$skins.profile()!=original) field.set(this,e4steam$skins.profile());
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    @Inject(method="/^(verifyLoginAndFinishConnectionSetup|method_52419|m_294778_)$/", at=@At("HEAD"), cancellable=true, require=0, remap=false)
    private void e4steam$prepareModernSkin(@Coerce Object profile, CallbackInfo ci) {
        try {
            if (!e4steam$skins.prepare(profile)) { ci.cancel(); return; }
            if (e4steam$skins.profile()!=profile) {
                Field field=LanReflection.field(this,"authenticatedProfile","field_45029","f_291112_");
                // Verification is retried by the vanilla tick with the new immutable profile.
                if (field!=null) { field.set(this,e4steam$skins.profile()); ci.cancel(); }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }
}
