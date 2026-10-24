package link.e4steam.lan;

import link.e4steam.Config;
import link.e4steam.E4steamClient;
import link.e4steam.steam.SteamAccessMode;
import link.e4steam.steam.SteamSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

/** Modern config adapter. The TOML config is the single source of saved preferences. */
public final class ModernLanSettings {
    private ModernLanSettings() { }

    public static void initialize() {
        Config config = Config.INSTANCE;
        try {
            LanSettings.install(new LanSettings.Snapshot(config.lanOnlineMode.value(),
                    config.lanPvp.value(), config.lanMaxPlayers.value(), config.lanPort.value(),
                    config.lanGameMode.value(), config.lanAllowCheats.value()));
        } catch (IllegalArgumentException invalid) {
            E4steamClient.LOGGER.warn("Invalid e4steam LAN preferences; using safe defaults", invalid);
            LanSettings.install(LanSettings.defaults());
        }
    }

    public static void save(LanSettings.Snapshot settings) {
        settings.validated();
        Config config = Config.INSTANCE;
        config.lanOnlineMode.setValue(settings.onlineMode());
        config.lanPvp.setValue(settings.pvp());
        config.lanMaxPlayers.setValue(settings.maxPlayers());
        config.lanPort.setValue(settings.port());
        config.lanGameMode.setValue(settings.gameMode());
        config.lanAllowCheats.setValue(settings.allowCheats());
        LanSettings.install(settings);
    }

    public static void refreshSteamSession(IntegratedServer server) {
        SteamSession previous = E4steamClient.session;
        SteamAccessMode mode = Config.INSTANCE.hostEnabled.value()
                ? E4steamClient.selectedAccessMode : SteamAccessMode.LOCAL_ONLY;
        if (previous != null && previous.accessMode() == mode
                && java.util.Objects.equals(previous.customAccessModeId(), E4steamClient.selectedCustomAccessMode)) return;
        if (previous != null) previous.stop();
        E4steamClient.session = null;
        if (mode != SteamAccessMode.LOCAL_ONLY) {
            SteamSession session = new SteamSession(LanServerSettingsApplier.port(server), mode,
                    E4steamClient.selectedCustomAccessMode);
            E4steamClient.session = session;
            session.startAsync();
        }
    }
}
