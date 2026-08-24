package link.e4steam.api.addon;

import java.util.Optional;

/** Read-only snapshot handle for one addon lifecycle. */
public interface AddonHandle {
    /** Returns immutable addon metadata. */
    AddonDescriptor descriptor();

    /** Returns the current observable state. */
    AddonState state();

    /** Returns a sanitized failure only when the addon failed. */
    Optional<AddonFailure> failure();
}
