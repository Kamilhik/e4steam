package link.e4steam.retro.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import link.e4steam.retro.RetroBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds local e4steam commands to 1.14.4's Brigadier completion tree. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenCommandSuggestions114Mixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void e4steam$registerClientSuggestions(CallbackInfo info) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;

        CommandDispatcher<SharedSuggestionProvider> dispatcher = connection.getCommands();
        if (dispatcher == null || dispatcher.getRoot().getChild("e4steam") != null) return;

        LiteralArgumentBuilder<SharedSuggestionProvider> root =
                LiteralArgumentBuilder.literal("e4steam");
        for (String command : RetroBootstrap.clientCommandNames()) {
            root.then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal(command));
        }
        dispatcher.register(root);
    }
}
