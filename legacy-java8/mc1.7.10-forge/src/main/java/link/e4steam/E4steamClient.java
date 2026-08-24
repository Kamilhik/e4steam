package link.e4steam;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import link.e4steam.steam.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mod(modid=E4steamClient.MOD_ID,name="e4steam",version="0.2.3")
public final class E4steamClient {
    public static final String MOD_ID="e4steam";
    public static final Logger LOGGER=LogManager.getLogger(MOD_ID);
    private static final ConcurrentLinkedQueue<Runnable> CLIENT_TASKS=new ConcurrentLinkedQueue<Runnable>();
    private static volatile SteamSession session;
    private static volatile int hostedPort;

    @Mod.EventHandler public void init(FMLInitializationEvent event){SteamRuntime.preloadCompatibilityClasses();MinecraftForge.EVENT_BUS.register(this);cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(this);ClientCommandHandler.instance.registerCommand(new InviteCommand());}
    @SubscribeEvent public void clientTick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Runnable task;while((task=CLIENT_TASKS.poll())!=null){try{task.run();}catch(Throwable error){LOGGER.error("Client task failed",error);}}
        int port=publishedPort(Minecraft.getMinecraft().getIntegratedServer());
        if(port<=0)stopSharing();else if(session==null||hostedPort!=port){stopSharing();hostedPort=port;SteamSession created=new SteamSession(port,SteamAccessMode.FRIENDS_ONLY);session=created;created.startAsync();}
    }
    private static int publishedPort(IntegratedServer server){return server!=null&&server.getPublic()?server.getServerPort():0;}
    private static void stopSharing(){SteamSession current=session;if(current!=null){session=null;hostedPort=0;current.stop();}}
    public static void sessionReady(final SteamSession ready){CLIENT_TASKS.add(new Runnable(){@Override public void run(){if(session!=ready||ready.address()==null)return;chat("e4steam: "+ready.address().inviteString());ChatComponentText invite=new ChatComponentText("[Invite friends]");invite.getChatStyle().setColor(EnumChatFormatting.AQUA).setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/e4steam invite"));Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(invite);}});}
    private static void openInvite(){SteamSession current=session;if(current==null||current.state()!=SteamSession.State.STARTED){chat("e4steam: Steam lobby is not ready");return;}current.openInviteOverlayAsync();}
    public static void sessionFailed(final Throwable throwable){LOGGER.error("Could not start e4steam",throwable);CLIENT_TASKS.add(new Runnable(){@Override public void run(){chat("e4steam: "+throwable.getMessage());}});}
    public static void acceptSteamInvite(final String endpoint,final String hostName){
        final Optional<SteamAddress> parsed=SteamAddress.tryParse(endpoint);if(!parsed.isPresent()){showSteamJoinFailure("Invalid Steam address");return;}
        CompletableFuture<Boolean> claim=SteamRuntime.get().beginGuestConnect(endpoint);
        claim.whenComplete((accepted,failure)->{if(failure!=null||!Boolean.TRUE.equals(accepted)){showSteamJoinFailure(failure==null?"Steam invitation rejected":failure.getMessage());return;}try{final InetSocketAddress local=SteamClientBridge.open(parsed.get());CLIENT_TASKS.add(new Runnable(){@Override public void run(){Minecraft minecraft=Minecraft.getMinecraft();String address=local.getAddress().getHostAddress()+":"+local.getPort();minecraft.displayGuiScreen(new GuiConnecting(new GuiMultiplayer(new GuiMainMenu()),minecraft,new ServerData(hostName,address,false)));}});}catch(Throwable throwable){showSteamJoinFailure(throwable.getMessage());}});
    }
    public static void showSteamJoinFailure(Object detail){final String message=String.valueOf(detail);LOGGER.warn("Steam join failed: {}",message);CLIENT_TASKS.add(new Runnable(){@Override public void run(){chat("e4steam: "+message);}});}
    private static void chat(String message){Minecraft minecraft=Minecraft.getMinecraft();if(minecraft.ingameGUI!=null)minecraft.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(message));}
    private static final class InviteCommand extends CommandBase {
        @Override public String getCommandName(){return "e4steam";}
        @Override public String getCommandUsage(ICommandSender sender){return "/e4steam invite";}
        @Override public void processCommand(ICommandSender sender,String[] args){if(args.length==1&&"invite".equalsIgnoreCase(args[0]))openInvite();}
        @Override public int getRequiredPermissionLevel(){return 0;}
    }
}
