package link.e4steam.mixin;

import link.e4steam.Config;
import link.e4steam.E4steamClient;
import link.e4steam.MinecraftUiCompat;
import link.e4steam.Mirror;
import link.e4steam.internal.api.CoreAccessModeCatalog;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds e4steam access controls to the replacement LAN screen used by Minecraft 26.x. */
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.screens.MultiplayerOptionsScreen", remap = false)
public abstract class MultiplayerOptionsScreenMixin extends Screen {
    protected MultiplayerOptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0, remap = false)
    private void e4steam$addSteamAccessMode(CallbackInfo ci) {
        if (!Config.INSTANCE.hostEnabled.value()) {
            return;
        }
        CoreAccessModeCatalog.Selection initialMode = CoreAccessModeCatalog.normalize(
                E4steamClient.selectedAccessMode, E4steamClient.selectedCustomAccessMode);
        e4steam$select(initialMode);

        Button accessButton = MinecraftUiCompat.button(
                e4steam$accessModeName(initialMode),
                button -> {
                    CoreAccessModeCatalog.Selection next = CoreAccessModeCatalog.next(
                            E4steamClient.selectedAccessMode, E4steamClient.selectedCustomAccessMode);
                    e4steam$choose(next, button);
                },
                width / 2 - 155,
                height - 56,
                310,
                20
        );
        MinecraftUiCompat.tooltip(
                accessButton,
                Mirror.translatable("text.e4steam_minecraft.accessModeHelp")
        );
        addRenderableWidget(accessButton);
    }

    @Unique
    private static Component e4steam$accessModeName(CoreAccessModeCatalog.Selection mode) {
        Component label = Mirror.append(
                Mirror.translatable("text.e4steam_minecraft.accessMode"),
                Mirror.literal(": ")
        );
        return Mirror.append(label, Mirror.translatable(mode.translationKey()));
    }

    @Unique
    private static void e4steam$select(CoreAccessModeCatalog.Selection selection) {
        E4steamClient.selectedAccessMode = selection.mode();
        E4steamClient.selectedCustomAccessMode = selection.customModeId();
    }

    @Unique
    private void e4steam$choose(CoreAccessModeCatalog.Selection selection, Button button) {
        e4steam$select(selection);
        button.setMessage(e4steam$accessModeName(selection));
    }

    /**
     * Minecraft 26.x applies the selected multiplayer scope only after the
     * user presses the bottom Apply/Open button. Keep addon warnings at that
     * same decision point instead of interrupting access-mode selection.
     */
    @Inject(method = "changeMultiplayerScope", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private void e4steam$confirmCustomAccessOnOpen(IntegratedServer server, CallbackInfo ci) {
        CoreAccessModeCatalog.Selection selection = CoreAccessModeCatalog.normalize(
                E4steamClient.selectedAccessMode, E4steamClient.selectedCustomAccessMode);
        if (!selection.requiresConfirmation()
                || E4steamClient.customAccessConfirmed(selection.customModeId())) {
            return;
        }

        ci.cancel();
        Screen parent = (Screen) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftUiCompat.setScreen(minecraft, new ConfirmScreen(confirmed -> {
            if (!confirmed) {
                MinecraftUiCompat.setScreen(minecraft, parent);
                return;
            }
            E4steamClient.confirmCustomAccess(selection.customModeId());
            MinecraftUiCompat.setScreen(minecraft, null);
            e4steam$changeMultiplayerScope(server);
        }, Mirror.translatable(selection.confirmationTitleKey()),
                Mirror.translatable(selection.confirmationMessageKey())));
    }

    @org.spongepowered.asm.mixin.Shadow(prefix = "e4steam$")
    private void e4steam$changeMultiplayerScope(IntegratedServer server) {
        throw new AssertionError("mixin shadow");
    }
}
