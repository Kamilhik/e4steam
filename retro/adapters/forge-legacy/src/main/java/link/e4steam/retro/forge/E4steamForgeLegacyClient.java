package link.e4steam.retro.forge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import link.e4steam.retro.RetroBootstrap;
import link.e4steam.retro.RetroPlatform;
import link.e4steam.retro.RetroVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.ChatComponentText;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Loaded reflectively only on the physical client. */
public final class E4steamForgeLegacyClient implements RetroPlatform {
    private final ConcurrentLinkedQueue<Runnable> clientTasks =
            new ConcurrentLinkedQueue<Runnable>();

    public E4steamForgeLegacyClient() {
        RetroBootstrap.install(RetroVersion.minecraft(), this);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(this);
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
                new ServerData(displayName, endpoint)));
    }

    @Override public void showMessage(String message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.ingameGUI != null) {
            minecraft.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(message));
        }
    }
}
