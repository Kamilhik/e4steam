package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.capability.CapabilityId;
import link.e4steam.api.directory.PublicDirectoryService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorePublicDirectoryServiceTest {
    @Test
    void missingCapabilityFailsClosed() {
        CorePublicDirectoryService service = new CorePublicDirectoryService(capabilities());
        ApiResult<PublicDirectoryService.DirectoryAvailability> result = service.availability();
        assertFalse(result.isSuccess());
        assertEquals(ApiErrorCode.CAPABILITY_DENIED, result.error().orElseThrow().code());
    }

    @Test
    void noRuntimeIsReportedWithoutCrashingWhenAddonIsPresent() {
        CorePublicDirectoryService service = new CorePublicDirectoryService(
                capabilities(Capabilities.DIRECTORY_JOIN));
        ApiResult<PublicDirectoryService.DirectoryAvailability> result = service.availability();
        assertTrue(result.isSuccess());
        assertEquals(PublicDirectoryService.DirectoryAvailability.NO_ACTIVE_SESSION,
                result.value().orElseThrow());
    }

    @Test
    void joinHandlesAreOneUseAndFailuresDoNotPermanentlyFillTheQueue() throws Exception {
        CorePublicDirectoryService service = new CorePublicDirectoryService(
                capabilities(Capabilities.DIRECTORY_JOIN));
        PublicDirectoryService.OpaqueTargetRef unknown =
                new PublicDirectoryService.OpaqueTargetRef("unknown_1234567890123456");

        PublicDirectoryService.JoinHandleRef firstHandle =
                new PublicDirectoryService.JoinHandleRef("join.1234567890123456");
        PublicDirectoryService.JoinRequest first =
                new PublicDirectoryService.JoinRequest(unknown, firstHandle);
        ApiResult<PublicDirectoryService.JoinOperation> failed = service.join(first)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertTrue(failed.isSuccess());
        assertEquals(PublicDirectoryService.JoinState.STALE,
                failed.value().orElseThrow().state());

        ApiResult<PublicDirectoryService.JoinOperation> replay = service.join(first)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertFalse(replay.isSuccess());
        assertEquals(ApiErrorCode.SECURITY_REJECTION,
                replay.error().orElseThrow().code());

        for (int index = 0; index < 64; index++) {
            String handle = String.format("join.%016d", index + 100L);
            ApiResult<PublicDirectoryService.JoinOperation> result = service.join(
                            new PublicDirectoryService.JoinRequest(unknown,
                                    new PublicDirectoryService.JoinHandleRef(handle)))
                    .toCompletableFuture().get(1, TimeUnit.SECONDS);
            assertTrue(result.isSuccess(), "terminal operation " + index + " must be evicted");
            assertEquals(PublicDirectoryService.JoinState.STALE,
                    result.value().orElseThrow().state());
        }
    }

    private static CoreCapabilityService capabilities(CapabilityId... values) {
        Set<CapabilityId> set = new LinkedHashSet<>(Arrays.asList(values));
        return new CoreCapabilityService(set, set);
    }
}
