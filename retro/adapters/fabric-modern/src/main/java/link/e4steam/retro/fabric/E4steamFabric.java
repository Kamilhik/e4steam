package link.e4steam.retro.fabric;

import link.e4steam.retro.RetroBootstrap;
import link.e4steam.retro.RetroPlatform;
import link.e4steam.retro.RetroVersion;
import link.e4steam.retro.ui.RetroModernChatControls;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.TextComponent;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;

public class E4steamFabric implements ClientModInitializer, RetroPlatform {
    private final ConcurrentLinkedQueue<Runnable> clientTasks =
            new ConcurrentLinkedQueue<Runnable>();

    @Override public void onInitializeClient() {
        RetroBootstrap.install(RetroVersion.minecraft(), this);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Runnable task;
            while ((task = clientTasks.poll()) != null) task.run();
        });
    }

    @Override public void execute(Runnable action) { clientTasks.add(action); }

    @Override public void connect(InetSocketAddress localAddress, String displayName) {
        Minecraft minecraft = Minecraft.getInstance();
        String endpoint = localAddress.getAddress().getHostAddress() + ':' + localAddress.getPort();
        minecraft.setScreen(new ConnectScreen(
                new JoinMultiplayerScreen(new TitleScreen()), minecraft,
                new ServerData(displayName, endpoint, false)));
    }

    @Override public void showMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) minecraft.gui.getChat().addMessage(new TextComponent(message));
    }

    @Override public boolean copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return false;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.keyboardHandler.setClipboard(text);
            return text.equals(minecraft.keyboardHandler.getClipboard());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override public void showTranslatedMessage(String translationKey, String fallback) {
        RetroModernChatControls.showTranslatedMessage(translationKey, fallback);
    }

    @Override public void showSharingReady(String endpoint) {
        RetroModernChatControls.showSharingReady(endpoint);
    }
}
