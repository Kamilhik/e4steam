package link.e4steam.internal.addon;

import link.e4steam.api.ApiConstants;
import link.e4steam.api.ApiVersion;
import link.e4steam.api.ApiVersionRange;
import link.e4steam.api.E4steamApi;
import link.e4steam.api.addon.AddonDependency;
import link.e4steam.api.addon.AddonDescriptor;
import link.e4steam.api.addon.AddonHandle;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.addon.AddonState;
import link.e4steam.api.addon.E4steamAddon;
import link.e4steam.api.capability.CapabilityId;
import link.e4steam.api.runtime.Architecture;
import link.e4steam.api.runtime.CompatibilityFlag;
import link.e4steam.api.runtime.LifecyclePhase;
import link.e4steam.api.runtime.LoaderInfo;
import link.e4steam.api.runtime.Platform;
import link.e4steam.api.runtime.RuntimeMode;
import link.e4steam.api.runtime.RuntimeSnapshot;
import link.e4steam.api.runtime.SteamRuntimeState;
import link.e4steam.api.runtime.TransportCapability;
import link.e4steam.api.testkit.DeterministicScheduler;
import link.e4steam.api.testkit.FakeAddonService;
import link.e4steam.api.testkit.FakeCapabilityService;
import link.e4steam.api.testkit.FakeE4steamApi;
import link.e4steam.api.testkit.FakeRuntimeService;
import link.e4steam.api.testkit.TestEventService;
import link.e4steam.api.testkit.TestServiceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonManagerTest {
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach void stopExecutor() { executor.shutdownNow(); }

    @Test
    void initializesInDependencyOrderFreezesAndClosesOwnedResources() {
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean resourceClosed = new AtomicBoolean();
        AddonCandidate base = candidate("test:base", Collections.emptyList(), context -> {
            calls.add("base");
            context.resources().own(registration(resourceClosed));
        });
        AddonDependency requiredBase = new AddonDependency(new AddonId("test:base"), apiRange(), true);
        AddonCandidate child = candidate("test:child", Collections.singletonList(requiredBase),
                context -> calls.add("child"));

        AddonManager manager = manager(1_000L, Collections.emptySet());
        manager.initialize(Arrays.asList(child, base));

        assertEquals(Arrays.asList("base", "child"), calls);
        assertEquals(AddonState.ACTIVE, manager.find(new AddonId("test:base")).get().state());
        assertTrue(manager.registrationsFrozen());
        manager.close();
        assertTrue(resourceClosed.get());
        assertEquals(AddonState.STOPPED, manager.find(new AddonId("test:child")).get().state());
    }

    @Test
    void rejectsDuplicateCycleAndMissingRequiredDependencyWithoutRunningCallbacks() {
        AtomicBoolean called = new AtomicBoolean();
        AddonDependency toB = new AddonDependency(new AddonId("test:b"), apiRange(), true);
        AddonDependency toA = new AddonDependency(new AddonId("test:a"), apiRange(), true);
        AddonCandidate a = candidate("test:a", Collections.singletonList(toB), context -> called.set(true));
        AddonCandidate b = candidate("test:b", Collections.singletonList(toA), context -> called.set(true));
        AddonCandidate duplicateA = candidate("test:a", Collections.emptyList(), context -> called.set(true));
        AddonCandidate missing = candidate("test:missing", Collections.singletonList(
                new AddonDependency(new AddonId("test:absent"), apiRange(), true)), context -> called.set(true));

        AddonManager manager = manager(1_000L, Collections.emptySet());
        manager.initialize(Arrays.asList(a, b, duplicateA, missing));

        assertEquals(AddonState.FAILED, manager.find(new AddonId("test:a")).get().state());
        assertEquals(AddonState.FAILED, manager.find(new AddonId("test:b")).get().state());
        assertEquals(AddonState.FAILED, manager.find(new AddonId("test:missing")).get().state());
        assertTrue(!called.get());
        manager.close();
    }

    @Test
    void ignoresUnknownOptionalCapabilityButRejectsUnknownRequiredCapability() {
        CapabilityId unknown = new CapabilityId("test.unknown");
        AtomicBoolean optionalRan = new AtomicBoolean();
        AddonCandidate optional = candidate(descriptor("test:optional", Collections.emptyList(),
                Collections.singleton(unknown), Collections.emptySet()), context -> optionalRan.set(true));
        AddonCandidate required = candidate(descriptor("test:required", Collections.emptyList(),
                Collections.singleton(unknown), Collections.singleton(unknown)), context -> { });

        AddonManager manager = manager(1_000L, Collections.emptySet());
        manager.initialize(Arrays.asList(optional, required));

        assertTrue(optionalRan.get());
        assertEquals(AddonState.ACTIVE, manager.find(new AddonId("test:optional")).get().state());
        assertEquals(AddonState.FAILED, manager.find(new AddonId("test:required")).get().state());
        manager.close();
    }

    @Test
    void isolatesCallbackFailureAndTimeout() {
        AddonCandidate failed = candidate("test:failed", Collections.emptyList(), context -> {
            throw new IllegalStateException("canary-secret-must-not-escape");
        });
        AddonCandidate slow = candidate("test:slow", Collections.emptyList(), context -> Thread.sleep(10_000L));
        AddonCandidate healthy = candidate("test:healthy", Collections.emptyList(), context -> { });

        AddonManager manager = manager(100L, Collections.emptySet());
        manager.initialize(Arrays.asList(failed, slow, healthy));

        assertEquals(AddonState.FAILED, manager.find(new AddonId("test:failed")).get().state());
        assertEquals(AddonState.FAILED, manager.find(new AddonId("test:slow")).get().state());
        assertEquals(AddonState.ACTIVE, manager.find(new AddonId("test:healthy")).get().state());
        String failure = manager.find(new AddonId("test:failed")).get().failure().get().toString();
        assertTrue(!failure.contains("canary-secret"));
        manager.close();
    }

    private AddonManager manager(long timeout, Set<CapabilityId> denied) {
        AddonApiFactory factory = (descriptor, granted, resources) -> new FakeE4steamApi(
                new FakeRuntimeService(runtimeSnapshot()),
                new FakeAddonService(Collections.<AddonHandle>emptyList(), false),
                new FakeCapabilityService(descriptor.requestedCapabilities(), granted),
                new TestEventService(),
                new DeterministicScheduler(),
                new TestServiceRegistry()
        );
        return new AddonManager(ApiConstants.API_VERSION, new BuiltinCapabilityPolicy(denied),
                factory, executor, timeout, handle -> { });
    }

    private static AddonCandidate candidate(String id, List<AddonDependency> dependencies, E4steamAddon addon) {
        return candidate(descriptor(id, dependencies, Collections.emptySet(), Collections.emptySet()), addon);
    }

    private static AddonCandidate candidate(AddonDescriptor descriptor, E4steamAddon addon) {
        return new AddonCandidate(descriptor, addon, "test-addon", true);
    }

    private static AddonDescriptor descriptor(String id, List<AddonDependency> dependencies,
                                              Set<CapabilityId> requested, Set<CapabilityId> required) {
        return new AddonDescriptor(new AddonId(id), id, ApiVersion.parse("1.0.0"),
                apiRange(), dependencies, requested, required);
    }

    private static ApiVersionRange apiRange() {
        return new ApiVersionRange(ApiVersion.parse("1.0.0"), ApiVersion.parse("2.0.0"));
    }

    private static RuntimeSnapshot runtimeSnapshot() {
        return new RuntimeSnapshot(ApiConstants.API_VERSION, "0.3.0", ApiConstants.WIRE_PROTOCOL_VERSION,
                Platform.WINDOWS, Architecture.X86_64, RuntimeMode.CLIENT,
                new LoaderInfo("test", "1.0.0"), "1.20.2", SteamRuntimeState.READY,
                LifecyclePhase.IDLE, Collections.singleton(TransportCapability.RELIABLE_STREAM),
                Collections.singleton(CompatibilityFlag.LOADER_ADAPTER_PRESENT), "");
    }

    private static link.e4steam.api.Registration registration(AtomicBoolean closed) {
        return new link.e4steam.api.Registration() {
            @Override public void close() { closed.set(true); }
            @Override public boolean isClosed() { return closed.get(); }
        };
    }
}
