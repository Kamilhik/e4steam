package link.e4steam.internal.addon;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiVersion;
import link.e4steam.api.E4steamApi;
import link.e4steam.api.Retryability;
import link.e4steam.api.addon.AddonContext;
import link.e4steam.api.addon.AddonDependency;
import link.e4steam.api.addon.AddonDescriptor;
import link.e4steam.api.addon.AddonFailure;
import link.e4steam.api.addon.AddonHandle;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.addon.AddonService;
import link.e4steam.api.addon.AddonState;
import link.e4steam.api.capability.CapabilityId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Loader-independent validation, deterministic lifecycle and ownership for discovered addons. */
public final class AddonManager implements AddonService, AutoCloseable {
    private final Object lock = new Object();
    private final ApiVersion apiVersion;
    private final CapabilityGrantPolicy capabilityPolicy;
    private final AddonApiFactory apiFactory;
    private final ExecutorService lifecycleExecutor;
    private final long callbackTimeoutMillis;
    private final Consumer<AddonHandle> lifecycleListener;
    private final LinkedHashMap<AddonId, Slot> slots = new LinkedHashMap<>();
    private final List<Slot> activationOrder = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean frozen;
    private boolean discovered;

    public AddonManager(ApiVersion apiVersion,
                        CapabilityGrantPolicy capabilityPolicy,
                        AddonApiFactory apiFactory,
                        ExecutorService lifecycleExecutor,
                        long callbackTimeoutMillis) {
        this(apiVersion, capabilityPolicy, apiFactory, lifecycleExecutor,
                callbackTimeoutMillis, handle -> { });
    }

    public AddonManager(ApiVersion apiVersion,
                        CapabilityGrantPolicy capabilityPolicy,
                        AddonApiFactory apiFactory,
                        ExecutorService lifecycleExecutor,
                        long callbackTimeoutMillis,
                        Consumer<AddonHandle> lifecycleListener) {
        this.apiVersion = java.util.Objects.requireNonNull(apiVersion, "apiVersion");
        this.capabilityPolicy = java.util.Objects.requireNonNull(capabilityPolicy, "capabilityPolicy");
        this.apiFactory = java.util.Objects.requireNonNull(apiFactory, "apiFactory");
        this.lifecycleExecutor = java.util.Objects.requireNonNull(lifecycleExecutor, "lifecycleExecutor");
        this.lifecycleListener = java.util.Objects.requireNonNull(lifecycleListener, "lifecycleListener");
        if (callbackTimeoutMillis < 1L || callbackTimeoutMillis > ApiLimits.MAX_LIFECYCLE_CALLBACK_MILLIS) {
            throw new IllegalArgumentException("Invalid lifecycle callback timeout");
        }
        this.callbackTimeoutMillis = callbackTimeoutMillis;
    }

    /** Validates candidates and invokes initialization callbacks in deterministic dependency order. */
    public void initialize(List<AddonCandidate> candidates) {
        if (candidates == null) throw new NullPointerException("candidates");
        synchronized (lock) {
            if (discovered) throw new IllegalStateException("Addon discovery already completed");
            if (closed.get()) throw new IllegalStateException("Addon manager is closed");
            discovered = true;
        }

        for (AddonCandidate candidate : candidates) {
            if (candidate == null) throw new NullPointerException("candidate");
        }
        List<AddonCandidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing(candidate -> candidate.descriptor().id()));
        Map<AddonId, List<AddonCandidate>> grouped = new LinkedHashMap<>();
        for (AddonCandidate candidate : sorted) {
            grouped.computeIfAbsent(candidate.descriptor().id(), ignored -> new ArrayList<>()).add(candidate);
        }

        for (Map.Entry<AddonId, List<AddonCandidate>> entry : grouped.entrySet()) {
            AddonCandidate first = entry.getValue().get(0);
            Slot slot = new Slot(first);
            slots.put(entry.getKey(), slot);
            if (entry.getValue().size() > 1) {
                slot.fail(error(ApiErrorCode.INVALID_ARGUMENT, "duplicate-id", "addon.discovery", "DuplicateAddonId"));
            } else if (!first.enabled()) {
                slot.state = AddonState.DISABLED;
            }
        }

        validateMetadataAndCapabilities();
        List<Slot> order = dependencyOrder();
        for (Slot slot : order) initializeOne(slot);
        frozen = true;
        for (Slot slot : slots.values()) notifyLifecycle(slot);
    }

    private void validateMetadataAndCapabilities() {
        for (Slot slot : slots.values()) {
            if (slot.terminal()) continue;
            slot.state = AddonState.VALIDATING;
            AddonDescriptor descriptor = slot.descriptor();
            if (!descriptor.apiRange().contains(apiVersion)) {
                slot.fail(error(ApiErrorCode.INCOMPATIBLE_VERSION, "api-range", "addon.validation", "ApiVersionMismatch"));
                continue;
            }
            LinkedHashSet<CapabilityId> granted = new LinkedHashSet<>();
            for (CapabilityId requested : descriptor.requestedCapabilities()) {
                boolean known = capabilityPolicy.knownCapabilities().contains(requested);
                boolean allowed = known && capabilityPolicy.isAllowed(descriptor, requested);
                if (allowed) granted.add(requested);
                else if (descriptor.requiredCapabilities().contains(requested)) {
                    slot.fail(error(ApiErrorCode.CAPABILITY_DENIED,
                            known ? "required-capability-denied" : "required-capability-unknown",
                            "addon.capabilities", known ? "PolicyDenied" : "UnknownCapability"));
                    break;
                }
            }
            slot.granted = Collections.unmodifiableSet(granted);
        }

        for (Slot slot : slots.values()) {
            if (slot.terminal()) continue;
            for (AddonDependency dependency : slot.descriptor().dependencies()) {
                Slot target = slots.get(dependency.addonId());
                if (target == null) {
                    if (dependency.required()) {
                        slot.fail(error(ApiErrorCode.UNAVAILABLE, "required-dependency-missing",
                                "addon.dependencies", "MissingDependency"));
                    }
                    continue;
                }
                if (!dependency.supportedVersions().contains(target.descriptor().version()) && dependency.required()) {
                    slot.fail(error(ApiErrorCode.INCOMPATIBLE_VERSION, "dependency-version",
                            "addon.dependencies", "DependencyVersionMismatch"));
                }
            }
        }
    }

    private List<Slot> dependencyOrder() {
        Map<Slot, Integer> indegree = new LinkedHashMap<>();
        Map<Slot, List<Slot>> outgoing = new HashMap<>();
        for (Slot slot : slots.values()) if (!slot.terminal()) indegree.put(slot, 0);
        for (Slot slot : indegree.keySet()) {
            for (AddonDependency dependency : slot.descriptor().dependencies()) {
                Slot target = slots.get(dependency.addonId());
                if (target == null || target.terminal()
                        || !dependency.supportedVersions().contains(target.descriptor().version())) continue;
                outgoing.computeIfAbsent(target, ignored -> new ArrayList<>()).add(slot);
                indegree.put(slot, indegree.get(slot) + 1);
            }
        }

        ArrayDeque<Slot> ready = new ArrayDeque<>();
        indegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(slot -> slot.descriptor().id()))
                .forEach(ready::addLast);
        List<Slot> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            Slot current = ready.removeFirst();
            order.add(current);
            List<Slot> dependants = outgoing.getOrDefault(current, Collections.emptyList());
            dependants.sort(Comparator.comparing(slot -> slot.descriptor().id()));
            for (Slot dependant : dependants) {
                int remaining = indegree.get(dependant) - 1;
                indegree.put(dependant, remaining);
                if (remaining == 0) ready.addLast(dependant);
            }
        }
        if (order.size() != indegree.size()) {
            Set<Slot> ordered = new HashSet<>(order);
            for (Slot slot : indegree.keySet()) {
                if (!ordered.contains(slot)) {
                    slot.fail(error(ApiErrorCode.INVALID_ARGUMENT, "dependency-cycle",
                            "addon.dependencies", "DependencyCycle"));
                }
            }
        }
        return order;
    }

    private void initializeOne(Slot slot) {
        if (slot.terminal()) return;
        for (AddonDependency dependency : slot.descriptor().dependencies()) {
            if (!dependency.required()) continue;
            Slot target = slots.get(dependency.addonId());
            if (target == null || target.state != AddonState.ACTIVE) {
                slot.fail(error(ApiErrorCode.UNAVAILABLE, "required-dependency-inactive",
                        "addon.initialize", "DependencyInactive"));
                return;
            }
        }

        slot.state = AddonState.INITIALIZING;
        E4steamApi api;
        try {
            api = apiFactory.create(slot.descriptor(), slot.granted, slot.resources);
        } catch (RuntimeException failure) {
            slot.fail(error(ApiErrorCode.ADDON_FAILURE, "api-view-failed",
                    "addon.initialize", safeCategory(failure)));
            return;
        }
        AddonContext context = new Context(slot.descriptor(), api, slot.resources);
        Future<?> callback;
        try {
            callback = lifecycleExecutor.submit(() -> {
                try {
                    slot.candidate.addon().initialize(context);
                } catch (Exception failure) {
                    throw new AddonCallbackException(failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            slot.fail(error(ApiErrorCode.QUEUE_FULL, "initialize-rejected",
                    "addon.initialize", "LifecycleQueue"));
            return;
        }
        try {
            callback.get(callbackTimeoutMillis, TimeUnit.MILLISECONDS);
            slot.state = AddonState.ACTIVE;
            activationOrder.add(slot);
        } catch (TimeoutException failure) {
            callback.cancel(true);
            slot.fail(error(ApiErrorCode.TIMEOUT, "initialize-timeout",
                    "addon.initialize", "Timeout"));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            callback.cancel(true);
            slot.fail(error(ApiErrorCode.CANCELLED, "initialize-interrupted",
                    "addon.initialize", "Interrupted"));
        } catch (java.util.concurrent.ExecutionException failure) {
            Throwable cause = rootCause(failure);
            if (cause instanceof VirtualMachineError) throw (VirtualMachineError) cause;
            if (cause instanceof ThreadDeath) throw (ThreadDeath) cause;
            slot.fail(error(ApiErrorCode.ADDON_FAILURE, "initialize-failed",
                    "addon.initialize", safeCategory(cause)));
        }
    }

    @Override
    public List<AddonHandle> addons() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<AddonHandle>(slots.values()));
        }
    }

    @Override
    public Optional<AddonHandle> find(AddonId addonId) {
        if (addonId == null) throw new NullPointerException("addonId");
        synchronized (lock) { return Optional.ofNullable(slots.get(addonId)); }
    }

    @Override public boolean registrationsFrozen() { return frozen; }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        frozen = true;
        RuntimeException first = null;
        for (int index = activationOrder.size() - 1; index >= 0; index--) {
            Slot slot = activationOrder.get(index);
            try { slot.resources.close(); }
            catch (RuntimeException failure) {
                if (first == null) first = failure; else first.addSuppressed(failure);
            }
            slot.state = AddonState.STOPPED;
        }
        for (Slot slot : slots.values()) {
            if (slot.state != AddonState.STOPPED && !slot.resources.isClosed()) {
                try { slot.resources.close(); }
                catch (RuntimeException failure) {
                    if (first == null) first = failure; else first.addSuppressed(failure);
                }
                if (slot.state == AddonState.DISCOVERED || slot.state == AddonState.VALIDATING
                        || slot.state == AddonState.INITIALIZING) slot.state = AddonState.STOPPED;
            }
        }
        if (first != null) throw first;
    }

    private static ApiError error(ApiErrorCode code, String message, String operation, String category) {
        return new ApiError(code, "e4steam.api." + message, Retryability.PERMANENT,
                operation, "", category);
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private static String safeCategory(Throwable failure) {
        String simple = failure == null ? "AddonFailure" : failure.getClass().getSimpleName();
        return simple.matches("[A-Za-z0-9_$.-]{1,64}") ? simple : "AddonFailure";
    }

    private void notifyLifecycle(Slot slot) {
        try { lifecycleListener.accept(slot); }
        catch (RuntimeException ignored) {
            // Observational diagnostics cannot alter addon activation state.
        }
    }

    private static final class Context implements AddonContext {
        private final AddonDescriptor descriptor;
        private final E4steamApi api;
        private final ManagedResourceScope resources;
        private Context(AddonDescriptor descriptor, E4steamApi api, ManagedResourceScope resources) {
            this.descriptor = descriptor; this.api = api; this.resources = resources;
        }
        @Override public AddonDescriptor descriptor() { return descriptor; }
        @Override public E4steamApi api() { return api; }
        @Override public ManagedResourceScope resources() { return resources; }
    }

    private static final class Slot implements AddonHandle {
        private final AddonCandidate candidate;
        private final ManagedResourceScope resources = new ManagedResourceScope();
        private volatile AddonState state = AddonState.DISCOVERED;
        private volatile AddonFailure failure;
        private volatile Set<CapabilityId> granted = Collections.emptySet();
        private Slot(AddonCandidate candidate) { this.candidate = candidate; }
        @Override public AddonDescriptor descriptor() { return candidate.descriptor(); }
        @Override public AddonState state() { return state; }
        @Override public Optional<AddonFailure> failure() { return Optional.ofNullable(failure); }
        private boolean terminal() { return state == AddonState.DISABLED || state == AddonState.FAILED || state == AddonState.STOPPED; }
        private void fail(ApiError error) {
            state = AddonState.FAILED;
            failure = new AddonFailure(descriptor().id(), error);
            try { resources.close(); } catch (RuntimeException ignored) { }
        }
    }

    private static final class AddonCallbackException extends RuntimeException {
        private AddonCallbackException(Throwable cause) { super(cause); }
    }
}
