package link.e4steam.internal.api;

import link.e4steam.api.ApiConstants;
import link.e4steam.api.addon.AddonService;
import link.e4steam.api.event.RuntimeReadyEvent;
import link.e4steam.api.event.RuntimeStoppingEvent;
import link.e4steam.api.event.LifecycleEvent;
import link.e4steam.api.addon.AddonHandle;
import link.e4steam.api.addon.AddonState;
import link.e4steam.api.runtime.LifecyclePhase;
import link.e4steam.api.runtime.SteamRuntimeState;
import link.e4steam.api.ApiResult;
import link.e4steam.api.access.AccessService;
import link.e4steam.api.identity.IdentityService;
import link.e4steam.api.session.SessionService;
import link.e4steam.internal.addon.AddonCandidate;
import link.e4steam.internal.addon.AddonManager;
import link.e4steam.internal.addon.BuiltinCapabilityPolicy;
import link.e4steam.steam.SteamAddonNetworkRuntime;
import link.e4steam.steam.SteamClientApiBridge;
import link.e4steam.steam.SteamCustomAccessGate;
import link.e4steam.steam.SteamPeerPrivacy;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Process singleton owning the stable addon API implementation and deterministic shutdown. */
public final class CoreApiPlatform implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("e4steam-addon-api");
    private static final Object INSTANCE_LOCK = new Object();
    private static volatile CoreApiPlatform instance;

    private final RuntimeEnvironment environment;
    private final CoreSchedulerService scheduler = new CoreSchedulerService();
    private final CoreEventBus events = new CoreEventBus(scheduler);
    private final CoreContributionRegistry contributions = new CoreContributionRegistry();
    private final CoreSessionRegistry sessions = new CoreSessionRegistry(events);
    private final AddonNetworkCoordinator network = new AddonNetworkCoordinator(scheduler, sessions);
    private final CoreRuntimeService runtime;
    private final ExecutorService lifecycleExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AutoCloseable addonNetworkHooks;
    private final AutoCloseable customAccessHooks;
    private volatile AddonManager addons;

    private CoreApiPlatform(RuntimeEnvironment environment) {
        this.environment = environment;
        this.runtime = new CoreRuntimeService(environment);
        this.addonNetworkHooks = SteamAddonNetworkRuntime.installHooks();
        this.customAccessHooks = SteamCustomAccessGate.install(new SteamCustomAccessGate.Provider() {
            @Override public boolean isRegistered(String modeId) {
                return hasCustomAccess(modeId);
            }

            @Override public CompletionStage<Boolean> evaluate(String modeId, long remoteSteamId) {
                return evaluateCustomAccess(modeId, remoteSteamId);
            }
        });
        this.lifecycleExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(4), daemonFactory("e4steam-addon-lifecycle"),
                new ThreadPoolExecutor.AbortPolicy());
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "e4steam-addon-shutdown"));
    }

    /** Starts once using entry points supplied by the normal loader. */
    public static CoreApiPlatform start(RuntimeEnvironment environment, List<AddonCandidate> candidates) {
        if (environment == null || candidates == null) throw new NullPointerException("addon platform");
        synchronized (INSTANCE_LOCK) {
            if (instance != null) return instance;
            CoreApiPlatform created = new CoreApiPlatform(environment);
            instance = created;
            created.initialize(candidates);
            return created;
        }
    }

    /** Returns the initialized platform, or null during very early bootstrap. */
    public static CoreApiPlatform current() { return instance; }

    /** Internal authenticated-transport bridge; no native handles or secrets cross it. */
    public AddonNetworkCoordinator addonNetwork() { return network; }

    private void initialize(List<AddonCandidate> candidates) {
        runtime.phase(LifecyclePhase.ADDON_INITIALIZATION);
        AddonManager manager = new AddonManager(ApiConstants.API_VERSION,
                new BuiltinCapabilityPolicy(Collections.emptySet()),
                (descriptor, granted, resources) -> new ScopedE4steamApi(descriptor, granted, resources, this),
                lifecycleExecutor, link.e4steam.api.ApiLimits.MAX_LIFECYCLE_CALLBACK_MILLIS,
                this::publishAddonLifecycle);
        addons = manager;
        contributions.frozenSupplier(manager::registrationsFrozen);
        manager.initialize(candidates);
        runtime.phase(LifecyclePhase.IDLE);
        beginReadinessObservation();
    }

    private void beginReadinessObservation() {
        Thread observer = new Thread(() -> {
            boolean runtimeReadyPublished = false;
            while (!closed.get()) {
                runtime.refreshReadiness();
                sessions.refresh();
                if (!runtimeReadyPublished
                        && runtime.snapshot().steamState() == SteamRuntimeState.READY) {
                    events.publish(RuntimeReadyEvent.TYPE,
                            new RuntimeReadyEvent(System.currentTimeMillis(), runtime.snapshot()), true);
                    runtimeReadyPublished = true;
                }
                try { Thread.sleep(100L); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); return; }
            }
        }, "e4steam-api-readiness");
        observer.setDaemon(true);
        observer.start();
    }

    CoreRuntimeService runtime() { return runtime; }
    AddonService addons() { return addons; }
    CoreSchedulerService scheduler() { return scheduler; }
    CoreEventBus events() { return events; }
    CoreContributionRegistry contributions() { return contributions; }
    CoreSessionRegistry sessions() { return sessions; }
    AddonNetworkCoordinator network() { return network; }
    RuntimeEnvironment environment() { return environment; }

    /** Runs one registered custom admission policy after the Steam/token gates. */
    public CompletionStage<Boolean> evaluateCustomAccess(String modeId, long remoteSteamId) {
        if (modeId == null || modeId.isEmpty() || remoteSteamId == 0L || closed.get()) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        try {
            CoreContributionRegistry.OwnedContribution contribution =
                    contributions.findOwned("access", modeId);
            SteamClientApiBridge.SessionView view = SteamClientApiBridge.sessionView();
            if (contribution == null || !view.active() || view.generation() <= 0L
                    || !"INTEGRATED_HOST".equals(view.roleCode())) {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
            AccessService.AccessModeId accessModeId = new AccessService.AccessModeId(modeId);
            AccessService.AdmissionContext context = new AccessService.AdmissionContext(
                    new SessionService.SessionId(view.sessionId(), view.generation()),
                    new IdentityService.PeerId(
                            SteamPeerPrivacy.opaquePeerId(view.generation(), remoteSteamId)),
                    contribution.owner(), true);
            return CoreAccessService.evaluateRegistered(
                            contributions, scheduler, accessModeId, context)
                    .handle((result, failure) -> failure == null
                            && result != null
                            && result.isSuccess()
                            && result.value().map(decision ->
                                    decision.kind() == AccessService.AdmissionDecision.Kind.ALLOW)
                            .orElse(false));
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
    }

    private boolean hasCustomAccess(String modeId) {
        CoreContributionRegistry.OwnedContribution contribution =
                contributions.findOwned("access", modeId);
        return contribution != null
                && contribution.value() instanceof AccessService.AccessModeProvider;
    }

    private void publishAddonLifecycle(AddonHandle addon) {
        LifecycleEvent.Kind kind;
        if (addon.state() == AddonState.ACTIVE) kind = LifecycleEvent.Kind.ADDON_ACTIVATED;
        else if (addon.state() == AddonState.DISABLED) kind = LifecycleEvent.Kind.ADDON_DISABLED;
        else if (addon.state() == AddonState.FAILED) kind = LifecycleEvent.Kind.ADDON_FAILED;
        else return;
        if (addon.state() == AddonState.ACTIVE) {
            LOGGER.info("Loaded e4steam addon {} {} (API range {}, core API {})",
                    addon.descriptor().id().value(), addon.descriptor().version(),
                    addon.descriptor().apiRange(), ApiConstants.API_VERSION);
        } else if (addon.state() == AddonState.DISABLED) {
            LOGGER.info("Skipped disabled e4steam addon {} {}",
                    addon.descriptor().id().value(), addon.descriptor().version());
        } else {
            addon.failure().ifPresent(failure -> LOGGER.warn(
                    "Could not load e4steam addon {} {}: code={}, operation={}, category={}, "
                            + "addon API range={}, core API={}",
                    addon.descriptor().id().value(), addon.descriptor().version(),
                    failure.error().code(), failure.error().operation(),
                    failure.error().causeCategory(), addon.descriptor().apiRange(),
                    ApiConstants.API_VERSION));
        }
        events.publish(LifecycleEvent.TYPE, new LifecycleEvent(System.currentTimeMillis(),
                kind, addon.descriptor().id().value(), addon.state().name().toLowerCase(
                java.util.Locale.ROOT)), false);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        runtime.phase(LifecyclePhase.SHUTTING_DOWN);
        events.publish(RuntimeStoppingEvent.TYPE, new RuntimeStoppingEvent(System.currentTimeMillis()), false);
        AddonManager manager = addons;
        if (manager != null) manager.close();
        network.close();
        try { addonNetworkHooks.close(); }
        catch (Exception ignored) { }
        try { customAccessHooks.close(); }
        catch (Exception ignored) { }
        sessions.close();
        scheduler.close();
        lifecycleExecutor.shutdownNow();
        runtime.phase(LifecyclePhase.STOPPED);
    }

    private static ThreadFactory daemonFactory(String prefix) {
        java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + '-' + sequence.incrementAndGet());
            thread.setDaemon(true); return thread;
        };
    }
}
