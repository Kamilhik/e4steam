package link.e4steam.api.directory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PublicDirectoryServiceTest {
    @Test void acceptsSecureOriginsAndLoopbackOnlyDevelopmentHttp() {
        PublicDirectoryService.DirectoryOrigin origin =
                new PublicDirectoryService.DirectoryOrigin("https://Registry.Example/");
        assertEquals("https://registry.example", origin.value());
        assertThrows(IllegalArgumentException.class,
                () -> new PublicDirectoryService.DirectoryOrigin("http://registry.example"));
        assertEquals("http://127.0.0.1:8090",
                new PublicDirectoryService.DirectoryOrigin("http://127.0.0.1:8090/").value());
        assertThrows(IllegalArgumentException.class,
                () -> new PublicDirectoryService.DirectoryOrigin("https://user@registry.example"));
        assertThrows(IllegalArgumentException.class,
                () -> new PublicDirectoryService.DirectoryOrigin("https://registry.example/api"));
    }

    @Test void sensitiveValuesStayRedactedFromStringRepresentations() {
        PublicDirectoryService.OpaqueTargetRef target =
                new PublicDirectoryService.OpaqueTargetRef("iw_12345678901234567");
        PublicDirectoryService.JoinHandleRef handle =
                new PublicDirectoryService.JoinHandleRef("join_1234567890123456");
        PublicDirectoryService.JoinRequest request = new PublicDirectoryService.JoinRequest(target, handle);
        PublicDirectoryService.AttestationReceipt receipt = new PublicDirectoryService.AttestationReceipt(
                "owner.123456789012", "receipt.123456789012", 10_000L);
        assertFalse(target.toString().contains(target.value()));
        assertFalse(handle.toString().contains(handle.value()));
        assertFalse(request.toString().contains(handle.value()));
        assertFalse(receipt.toString().contains(receipt.receiptRef()));
    }

    @Test void validatesGenerationAndSafeDetailCodes() {
        PublicDirectoryService.OpaqueTargetRef target =
                new PublicDirectoryService.OpaqueTargetRef("iw_12345678901234567");
        assertThrows(IllegalArgumentException.class, () -> new PublicDirectoryService.PublicationTarget(
                PublicDirectoryService.TargetKind.INTEGRATED_WORLD, target, 0L));
        PublicDirectoryService.JoinOperationId id =
                new PublicDirectoryService.JoinOperationId("operation_123456789012");
        assertThrows(IllegalArgumentException.class, () -> new PublicDirectoryService.JoinOperation(
                id, PublicDirectoryService.JoinState.FAILED, "raw endpoint: 127.0.0.1"));
    }

    @Test void acceptsNamespacedRegistryChallengeValues() {
        PublicDirectoryService.AttestationChallenge challenge =
                new PublicDirectoryService.AttestationChallenge(
                        new PublicDirectoryService.DirectoryOrigin("http://127.0.0.1:8090"),
                        "chal.7OvBmB3-e6NBXt00vdNOKFBZ7YdBgM_m",
                        "nonce.3SZ7J44OGB9fBoP_Jr6hntJ3cwi396YMFgvP--ZCefU",
                        PublicDirectoryService.AttestationAction.PUBLISH_INTEGRATED,
                        1_788_377_100_394L);
        assertEquals("chal.7OvBmB3-e6NBXt00vdNOKFBZ7YdBgM_m", challenge.challengeId());
    }

    @Test void acceptsNamespacedRegistryJoinHandles() {
        PublicDirectoryService.JoinHandleRef handle =
                new PublicDirectoryService.JoinHandleRef(
                        "join.LxSviWzB7Jx7rm0q5VTn4y7Qw_BIvnbr");
        assertEquals("join.LxSviWzB7Jx7rm0q5VTn4y7Qw_BIvnbr", handle.value());
    }
}
