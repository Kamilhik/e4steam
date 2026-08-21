package link.e4steam.api.addon;

import link.e4steam.api.E4steamApi;
import link.e4steam.api.ResourceScope;

/** Scoped initialization context whose services are filtered for one addon. */
public interface AddonContext {
    /** Returns immutable metadata for the initializing addon. */
    AddonDescriptor descriptor();

    /** Returns the capability-filtered API view. */
    E4steamApi api();

    /** Returns the parent scope that owns all addon registrations. */
    ResourceScope resources();
}
