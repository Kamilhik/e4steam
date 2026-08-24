package link.e4steam.retro.forge;

import link.e4steam.retro.RetroBootstrap;
import link.e4steam.retro.RetroPlatform;
import link.e4steam.retro.RetroVersion;
import link.e4steam.steam.SteamAccessMode;
import link.e4steam.steam.SteamSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiShareToLan;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Loaded reflectively only on the physical client. */
public final class E4steamForge112Client implements RetroPlatform {
    private static final int ACCESS_MODE_BUTTON = 0xE451;
    private static final int INVITE_BUTTON = 0xE452;
    private final ConcurrentLinkedQueue<Runnable> clientTasks =
            new ConcurrentLinkedQueue<Runnable>();

    public E4steamForge112Client() {
        RetroBootstrap.install(RetroVersion.minecraft(), this);
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            // Minecraft renamed these methods between 1.9 and 1.10. Keeping
            // both spellings lets this one adapter compile for every branch.
            public String getCommandName() { return "e4steam"; }
            public String getName() { return "e4steam"; }

            public String getCommandUsage(ICommandSender sender) { return usage(); }
            public String getUsage(ICommandSender sender) { return usage(); }

            @Override public void execute(
                    MinecraftServer server,
                    ICommandSender sender,
                    String[] arguments
            ) {
                handle(arguments, sender);
            }

            public List<String> getTabCompletionOptions(
                    MinecraftServer server,
                    ICommandSender sender,
                    String[] arguments,
                    BlockPos position
            ) {
                return complete(arguments);
            }

            public List<String> getTabCompletions(
                    MinecraftServer server,
                    ICommandSender sender,
                    String[] arguments,
                    BlockPos position
            ) {
                return complete(arguments);
            }

            @Override public int getRequiredPermissionLevel() { return 0; }

            private String usage() {
                return "/e4steam <start|stop|restart|invite|doctor|addon|help>";
            }

            private void handle(String[] arguments, ICommandSender sender) {
                if (arguments.length <= 1
                        && RetroBootstrap.handleClientCommand(arguments.length == 0
                        ? "/e4steam" : "/e4steam " + arguments[0])) {
                    return;
                }
                E4steamForge112Client.this.showMessage(usage());
            }

            private List<String> complete(String[] arguments) {
                if (arguments.length == 1) {
                    return CommandBase.getListOfStringsMatchingLastWord(
                            arguments, RetroBootstrap.clientCommandNames());
                }
                return java.util.Collections.emptyList();
            }
        });
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Runnable task;
        while ((task = clientTasks.poll()) != null) task.run();
    }

    @Override public void execute(Runnable action) { clientTasks.add(action); }

    @Override public void connect(InetSocketAddress localAddress, String displayName) {
        Minecraft minecraft = Minecraft.getMinecraft();
        String endpoint = localAddress.getAddress().getHostAddress() + ':' + localAddress.getPort();
        minecraft.displayGuiScreen(new GuiConnecting(
                new GuiMultiplayer(new GuiMainMenu()), minecraft,
                new ServerData(displayName, endpoint, false)));
    }

    @Override public void showMessage(String message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.ingameGUI != null) {
            minecraft.ingameGUI.getChatGUI().printChatMessage(new TextComponentString(message));
        }
    }

    @Override public boolean copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return false;
        try {
            GuiScreen.setClipboardString(text);
            return text.equals(GuiScreen.getClipboardString());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override public void showTranslatedMessage(String translationKey, String fallback) {
        showMessage(I18n.hasKey(translationKey) ? I18n.format(translationKey) : fallback);
    }

    @Override public void showSharingReady(String endpoint) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.ingameGUI == null) return;

        ITextComponent message = new TextComponentString(
                I18n.format("text.e4steam_minecraft.domainAssigned") + " ");
        TextComponentString address = new TextComponentString(endpoint);
        address.setStyle(new Style()
                .setColor(TextFormatting.GREEN)
                .setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND, "/e4steam copy"))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TextComponentString(I18n.format(
                                "text.e4steam_minecraft.addressCopyHelp")))));
        message.appendSibling(address);

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

    @SubscribeEvent public void initializeScreen(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiShareToLan) {
            event.getButtonList().add(new GuiButton(
                    ACCESS_MODE_BUTTON,
                    event.getGui().width / 2 - 155,
                    event.getGui().height - 52,
                    310,
                    20,
                    accessModeLabel(RetroBootstrap.selectedAccessMode())
            ));
            return;
        }

        if (event.getGui() instanceof GuiIngameMenu && sharingIsActive()) {
            event.getButtonList().add(new GuiButton(
                    INVITE_BUTTON,
                    Math.max(4, event.getGui().width - 154),
                    6,
                    150,
                    20,
                    I18n.format("text.e4steam_minecraft.inviteFriends")
            ));
        }
    }

    @SubscribeEvent public void buttonPressed(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.getButton().id == ACCESS_MODE_BUTTON
                && event.getGui() instanceof GuiShareToLan) {
            event.getButton().displayString = accessModeLabel(RetroBootstrap.cycleAccessMode());
        } else if (event.getButton().id == INVITE_BUTTON
                && event.getGui() instanceof GuiIngameMenu) {
            RetroBootstrap.handleClientCommand("/e4steam invite");
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
