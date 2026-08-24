package link.e4steam.api;

import link.e4steam.api.addon.AddonService;
import link.e4steam.api.capability.CapabilityService;
import link.e4steam.api.event.EventService;
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
import link.e4steam.api.runtime.RuntimeService;
import link.e4steam.api.scheduler.SchedulerService;
import link.e4steam.api.session.SessionService;
import link.e4steam.api.skin.SkinService;
import link.e4steam.api.storage.StorageService;
import link.e4steam.api.ui.UiService;
import link.e4steam.api.udp.UdpService;
import link.e4steam.api.world.WorldSettingsService;

/** Loader-independent, capability-scoped root API exposed to one trusted addon. */
public interface E4steamApi {
    /** Returns the public Java API version, independent from mod and wire versions. */
    ApiVersion apiVersion();

    /** Returns safe runtime snapshots and readiness. */
    RuntimeService runtime();

    /** Returns read-only addon lifecycle inventory. */
    AddonService addons();

    /** Returns capabilities scoped to the current addon. */
    CapabilityService capabilities();

    /** Returns the bounded typed observational event service. */
    EventService events();

    /** Returns privacy-scoped local and remote identity projections. */
    default IdentityService identities() { return ApiServiceKeys.require(services(), ApiServiceKeys.IDENTITIES); }

    /** Returns generation-safe session observation and controlled actions. */
    default SessionService sessions() { return ApiServiceKeys.require(services(), ApiServiceKeys.SESSIONS); }

    /** Returns headless dedicated-server status and controlled administration. */
    default DedicatedServerService dedicatedServers() { return ApiServiceKeys.require(services(), ApiServiceKeys.DEDICATED); }

    /** Returns access-mode registration and admission-policy services. */
    default AccessService access() { return ApiServiceKeys.require(services(), ApiServiceKeys.ACCESS); }

    /** Returns bounded Steam lobby operations. */
    default LobbyService lobbies() { return ApiServiceKeys.require(services(), ApiServiceKeys.LOBBIES); }

    /** Returns negotiated namespaced addon channels. */
    default NetworkService network() { return ApiServiceKeys.require(services(), ApiServiceKeys.NETWORK); }

    /** Returns session-scoped virtual datagram endpoints. */
    default UdpService udp() { return ApiServiceKeys.require(services(), ApiServiceKeys.UDP); }

    /** Returns declarative UI contributions or headless unavailability. */
    default UiService ui() { return ApiServiceKeys.require(services(), ApiServiceKeys.UI); }

    /** Returns loader-neutral command registration. */
    default CommandService commands() { return ApiServiceKeys.require(services(), ApiServiceKeys.COMMANDS); }

    /** Returns typed namespaced addon configuration. */
    default ConfigService config() { return ApiServiceKeys.require(services(), ApiServiceKeys.CONFIG); }

    /** Returns pathless quota-bounded private storage. */
    default StorageService storage() { return ApiServiceKeys.require(services(), ApiServiceKeys.STORAGE); }

    /** Returns allowlisted world-setting proposal contracts. */
    default WorldSettingsService worldSettings() { return ApiServiceKeys.require(services(), ApiServiceKeys.WORLD_SETTINGS); }

    /** Returns contracts for optional external modpack providers. */
    default ModpackService modpacks() { return ApiServiceKeys.require(services(), ApiServiceKeys.MODPACKS); }

    /** Returns contracts for optional external skin providers. */
    default SkinService skins() { return ApiServiceKeys.require(services(), ApiServiceKeys.SKINS); }

    /** Returns privacy-safe diagnostics contributions and previews. */
    default DiagnosticsService diagnostics() { return ApiServiceKeys.require(services(), ApiServiceKeys.DIAGNOSTICS); }

    /** Returns namespaced localized messages and safe fallbacks. */
    default LocalizationService localization() { return ApiServiceKeys.require(services(), ApiServiceKeys.LOCALIZATION); }

    /** Returns a structured bounded logger that rejects credential-like fields. */
    default SafeLogger logger() { return ApiServiceKeys.require(services(), ApiServiceKeys.LOGGER); }

    /** Returns the bounded named-context scheduler. */
    SchedulerService scheduler();

    /** Returns optional future services without expanding this root interface. */
    ServiceRegistry services();
}
