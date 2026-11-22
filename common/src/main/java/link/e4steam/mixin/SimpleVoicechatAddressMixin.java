package link.e4steam.mixin;
import link.e4steam.steam.SteamRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Optional compatibility, without a Voice Chat dependency or effects on direct IP connections. */
@Pseudo
@Mixin(targets={"de.maxhenkel.voicechat.voice.client.InitializationData","de.maxhenkel.voicechat.net.SecretPacket"}, remap=false)
public abstract class SimpleVoicechatAddressMixin {
 @Inject(method="getServerIP",at=@At("HEAD"),cancellable=true,require=0,remap=false)
 private void e4steam$voiceIp(CallbackInfoReturnable<String> ci) {
  java.net.InetSocketAddress proxy=SteamRuntime.get().simpleVoicechatProxy();if(proxy!=null)ci.setReturnValue("127.0.0.1");
 }
 @Inject(method="getVoiceHost",at=@At("HEAD"),cancellable=true,require=0,remap=false)
 private void e4steam$voiceHost(CallbackInfoReturnable<String> ci) {
  java.net.InetSocketAddress proxy=SteamRuntime.get().simpleVoicechatProxy();if(proxy!=null)ci.setReturnValue("127.0.0.1:"+proxy.getPort());
 }
 @Inject(method="getServerPort",at=@At("HEAD"),cancellable=true,require=0,remap=false)
 private void e4steam$voicePort(CallbackInfoReturnable<Integer> ci) {
  java.net.InetSocketAddress proxy=SteamRuntime.get().simpleVoicechatProxy();if(proxy!=null)ci.setReturnValue(proxy.getPort());
 }
}
