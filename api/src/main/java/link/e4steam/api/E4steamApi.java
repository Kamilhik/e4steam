package link.e4steam.api;

import link.e4steam.api.addon.AddonService;
import link.e4steam.api.capability.CapabilityService;
import link.e4steam.api.event.EventService;
import link.e4steam.api.runtime.RuntimeService;
import link.e4steam.api.scheduler.SchedulerService;

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

    /** Returns the bounded named-context scheduler. */
    SchedulerService scheduler();

    /** Returns optional future services without expanding this root interface. */
    ServiceRegistry services();
}
