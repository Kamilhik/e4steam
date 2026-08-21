package link.e4steam.retro.forge;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import link.e4steam.retro.RetroBootstrap;
import link.e4steam.retro.RetroPlatform;
import link.e4steam.retro.RetroVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.multiplayer.GuiConnecting;

import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Loaded reflectively only on the physical client. */
public final class E4steamForge164Client implements RetroPlatform, ITickHandler {
    private final ConcurrentLinkedQueue<Runnable> clientTasks =
            new ConcurrentLinkedQueue<Runnable>();

    public E4steamForge164Client() {
        RetroBootstrap.install(RetroVersion.minecraft(), this);
        TickRegistry.registerTickHandler(this, Side.CLIENT);
    }

    @Override public void tickStart(EnumSet<TickType> type, Object... tickData) { }

    @Override public void tickEnd(EnumSet<TickType> type, Object... tickData) {
        Runnable task;
        while ((task = clientTasks.poll()) != null) task.run();
    }

    @Override public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }

    @Override public String getLabel() { return "e4steam-client"; }

    @Override public void execute(Runnable action) { clientTasks.add(action); }

    @Override public void connect(InetSocketAddress localAddress, String displayName) {
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.displayGuiScreen(new GuiConnecting(
                new GuiMultiplayer(new GuiMainMenu()), minecraft,
                localAddress.getAddress().getHostAddress(), localAddress.getPort()));
    }

    @Override public void showMessage(String message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.ingameGUI != null) {
            minecraft.ingameGUI.getChatGUI().printChatMessage(message);
        }
    }
}
