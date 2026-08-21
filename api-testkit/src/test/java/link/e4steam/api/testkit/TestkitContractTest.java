package link.e4steam.api.testkit;

import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiConstants;
import link.e4steam.api.Subscription;
import link.e4steam.api.addon.AddonHandle;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.event.EventListener;
import link.e4steam.api.event.RuntimeStoppingEvent;
import link.e4steam.api.logging.SafeLogger;
import link.e4steam.api.runtime.Architecture;
import link.e4steam.api.runtime.CompatibilityFlag;
import link.e4steam.api.runtime.LifecyclePhase;
import link.e4steam.api.runtime.LoaderInfo;
import link.e4steam.api.runtime.Platform;
import link.e4steam.api.runtime.RuntimeMode;
import link.e4steam.api.runtime.RuntimeSnapshot;
import link.e4steam.api.runtime.SteamRuntimeState;
import link.e4steam.api.runtime.TransportCapability;
import link.e4steam.api.scheduler.ExecutionContext;
import link.e4steam.api.scheduler.TaskHandle;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestkitContractTest {
    @Test
    void scopeClosesChildrenOnceInReverseOrder() {
        TestResourceScope scope = new TestResourceScope();
        List<Integer> order = new ArrayList<>();
        scope.own(new TestRegistration(() -> order.add(1)));
        scope.own(new TestRegistration(() -> order.add(2)));

        scope.close();
        scope.close();

        assertEquals(java.util.Arrays.asList(2, 1), order);
        scope.assertNoLeaks();
    }

    @Test
    void capabilityDenialIsTypedAndDoesNotGrantUnknownCapability() {
        FakeCapabilityService service = new FakeCapabilityService(
                Collections.singleton(Capabilities.SESSION_OBSERVE),
                Collections.singleton(Capabilities.SESSION_CONTROL)
        );

        assertTrue(service.granted().isEmpty());
        assertFalse(service.require(Capabilities.SESSION_CONTROL, "session.stop").isSuccess());
    }

    @Test
    void schedulerIsDeterministicAndIsolatesCallbackFailure() {
        DeterministicScheduler scheduler = new DeterministicScheduler();
        AtomicInteger ran = new AtomicInteger();
        ApiResult<TaskHandle> first = scheduler.schedule(
                ExecutionContext.ADDON_WORKER,
                ran::incrementAndGet,
                Duration.ofMillis(10),
                Duration.ofSeconds(1)
        );
        ApiResult<TaskHandle> failing = scheduler.execute(
                ExecutionContext.ADDON_WORKER,
                () -> { throw new IllegalStateException("canary-secret"); },
                Duration.ofSeconds(1)
        );

        scheduler.runUntilIdle();
        assertFalse(failing.value().get().completion().toCompletableFuture().join().isSuccess());
        scheduler.advance(Duration.ofMillis(10));
        assertEquals(1, ran.get());
        assertTrue(first.value().get().completion().toCompletableFuture().join().isSuccess());
    }

    @Test
    void eventCallbacksRunOutsideLocksAndFailuresAreIsolated() {
        TestEventService events = new TestEventService();
        AtomicInteger successful = new AtomicInteger();
        ApiResult<Subscription> broken = events.subscribe(
                RuntimeStoppingEvent.TYPE,
                event -> { throw new IllegalStateException("ignored"); }
        );
        events.subscribe(RuntimeStoppingEvent.TYPE, new EventListener<RuntimeStoppingEvent>() {
            @Override
            public void onEvent(RuntimeStoppingEvent event) {
                successful.incrementAndGet();
            }
        });

        events.publish(RuntimeStoppingEvent.TYPE, new RuntimeStoppingEvent(1), false);

        assertTrue(broken.isSuccess());
        assertEquals(1, successful.get());
        assertEquals(1, events.callbackFailureCount());
    }

    @Test
    void privacyAssertionRejectsCanary() {
        boolean rejected = false;
        try {
            PrivacyAssertions.assertNoSecrets("prefix TOKEN-CANARY suffix", "TOKEN-CANARY");
        } catch (AssertionError expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }

    @Test
    void standardServicesExposeLoggerAndRejectSensitiveFields() {
        StandardFakeServices services = new StandardFakeServices();
        TestServiceRegistry registry = services.registerInto(new TestServiceRegistry());
        FakeE4steamApi api = new FakeE4steamApi(
                new FakeRuntimeService(new RuntimeSnapshot(
                        ApiConstants.API_VERSION,
                        "0.3.0",
                        ApiConstants.WIRE_PROTOCOL_VERSION,
                        Platform.WINDOWS,
                        Architecture.X86_64,
                        RuntimeMode.CLIENT,
                        new LoaderInfo("fabric", "test"),
                        "1.20.2",
                        SteamRuntimeState.READY,
                        LifecyclePhase.IDLE,
                        Collections.singleton(TransportCapability.RELIABLE_STREAM),
                        Collections.singleton(CompatibilityFlag.LOADER_ADAPTER_PRESENT),
                        ""
                )),
                new FakeAddonService(Collections.<AddonHandle>emptyList(), false),
                new FakeCapabilityService(Collections.emptySet(), Collections.emptySet()),
                new TestEventService(),
                new DeterministicScheduler(),
                registry
        );

        assertTrue(api.logger().log(SafeLogger.Level.INFO, "example.ready",
                Collections.singletonMap("attempt", SafeLogger.SafeValue.integer(1))).isSuccess());

        Map<String, SafeLogger.SafeValue> unsafe = new LinkedHashMap<>();
        unsafe.put("authToken", SafeLogger.SafeValue.text("redacted-value"));
        assertFalse(api.logger().log(SafeLogger.Level.INFO, "example.unsafe", unsafe).isSuccess());
        services.close();
    }
}
