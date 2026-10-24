package link.e4steam.internal.api;

import link.e4steam.api.*;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.lobby.LobbyService;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class CoreLobbyServiceTest {
    @Test void addonCannotWriteAnotherNamespace() {
        assertTrue(CoreLobbyService.ownsMetadata("k2studio:e4steam_public_servers", metadata("k2studio-e4steam_public_servers:name")));
        assertFalse(CoreLobbyService.ownsMetadata("k2studio:e4steam_public_servers", metadata("other-addon:name")));
        assertFalse(CoreLobbyService.ownsMetadata("k2studio:e4steam_public_servers", metadata("e4steam:mode")));
    }
    @Test void missingCapabilityDeniesBeforeNativeCalls() {
        try (CoreSchedulerService scheduler = new CoreSchedulerService(); ResourceScope scope = new link.e4steam.internal.addon.ManagedResourceScope()) {
            CoreLobbyService service = new CoreLobbyService(new AddonId("test:directory"),
                    new CoreCapabilityService(Set.of(), Set.of()), scheduler, scope);
            var result = service.search(new LobbyService.SearchQuery(metadata("test-directory:schema"), 10, ""))
                    .toCompletableFuture().join();
            assertEquals(ApiErrorCode.CAPABILITY_DENIED, result.error().orElseThrow().code());
        }
    }
    private static LobbyService.Metadata metadata(String key) {
        return new LobbyService.Metadata(Map.of(key, LobbyService.MetadataValue.text("1")));
    }
}
