package link.e4steam.mixin;

import link.e4steam.steam.SteamAddress;
import link.e4steam.steam.SteamDedicatedAddress;
import link.e4steam.steam.SteamNamedAddress;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.Optional;

/** A queued status ping must never start a Steam join merely because ConnectScreen just opened. */
@Mixin(ServerStatusPinger.class)
public abstract class ServerStatusPingerMixin {
    // Name-only selectors cover the two- and three-callback overloads used across these versions.
    @Redirect(method="/^(pingServer|method_3003|m_105459_)$/",remap=false,at=@At(value="INVOKE",remap=true,
            target="Lnet/minecraft/client/multiplayer/resolver/ServerNameResolver;resolveAddress(Lnet/minecraft/client/multiplayer/resolver/ServerAddress;)Ljava/util/Optional;"),require=0)
    private Optional<ResolvedServerAddress> e4steam$skipSteamStatusProbe(ServerNameResolver resolver,ServerAddress address) {
        String host=address.getHost();
        if(SteamAddress.tryParse(host).isPresent()||SteamDedicatedAddress.tryParse(host).isPresent()
                ||SteamNamedAddress.tryParse(host).isPresent())return Optional.empty();
        return link.e4steam.steam.SteamResolverContext.statusProbe(()->resolver.resolveAddress(address));
    }
}
