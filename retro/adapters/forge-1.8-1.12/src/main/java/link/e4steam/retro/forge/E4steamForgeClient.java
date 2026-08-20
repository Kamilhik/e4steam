package link.e4steam.retro.forge;

import link.e4steam.retro.RetroBootstrap;
import link.e4steam.retro.RetroPlatform;
import link.e4steam.retro.RetroVersion;
import link.e4steam.steam.SteamAccessMode;
import link.e4steam.steam.SteamSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiShareToLan;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Loaded reflectively only on the physical client. */
public final class E4steamForgeClient implements RetroPlatform {
    private static final int ACCESS_MODE_BUTTON = 0xE451;
    private static final int INVITE_BUTTON = 0xE452;
    private final ConcurrentLinkedQueue<Runnable> clientTasks =
            new ConcurrentLinkedQueue<Runnable>();

    public E4steamForgeClient() {
        RetroBootstrap.install(RetroVersion.minecraft(), this);
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override public String getCommandName() { return "e4steam"; }

            @Override public String getCommandUsage(ICommandSender sender) {
                return "/e4steam <invite|stop>";
            }

            @Override public void processCommand(ICommandSender sender, String[] arguments) {
                if (arguments.length == 1
                        && RetroBootstrap.handleClientCommand("/e4steam " + arguments[0])) {
                    return;
                }
                E4steamForgeClient.this.showMessage(getCommandUsage(sender));
            }

            @Override public int getRequiredPermissionLevel() { return 0; }
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
            minecraft.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(message));
        }
    }

    @Override public void showSharingReady(String endpoint) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.ingameGUI == null) return;

        ChatComponentText message = new ChatComponentText(
                StatCollector.translateToLocal("text.e4steam_minecraft.domainAssigned") + " ");
        ChatComponentText address = new ChatComponentText(endpoint);
        address.getChatStyle()
                .setColor(EnumChatFormatting.GREEN)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, endpoint))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText(StatCollector.translateToLocal(
                                "text.e4steam_minecraft.addressCopyHelp"))));
        message.appendSibling(address);

        ChatComponentText invite = new ChatComponentText(" [" +
                StatCollector.translateToLocal("text.e4steam_minecraft.inviteFriends") + "]");
        invite.getChatStyle()
                .setColor(EnumChatFormatting.BLUE)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/e4steam invite"))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText(StatCollector.translateToLocal(
                                "text.e4steam_minecraft.inviteFriendsHelp"))));
        message.appendSibling(invite);

        ChatComponentText stop = new ChatComponentText(" [" +
                StatCollector.translateToLocal("text.e4steam_minecraft.clickToStop") + "]");
        stop.getChatStyle()
                .setColor(EnumChatFormatting.RED)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/e4steam stop"))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText(StatCollector.translateToLocal(
                                "text.e4steam_minecraft.stopSharingHelp"))));
        message.appendSibling(stop);
        minecraft.ingameGUI.getChatGUI().printChatMessage(message);
    }

    @SubscribeEvent public void initializeScreen(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.gui instanceof GuiShareToLan) {
            event.buttonList.add(new GuiButton(
                    ACCESS_MODE_BUTTON,
                    event.gui.width / 2 - 155,
                    event.gui.height - 52,
                    310,
                    20,
                    accessModeLabel(RetroBootstrap.selectedAccessMode())
            ));
            return;
        }

        if (event.gui instanceof GuiIngameMenu) {
            SteamSession session = link.e4steam.E4steamClient.session;
            if (session != null
                    && session.state() != SteamSession.State.STOPPED
                    && session.state() != SteamSession.State.STOPPING
                    && session.state() != SteamSession.State.UNHEALTHY) {
                event.buttonList.add(new GuiButton(
                        INVITE_BUTTON,
                        Math.max(4, event.gui.width - 154),
                        6,
                        150,
                        20,
                        StatCollector.translateToLocal("text.e4steam_minecraft.inviteFriends")
                ));
            }
        }
    }

    @SubscribeEvent public void buttonPressed(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.button.id == ACCESS_MODE_BUTTON && event.gui instanceof GuiShareToLan) {
            event.button.displayString = accessModeLabel(RetroBootstrap.cycleAccessMode());
        } else if (event.button.id == INVITE_BUTTON && event.gui instanceof GuiIngameMenu) {
            RetroBootstrap.handleClientCommand("/e4steam invite");
        }
    }

    private static String accessModeLabel(SteamAccessMode mode) {
        String value;
        switch (mode) {
            case LOCAL_ONLY:
                value = StatCollector.translateToLocal("text.e4steam_minecraft.access.local");
                break;
            case INVITE_ONLY:
                value = StatCollector.translateToLocal("text.e4steam_minecraft.access.invite");
                break;
            default:
                value = StatCollector.translateToLocal("text.e4steam_minecraft.access.friends");
                break;
        }
        return StatCollector.translateToLocal("text.e4steam_minecraft.accessMode") + ": " + value;
    }
}
