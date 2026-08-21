package link.e4steam.api.addon;

import java.util.List;
import java.util.Optional;

/** Read-only lifecycle inventory; addon discovery remains the mod loader's job. */
public interface AddonService {
    /** Returns deterministic immutable addon handles. */
    List<AddonHandle> addons();

    /** Looks up a discovered addon by its unique id. */
    Optional<AddonHandle> find(AddonId addonId);

    /** Returns whether registration contracts have reached their freeze point. */
    boolean registrationsFrozen();
}
