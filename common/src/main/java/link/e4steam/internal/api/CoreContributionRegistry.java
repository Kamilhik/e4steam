package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Registration;
import link.e4steam.api.ResourceScope;
import link.e4steam.api.addon.AddonId;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Namespaced bounded ownership registry shared by loader adapters. */
final class CoreContributionRegistry {
    private final Object lock = new Object();
    private final Map<String, LinkedHashMap<String, Entry>> families = new HashMap<>();
    private volatile BooleanSupplier frozen = () -> false;

    void frozenSupplier(BooleanSupplier supplier) { frozen = supplier; }
    boolean isFrozen() { return frozen.getAsBoolean(); }

    ApiResult<Registration> register(String family, String id, AddonId owner,
                                     Object value, ResourceScope resources, boolean freezeSensitive) {
        if (family == null || id == null || owner == null || value == null || resources == null) {
            return SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT, "contribution.register", "Validation");
        }
        if (freezeSensitive && frozen.getAsBoolean()) {
            return SafeApiErrors.failure(ApiErrorCode.UNAVAILABLE, "contribution.register", "RegistrationsFrozen");
        }
        String expected = owner.value().replace(':', '-').toLowerCase(java.util.Locale.ROOT) + ':';
        if (!id.toLowerCase(java.util.Locale.ROOT).startsWith(expected)) {
            return SafeApiErrors.failure(ApiErrorCode.SECURITY_REJECTION,
                    "contribution.namespace", "WrongOwnerNamespace");
        }
        CoreRegistration registration;
        synchronized (lock) {
            LinkedHashMap<String, Entry> entries = families.computeIfAbsent(family,
                    ignored -> new LinkedHashMap<>());
            if (entries.size() >= ApiLimits.MAX_REGISTRATIONS_PER_FAMILY) {
                return SafeApiErrors.failure(ApiErrorCode.QUEUE_FULL,
                        "contribution.register", "RegistrationLimit");
            }
            if (entries.containsKey(id)) {
                return SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT,
                        "contribution.register", "DuplicateId");
            }
            registration = new CoreRegistration(() -> remove(family, id, owner));
            entries.put(id, new Entry(owner, value, registration));
        }
        resources.own(registration);
        return ApiResult.success(registration);
    }

    Object find(String family, String id) {
        synchronized (lock) {
            Map<String, Entry> entries = families.get(family);
            Entry entry = entries == null ? null : entries.get(id);
            return entry == null || entry.registration.isClosed() ? null : entry.value;
        }
    }

    int size(String family) {
        synchronized (lock) {
            Map<String, Entry> entries = families.get(family);
            return entries == null ? 0 : entries.size();
        }
    }

    private void remove(String family, String id, AddonId owner) {
        synchronized (lock) {
            Map<String, Entry> entries = families.get(family);
            Entry entry = entries == null ? null : entries.get(id);
            if (entry != null && entry.owner.equals(owner)) entries.remove(id);
        }
    }

    private static final class Entry {
        private final AddonId owner;
        private final Object value;
        private final CoreRegistration registration;
        private Entry(AddonId owner, Object value, CoreRegistration registration) {
            this.owner = owner; this.value = value; this.registration = registration;
        }
    }
}
