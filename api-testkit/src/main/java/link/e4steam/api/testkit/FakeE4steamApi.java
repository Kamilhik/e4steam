package link.e4steam.api.testkit;

import link.e4steam.api.ApiConstants;
import link.e4steam.api.ApiVersion;
import link.e4steam.api.E4steamApi;
import link.e4steam.api.ServiceRegistry;
import link.e4steam.api.addon.AddonService;
import link.e4steam.api.capability.CapabilityService;
import link.e4steam.api.event.EventService;
import link.e4steam.api.runtime.RuntimeService;
import link.e4steam.api.scheduler.SchedulerService;

/** Complete fake root API for compile and lifecycle tests of third-party addons. */
public final class FakeE4steamApi implements E4steamApi {
    private final RuntimeService runtime;
    private final AddonService addons;
    private final CapabilityService capabilities;
    private final EventService events;
    private final SchedulerService scheduler;
    private final ServiceRegistry services;

    /** Creates a fake API from explicit bounded service implementations. */
    public FakeE4steamApi(
            RuntimeService runtime,
            AddonService addons,
            CapabilityService capabilities,
            EventService events,
            SchedulerService scheduler,
            ServiceRegistry services
    ) {
        if (runtime == null || addons == null || capabilities == null
                || events == null || scheduler == null || services == null) {
            throw new NullPointerException("services");
        }
        this.runtime = runtime;
        this.addons = addons;
        this.capabilities = capabilities;
        this.events = events;
        this.scheduler = scheduler;
        this.services = services;
    }

    @Override
    public ApiVersion apiVersion() { return ApiConstants.API_VERSION; }

    @Override
    public RuntimeService runtime() { return runtime; }

    @Override
    public AddonService addons() { return addons; }

    @Override
    public CapabilityService capabilities() { return capabilities; }

    @Override
    public EventService events() { return events; }

    @Override
    public SchedulerService scheduler() { return scheduler; }

    @Override
    public ServiceRegistry services() { return services; }
}
