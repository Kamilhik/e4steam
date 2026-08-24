package link.e4steam.internal.api;

import link.e4steam.api.ApiResult;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.capability.CapabilityId;
import link.e4steam.api.access.AccessService;
import link.e4steam.api.identity.IdentityService;
import link.e4steam.api.session.SessionService;
import link.e4steam.api.testkit.TestResourceScope;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreAccessServiceTest {
    @Test
    void policyFailureAndTimeoutDenyWhileFreezeBlocksLateRegistration() throws Exception {
        CoreSchedulerService scheduler = new CoreSchedulerService();
        TestResourceScope resources = new TestResourceScope();
        try {
            Set<CapabilityId> grants = new LinkedHashSet<>(Arrays.asList(
                    Capabilities.ACCESS_MODE_REGISTER,
                    Capabilities.ACCESS_POLICY_EVALUATE));
            CoreContributionRegistry registry = new CoreContributionRegistry();
            AtomicBoolean frozen = new AtomicBoolean();
            registry.frozenSupplier(frozen::get);
            AddonId owner = new AddonId("test:addon");
            CoreAccessService access = new CoreAccessService(owner,
                    new CoreCapabilityService(grants, grants), registry, resources, scheduler);
            AccessService.AccessModeId brokenId = new AccessService.AccessModeId("test-addon:broken");
            assertTrue(access.register(provider(brokenId, context -> {
                throw new IllegalStateException("secret-CANARY");
            })).isSuccess());

            ApiResult<AccessService.AdmissionDecision> broken = access.evaluate(
                    brokenId, context(owner)).toCompletableFuture().get(1, TimeUnit.SECONDS);
            assertTrue(broken.isSuccess());
            assertEquals(AccessService.AdmissionDecision.Kind.DENY,
                    broken.value().get().kind());

            AccessService.AccessModeId slowId = new AccessService.AccessModeId("test-addon:slow");
            assertTrue(access.register(provider(slowId,
                    context -> new CompletableFuture<>())).isSuccess());
            ApiResult<AccessService.AdmissionDecision> timed = access.evaluate(
                    slowId, context(owner)).toCompletableFuture().get(4, TimeUnit.SECONDS);
            assertTrue(timed.isSuccess());
            assertEquals("addon-policy-timeout", timed.value().get().reasonCode());

            frozen.set(true);
            assertTrue(access.registrationsFrozen());
            assertFalse(access.register(provider(
                    new AccessService.AccessModeId("test-addon:late"),
                    context -> CompletableFuture.completedFuture(
                            AccessService.AdmissionDecision.allow()))).isSuccess());
        } finally {
            resources.close();
            scheduler.close();
        }
    }

    private static AccessService.AccessModeProvider provider(
            AccessService.AccessModeId id, AccessService.AdmissionPolicy policy) {
        return new AccessService.AccessModeProvider() {
            @Override public AccessService.AccessModeId id() { return id; }
            @Override public String displayNameKey() { return "test-addon:mode"; }
            @Override public AccessService.AdmissionPolicy policy() { return policy; }
        };
    }

    private static AccessService.AdmissionContext context(AddonId owner) {
        return new AccessService.AdmissionContext(
                new SessionService.SessionId("session_1", 1L),
                new IdentityService.PeerId("peer_test1"), owner, true);
    }
}
