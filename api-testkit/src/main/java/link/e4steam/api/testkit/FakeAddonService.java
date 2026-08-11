package link.e4steam.api.testkit;

import link.e4steam.api.addon.AddonHandle;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.addon.AddonService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Deterministic read-only fake addon inventory. */
public final class FakeAddonService implements AddonService {
    private final List<AddonHandle> addons;
    private final boolean frozen;

    /** Creates a fake addon inventory. */
    public FakeAddonService(List<AddonHandle> addons, boolean frozen) {
        if (addons == null) throw new NullPointerException("addons");
        this.addons = Collections.unmodifiableList(new ArrayList<>(addons));
        this.frozen = frozen;
    }

    @Override
    public List<AddonHandle> addons() { return addons; }

    @Override
    public Optional<AddonHandle> find(AddonId addonId) {
        for (AddonHandle addon : addons) {
            if (addon.descriptor().id().equals(addonId)) return Optional.of(addon);
        }
        return Optional.empty();
    }

    @Override
    public boolean registrationsFrozen() { return frozen; }
}
