package link.e4steam.example;

import link.e4steam.api.ApiConstants;
import link.e4steam.api.ApiVersion;
import link.e4steam.api.ApiVersionRange;
import link.e4steam.api.E4steamApi;
import link.e4steam.api.ResourceScope;
import link.e4steam.api.addon.AddonContext;
import link.e4steam.api.addon.AddonDescriptor;
import link.e4steam.api.addon.AddonDependency;
import link.e4steam.api.addon.AddonHandle;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.CapabilityId;
import link.e4steam.api.event.RuntimeReadyEvent;
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
import link.e4steam.api.testkit.TestResourceScope;
import link.e4steam.api.testkit.TestServiceRegistry;
import link.e4steam.api.testkit.StandardFakeServices;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExampleAddonTest {
    @Test
    void exampleInitializesObservesEventSchedulesWorkAndClosesResources() throws Exception {
        RuntimeSnapshot initial = snapshot(SteamRuntimeState.STARTING);
        RuntimeSnapshot ready = snapshot(SteamRuntimeState.READY);
        FakeRuntimeService runtime = new FakeRuntimeService(initial);
        TestEventService events = new TestEventService();
        DeterministicScheduler scheduler = new DeterministicScheduler();
        StandardFakeServices platform = new StandardFakeServices();
        E4steamApi api = new FakeE4steamApi(
                runtime,
                new FakeAddonService(Collections.<AddonHandle>emptyList(), false),
                new FakeCapabilityService(
                        Collections.<CapabilityId>emptySet(),
                        Collections.<CapabilityId>emptySet()
                ),
                events,
                scheduler,
                platform.registerInto(new TestServiceRegistry())
        );
        TestResourceScope resources = new TestResourceScope();
        AddonDescriptor descriptor = new AddonDescriptor(
                new AddonId("e4steam:example"),
                "e4steam API example",
                ApiVersion.parse("1.0.0"),
                new ApiVersionRange(ApiVersion.parse("1.0.0"), ApiVersion.parse("2.0.0")),
                Collections.<AddonDependency>emptyList(),
                Collections.<CapabilityId>emptySet()
        );
        AddonContext context = new AddonContext() {
            @Override
            public AddonDescriptor descriptor() { return descriptor; }

            @Override
            public E4steamApi api() { return api; }

            @Override
            public ResourceScope resources() { return resources; }
        };
        ExampleAddon addon = new ExampleAddon();

        addon.initialize(context);
        assertSame(initial, addon.lastRuntime());
        events.publish(RuntimeReadyEvent.TYPE, new RuntimeReadyEvent(10, ready), true);
        scheduler.runUntilIdle();

        assertSame(ready, addon.lastRuntime());
        assertEquals(1, addon.workerCallbacks());
        resources.close();
        resources.assertNoLeaks();
        platform.close();
    }

    private static RuntimeSnapshot snapshot(SteamRuntimeState steamState) {
        return new RuntimeSnapshot(
                ApiConstants.API_VERSION,
                "0.3.0",
                ApiConstants.WIRE_PROTOCOL_VERSION,
                Platform.WINDOWS,
                Architecture.X86_64,
                RuntimeMode.CLIENT,
                new LoaderInfo("fabric", "0.18.0"),
                "1.20.2",
                steamState,
                LifecyclePhase.IDLE,
                Collections.singleton(TransportCapability.RELIABLE_STREAM),
                Collections.singleton(CompatibilityFlag.LOADER_ADAPTER_PRESENT),
                ""
        );
    }
}
