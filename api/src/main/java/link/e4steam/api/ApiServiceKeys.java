package link.e4steam.api;

import link.e4steam.api.access.AccessService;
import link.e4steam.api.command.CommandService;
import link.e4steam.api.config.ConfigService;
import link.e4steam.api.dedicated.DedicatedServerService;
import link.e4steam.api.diagnostics.DiagnosticsService;
import link.e4steam.api.identity.IdentityService;
import link.e4steam.api.lobby.LobbyService;
import link.e4steam.api.localization.LocalizationService;
import link.e4steam.api.logging.SafeLogger;
import link.e4steam.api.modpack.ModpackService;
import link.e4steam.api.network.NetworkService;
import link.e4steam.api.session.SessionService;
import link.e4steam.api.skin.SkinService;
import link.e4steam.api.storage.StorageService;
import link.e4steam.api.ui.UiService;
import link.e4steam.api.udp.UdpService;
import link.e4steam.api.world.WorldSettingsService;

/** Stable typed keys for services introduced by the 1.0 API. */
public final class ApiServiceKeys {
    /** Identity service key. */ public static final ServiceKey<IdentityService> IDENTITIES = key("identities", IdentityService.class);
    /** Session service key. */ public static final ServiceKey<SessionService> SESSIONS = key("sessions", SessionService.class);
    /** Dedicated service key. */ public static final ServiceKey<DedicatedServerService> DEDICATED = key("dedicated", DedicatedServerService.class);
    /** Access service key. */ public static final ServiceKey<AccessService> ACCESS = key("access", AccessService.class);
    /** Lobby service key. */ public static final ServiceKey<LobbyService> LOBBIES = key("lobbies", LobbyService.class);
    /** Network service key. */ public static final ServiceKey<NetworkService> NETWORK = key("network", NetworkService.class);
    /** UDP service key. */ public static final ServiceKey<UdpService> UDP = key("udp", UdpService.class);
    /** UI service key. */ public static final ServiceKey<UiService> UI = key("ui", UiService.class);
    /** Command service key. */ public static final ServiceKey<CommandService> COMMANDS = key("commands", CommandService.class);
    /** Config service key. */ public static final ServiceKey<ConfigService> CONFIG = key("config", ConfigService.class);
    /** Storage service key. */ public static final ServiceKey<StorageService> STORAGE = key("storage", StorageService.class);
    /** World settings service key. */ public static final ServiceKey<WorldSettingsService> WORLD_SETTINGS = key("world_settings", WorldSettingsService.class);
    /** Modpack contract service key. */ public static final ServiceKey<ModpackService> MODPACKS = key("modpacks", ModpackService.class);
    /** Skin contract service key. */ public static final ServiceKey<SkinService> SKINS = key("skins", SkinService.class);
    /** Diagnostics service key. */ public static final ServiceKey<DiagnosticsService> DIAGNOSTICS = key("diagnostics", DiagnosticsService.class);
    /** Localization service key. */ public static final ServiceKey<LocalizationService> LOCALIZATION = key("localization", LocalizationService.class);
    /** Structured safe logger key. */ public static final ServiceKey<SafeLogger> LOGGER = key("logger", SafeLogger.class);

    private ApiServiceKeys() { }

    /** Returns the visible service or throws a sanitized programming error for an incomplete host. */
    public static <T> T require(ServiceRegistry registry, ServiceKey<T> key) {
        if (registry == null || key == null) throw new NullPointerException("service");
        return registry.find(key).orElseThrow(() -> new IllegalStateException("API service unavailable: " + key.id()));
    }

    private static <T> ServiceKey<T> key(String path, Class<T> type) {
        return new ServiceKey<>("e4steam:" + path, type);
    }
}
