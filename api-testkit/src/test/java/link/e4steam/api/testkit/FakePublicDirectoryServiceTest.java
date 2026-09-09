package link.e4steam.api.testkit;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.directory.PublicDirectoryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FakePublicDirectoryServiceTest {
    @Test void rejectsJoinHandleReplayAndCancelsIdempotentOperation() throws Exception {
        FakePublicDirectoryService service = new FakePublicDirectoryService();
        PublicDirectoryService.JoinRequest request = new PublicDirectoryService.JoinRequest(
                new PublicDirectoryService.OpaqueTargetRef("iw_12345678901234567"),
                new PublicDirectoryService.JoinHandleRef("join_1234567890123456"));
        link.e4steam.api.ApiResult<PublicDirectoryService.JoinOperation> first =
                service.join(request).toCompletableFuture().get();
        assertTrue(first.isSuccess());
        link.e4steam.api.ApiResult<PublicDirectoryService.JoinOperation> replay =
                service.join(request).toCompletableFuture().get();
        assertEquals(ApiErrorCode.SECURITY_REJECTION,
                replay.error().orElseThrow(AssertionError::new).code());
        PublicDirectoryService.JoinOperationId id =
                first.value().orElseThrow(AssertionError::new).id();
        assertEquals(PublicDirectoryService.JoinState.CANCELLED,
                service.cancel(id).toCompletableFuture().get().value()
                        .orElseThrow(AssertionError::new).state());
        assertEquals(PublicDirectoryService.JoinState.CANCELLED,
                service.joinSnapshot(id).value().orElseThrow(AssertionError::new).state());
    }
}
