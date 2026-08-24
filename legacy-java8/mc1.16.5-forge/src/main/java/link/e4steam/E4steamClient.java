package link.e4steam;

import link.e4steam.steam.SteamAccessMode;
import link.e4steam.steam.SteamAddress;
import link.e4steam.steam.SteamClientBridge;
import link.e4steam.steam.SteamRuntime;
import link.e4steam.steam.SteamSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConnectingScreen;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.MultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mod(E4steamClient.MOD_ID)
public final class E4steamClient {
    public static final String MOD_ID = "e4steam";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static volatile SteamSession session;
    private static volatile int hostedPort;

    public E4steamClient() {
        SteamRuntime.preloadCompatibilityClasses();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() == null || !minecraft.getSingleplayerServer().isPublished()) {
            stopSharing();
            return;
        }
        int port = minecraft.getSingleplayerServer().getPort();
        if (port > 0 && (session == null || hostedPort != port)) {
            stopSharing();
            hostedPort = port;
            SteamSession created = new SteamSession(port, SteamAccessMode.FRIENDS_ONLY);
            session = created;
            created.startAsync();
        }
    }

    @SubscribeEvent
    public void clientCommand(ClientChatEvent event) {
        if (!"/e4steam invite".equalsIgnoreCase(event.getMessage().trim())) return;
        event.setCanceled(true);
        openInvite();
    }

    private static void stopSharing() {
        SteamSession current = session;
        if (current != null) {
            session = null;
            hostedPort = 0;
            current.stop();
        }
    }

    public static void sessionReady(final SteamSession ready) {
        Minecraft.getInstance().execute(new Runnable() {
            @Override public void run() {
                if (session != ready || ready.address() == null) return;
                chat("e4steam: " + ready.address().inviteString());
                Minecraft.getInstance().gui.getChat().addMessage(
                        new StringTextComponent("[Invite friends]")
                                .withStyle(TextFormatting.AQUA)
                                .withStyle(style -> style.withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND, "/e4steam invite"
                                )))
                );
            }
        });
    }

    private static void openInvite() {
        SteamSession current = session;
        if (current == null || current.state() != SteamSession.State.STARTED) {
            chat("e4steam: Steam lobby is not ready");
            return;
        }
        current.openInviteOverlayAsync();
    }

    public static void sessionFailed(final Throwable throwable) {
        LOGGER.error("Could not start e4steam", throwable);
        Minecraft.getInstance().execute(new Runnable() {
            @Override public void run() { chat("e4steam: " + throwable.getMessage()); }
        });
    }

    public static void acceptSteamInvite(final String endpoint, final String hostName) {
        final Optional<SteamAddress> parsed = SteamAddress.tryParse(endpoint);
        if (!parsed.isPresent()) {
            showSteamJoinFailure("Invalid Steam address");
            return;
        }
        CompletableFuture<Boolean> claim = SteamRuntime.get().beginGuestConnect(endpoint);
        claim.whenComplete((accepted, failure) -> {
            if (failure != null || !Boolean.TRUE.equals(accepted)) {
                showSteamJoinFailure(failure == null ? "Steam invitation rejected" : failure.getMessage());
                return;
            }
            try {
                final InetSocketAddress local = SteamClientBridge.open(parsed.get());
                Minecraft.getInstance().execute(new Runnable() {
                    @Override public void run() {
                        Minecraft minecraft = Minecraft.getInstance();
                        String address = local.getAddress().getHostAddress() + ":" + local.getPort();
                        ServerData data = new ServerData(hostName, address, false);
                        minecraft.setScreen(new ConnectingScreen(
                                new MultiplayerScreen(new MainMenuScreen()), minecraft, data
                        ));
                    }
                });
            } catch (Throwable throwable) {
                showSteamJoinFailure(throwable.getMessage());
            }
        });
    }

    public static void showSteamJoinFailure(Object detail) {
        final String message = String.valueOf(detail);
        LOGGER.warn("Steam join failed: {}", message);
        Minecraft.getInstance().execute(new Runnable() {
            @Override public void run() { chat("e4steam: " + message); }
        });
    }

    private static void chat(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) minecraft.gui.getChat().addMessage(new StringTextComponent(message));
    }
}
