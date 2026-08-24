package link.e4steam.retro.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import link.e4steam.retro.RetroBootstrap;
import link.e4steam.retro.RetroPlatform;
import link.e4steam.retro.RetroVersion;
import link.e4steam.steam.SteamAccessMode;
import link.e4steam.steam.SteamSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiConnecting;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiShareToLan;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Loaded reflectively only on the physical client. */
public final class E4steamForge113Client implements RetroPlatform {
    private static final int ACCESS_MODE_BUTTON = 0xE451;
    private static final int INVITE_BUTTON = 0xE452;
    private final ConcurrentLinkedQueue<Runnable> clientTasks =
            new ConcurrentLinkedQueue<Runnable>();
    private int observedLanPort;
    private NetHandlerPlayClient registeredCommandConnection;

    public E4steamForge113Client() {
        E4steamForge113Hooks.preload();
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
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getIntegratedServer();
        int currentPort = server != null && server.getPublic()
                ? server.getServerPort()
                : 0;
        if (currentPort == observedLanPort) return;

        int previousPort = observedLanPort;
        observedLanPort = currentPort;
        if (previousPort > 0) RetroBootstrap.relayClosedFallback(previousPort);
        if (currentPort > 0) RetroBootstrap.relayBoundFallback(currentPort);
    }

    private void registerClientCommands() {
        NetHandlerPlayClient connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            registeredCommandConnection = null;
            return;
        }

        CommandDispatcher<ISuggestionProvider> dispatcher = connection.func_195515_i();
        if (dispatcher == null) return;
        if (connection == registeredCommandConnection
                && dispatcher.getRoot().getChild("e4steam") != null) return;

        if (dispatcher.getRoot().getChild("e4steam") == null) {
            LiteralArgumentBuilder<ISuggestionProvider> root =
                    LiteralArgumentBuilder.literal("e4steam");
            for (String command : RetroBootstrap.clientCommandNames()) {
                root.then(LiteralArgumentBuilder.<ISuggestionProvider>literal(command));
            }
            dispatcher.register(root);
        }
        registeredCommandConnection = connection;
    }

    @Override public void execute(Runnable action) { clientTasks.add(action); }

    @Override public void connect(InetSocketAddress localAddress, String displayName) {
        Minecraft minecraft = Minecraft.getInstance();
        String endpoint = localAddress.getAddress().getHostAddress() + ':' + localAddress.getPort();
        minecraft.displayGuiScreen(new GuiConnecting(
                new GuiMultiplayer(new GuiMainMenu()), minecraft,
                new ServerData(displayName, endpoint, false)));
    }

    @Override public void showMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.ingameGUI != null) {
            minecraft.ingameGUI.getChatGUI().printChatMessage(new TextComponentString(message));
        }
    }

    @Override public boolean copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return false;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.keyboardListener.setClipboardString(text);
            return text.equals(minecraft.keyboardListener.getClipboardString());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override public void showTranslatedMessage(String translationKey, String fallback) {
        showMessage(I18n.hasKey(translationKey) ? I18n.format(translationKey) : fallback);
    }

    @Override public void showSharingReady(String endpoint) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.ingameGUI == null) return;

        TextComponentString address = new TextComponentString(endpoint);
        address.setStyle(new Style()
                .setColor(TextFormatting.GREEN)
                .setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND, "/e4steam copy"))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TextComponentString(I18n.format(
                                "text.e4steam_minecraft.addressCopyHelp")))));
        ITextComponent message = new TextComponentTranslation(
                "text.e4steam_minecraft.domainAssigned", address);

        TextComponentString invite = new TextComponentString("[" +
                I18n.format("text.e4steam_minecraft.inviteFriends") + "]");
        invite.setStyle(new Style()
                .setColor(TextFormatting.BLUE)
                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/e4steam invite"))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TextComponentString(I18n.format(
                                "text.e4steam_minecraft.inviteFriendsHelp")))));
        message.appendText(" ").appendSibling(invite);

        TextComponentString stop = new TextComponentString("[" +
                I18n.format("text.e4steam_minecraft.clickToStop") + "]");
        stop.setStyle(new Style()
                .setColor(TextFormatting.RED)
                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/e4steam stop"))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TextComponentString(I18n.format(
                                "text.e4steam_minecraft.stopSharingHelp")))));
        message.appendText(" ").appendSibling(stop);
        minecraft.ingameGUI.getChatGUI().printChatMessage(message);
    }

    @SubscribeEvent public void sendChat(ClientChatEvent event) {
        if (RetroBootstrap.handleClientCommand(event.getMessage())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent public void initializeScreen(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiShareToLan) {
            event.addButton(new GuiButton(
                    ACCESS_MODE_BUTTON,
                    event.getGui().width / 2 - 155,
                    event.getGui().height - 52,
                    310,
                    20,
                    accessModeLabel(RetroBootstrap.selectedAccessMode())
            ) {
                @Override public void onClick(double mouseX, double mouseY) {
                    displayString = accessModeLabel(RetroBootstrap.cycleAccessMode());
                }
            });
            return;
        }

        if (event.getGui() instanceof GuiIngameMenu && sharingIsActive()) {
            event.addButton(new GuiButton(
                    INVITE_BUTTON,
                    Math.max(4, event.getGui().width - 154),
                    6,
                    150,
                    20,
                    I18n.format("text.e4steam_minecraft.inviteFriends")
            ) {
                @Override public void onClick(double mouseX, double mouseY) {
                    RetroBootstrap.handleClientCommand("/e4steam invite");
                }
            });
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
        return I18n.format("text.e4steam_minecraft.accessMode") + ": "
                + I18n.format(mode.translationKey());
    }
}
