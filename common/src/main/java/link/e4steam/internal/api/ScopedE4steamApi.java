package link.e4steam.internal.api;

import link.e4steam.api.ApiConstants;
import link.e4steam.api.ApiServiceKeys;
import link.e4steam.api.ApiVersion;
import link.e4steam.api.E4steamApi;
import link.e4steam.api.ResourceScope;
import link.e4steam.api.ServiceRegistry;
import link.e4steam.api.addon.AddonDescriptor;
import link.e4steam.api.addon.AddonService;
import link.e4steam.api.capability.CapabilityId;
import link.e4steam.api.capability.CapabilityService;
import link.e4steam.api.event.EventService;
import link.e4steam.api.runtime.RuntimeService;
import link.e4steam.api.scheduler.SchedulerService;

import java.util.Set;

final class ScopedE4steamApi implements E4steamApi {
    private final CoreRuntimeService runtime;
    private final AddonService addons;
    private final CoreCapabilityService capabilities;
    private final EventService events;
    private final SchedulerService scheduler;
    private final CoreServiceRegistry services;

    ScopedE4steamApi(AddonDescriptor descriptor, Set<CapabilityId> granted, ResourceScope resources,
                     CoreApiPlatform platform) {
        this.runtime = platform.runtime();
        this.addons = platform.addons();
        this.capabilities = new CoreCapabilityService(descriptor.requestedCapabilities(), granted);
        this.events = platform.events().scoped(resources);
        this.scheduler = new ScopedSchedulerService(platform.scheduler(), resources);
        CoreContributionRegistry contributions = platform.contributions();
        services = new CoreServiceRegistry()
                .add(ApiServiceKeys.IDENTITIES, CoreStateServices.identities(capabilities, platform.sessions()))
                .add(ApiServiceKeys.SESSIONS, CoreStateServices.sessions(capabilities, platform.sessions()))
                .add(ApiServiceKeys.DEDICATED, CoreStateServices.dedicated(capabilities))
                .add(ApiServiceKeys.ACCESS, new CoreAccessService(descriptor.id(), capabilities,
                        contributions, resources, platform.scheduler()))
                .add(ApiServiceKeys.LOBBIES, CoreStateServices.lobbies(capabilities))
                .add(ApiServiceKeys.NETWORK, new CoreNetworkService(descriptor.id(), capabilities,
                        contributions, resources, platform.network()))
                .add(ApiServiceKeys.UDP, new CoreUdpService(descriptor.id(), capabilities,
                        contributions, resources))
                .add(ApiServiceKeys.UI, new CoreUiService(descriptor.id(), capabilities,
                        contributions, resources, platform.environment().mode()))
                .add(ApiServiceKeys.COMMANDS, new CoreCommandService(descriptor.id(), capabilities,
                        contributions, resources))
                .add(ApiServiceKeys.CONFIG, new CoreConfigService(descriptor.id(), capabilities))
                .add(ApiServiceKeys.STORAGE, new CoreStorageService(descriptor.id(), capabilities))
                .add(ApiServiceKeys.WORLD_SETTINGS, CoreStateServices.world(capabilities))
                .add(ApiServiceKeys.MODPACKS, CoreProviderServices.modpacks(descriptor.id(), capabilities,
                        contributions, resources, platform.scheduler()))
                .add(ApiServiceKeys.SKINS, CoreProviderServices.skins(descriptor.id(), capabilities,
                        contributions, resources, platform.scheduler()))
                .add(ApiServiceKeys.DIAGNOSTICS, CoreProviderServices.diagnostics(descriptor.id(), capabilities,
                        contributions, resources, platform.scheduler()))
                .add(ApiServiceKeys.LOCALIZATION, CoreStateServices.localization())
                .add(ApiServiceKeys.LOGGER, new CoreSafeLogger(descriptor.id()));
    }

    @Override public ApiVersion apiVersion() { return ApiConstants.API_VERSION; }
    @Override public RuntimeService runtime() { return runtime; }
    @Override public AddonService addons() { return addons; }
    @Override public CapabilityService capabilities() { return capabilities; }
    @Override public EventService events() { return events; }
    @Override public SchedulerService scheduler() { return scheduler; }
    @Override public ServiceRegistry services() { return services; }
}
