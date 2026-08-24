package link.e4steam.retro.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import link.e4steam.retro.RetroBootstrap;
import link.e4steam.retro.RetroPlatform;
import link.e4steam.retro.RetroVersion;
import link.e4steam.retro.ui.RetroModernChatControls;
import link.e4steam.steam.SteamAccessMode;
import link.e4steam.steam.SteamSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Forge 1.14 fallback adapter. Its early ModLauncher does not reliably apply
 * the production Mixin hooks, so LAN lifecycle, commands and buttons are
 * provided through Forge events as well.
 */
public final class E4steamForge114Client implements RetroPlatform {
    private final ConcurrentLinkedQueue<Runnable> clientTasks =
            new ConcurrentLinkedQueue<Runnable>();
    private int observedLanPort;
    private ClientPacketListener registeredCommandConnection;

    public E4steamForge114Client() {
        E4steamForge114Hooks.preload();
        RetroBootstrap.install(RetroVersion.minecraft(), this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Runnable task;
        while ((task = clientTasks.poll()) != null) task.run();
        detectLanPort();
        registerClientCommands();
    }

    private void detectLanPort() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        int currentPort = server != null && server.isPublished() ? server.getPort() : 0;
        if (currentPort == observedLanPort) return;

        int previousPort = observedLanPort;
        observedLanPort = currentPort;
        if (previousPort > 0) RetroBootstrap.relayClosedFallback(previousPort);
        if (currentPort > 0) RetroBootstrap.relayBoundFallback(currentPort);
    }

    private void registerClientCommands() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            registeredCommandConnection = null;
            return;
        }

        CommandDispatcher<SharedSuggestionProvider> dispatcher = connection.getCommands();
        if (dispatcher == null) return;
        if (connection == registeredCommandConnection
                && dispatcher.getRoot().getChild("e4steam") != null) return;

        if (dispatcher.getRoot().getChild("e4steam") == null) {
            LiteralArgumentBuilder<SharedSuggestionProvider> root =
                    LiteralArgumentBuilder.literal("e4steam");
            for (String command : RetroBootstrap.clientCommandNames()) {
                root.then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal(command));
            }
            dispatcher.register(root);
        }
        registeredCommandConnection = connection;
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

    @SubscribeEvent public void sendChat(ClientChatEvent event) {
        if (RetroBootstrap.handleClientCommand(event.getMessage())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent public void initializeScreen(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof ShareToLanScreen) {
            Button accessMode = new Button(
                    event.getGui().width / 2 - 155,
                    event.getGui().height - 52,
                    310,
                    20,
                    accessModeLabel(RetroBootstrap.selectedAccessMode()),
                    button -> button.setMessage(accessModeLabel(
                            RetroBootstrap.cycleAccessMode()))
            );
            event.addWidget(accessMode);
            return;
        }

        if (event.getGui() instanceof PauseScreen && sharingIsActive()) {
            event.addWidget(new Button(
                    Math.max(4, event.getGui().width - 154),
                    6,
                    150,
                    20,
                    I18n.get("text.e4steam_minecraft.inviteFriends"),
                    button -> RetroBootstrap.handleClientCommand("/e4steam invite")
            ));
        }
    }

    private static boolean sharingIsActive() {
        SteamSession session = link.e4steam.E4steamClient.session;
        return session != null
                && session.state() != SteamSession.State.STOPPED
                && session.state() != SteamSession.State.STOPPING
                && session.state() != SteamSession.State.UNHEALTHY;
    }

    private static String accessModeLabel(SteamAccessMode mode) {
        return I18n.get("text.e4steam_minecraft.accessMode") + ": "
                + I18n.get(mode.translationKey());
    }
}
