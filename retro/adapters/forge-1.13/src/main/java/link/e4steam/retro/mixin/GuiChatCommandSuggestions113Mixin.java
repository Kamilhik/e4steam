package link.e4steam.retro.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import link.e4steam.retro.RetroBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.command.ISuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds local e4steam commands to Minecraft 1.13.2's Brigadier tree. */
@Mixin(GuiChat.class)
public abstract class GuiChatCommandSuggestions113Mixin {
    @Inject(method = "initGui", at = @At("TAIL"))
    private void e4steam$registerClientSuggestions(CallbackInfo info) {
        NetHandlerPlayClient connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;

        CommandDispatcher<ISuggestionProvider> dispatcher = connection.func_195515_i();
        if (dispatcher == null || dispatcher.getRoot().getChild("e4steam") != null) return;

        LiteralArgumentBuilder<ISuggestionProvider> root =
                LiteralArgumentBuilder.literal("e4steam");
        for (String command : RetroBootstrap.clientCommandNames()) {
            root.then(LiteralArgumentBuilder.<ISuggestionProvider>literal(command));
        }
        dispatcher.register(root);
    }
}
