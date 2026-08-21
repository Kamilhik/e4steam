package link.e4steam.retro.forge;

import link.e4steam.retro.RetroBootstrap;
import link.e4steam.retro.RetroPlatform;
import link.e4steam.retro.RetroVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiConnecting;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Loaded reflectively only on the physical client. */
public final class E4steamForge113Client implements RetroPlatform {
    private final ConcurrentLinkedQueue<Runnable> clientTasks =
            new ConcurrentLinkedQueue<Runnable>();

    public E4steamForge113Client() {
        RetroBootstrap.install(RetroVersion.minecraft(), this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Runnable task;
        while ((task = clientTasks.poll()) != null) task.run();
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
}
