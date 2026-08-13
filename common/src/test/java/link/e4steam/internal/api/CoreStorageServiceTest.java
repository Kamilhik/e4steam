package link.e4steam.internal.api;

import link.e4steam.api.ApiResult;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.capability.CapabilityId;
import link.e4steam.api.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreStorageServiceTest {
    @TempDir Path temporary;

    @Test
    void atomicallyPersistsDefensiveValuesAndSupportsCursorPaging() {
        CoreStorageService first = service(temporary.resolve("addon-storage"));
        StorageService.StorageKey alpha = new StorageService.StorageKey("state/alpha");
        StorageService.StorageKey beta = new StorageService.StorageKey("state/beta");
        byte[] payload = "persistent".getBytes(StandardCharsets.UTF_8);

        assertTrue(first.put(alpha, StorageService.StorageScope.GLOBAL,
                new StorageService.StoredValue(StorageService.StorageFormat.UTF8, 1, payload))
                .toCompletableFuture().join().isSuccess());
        payload[0] = 'X';
        assertTrue(first.put(beta, StorageService.StorageScope.GLOBAL,
                new StorageService.StoredValue(StorageService.StorageFormat.JSON, 2,
                        "{}".getBytes(StandardCharsets.UTF_8)))
                .toCompletableFuture().join().isSuccess());

        CoreStorageService reopened = service(temporary.resolve("addon-storage"));
        ApiResult<StorageService.StoredValue> stored = reopened.get(
                alpha, StorageService.StorageScope.GLOBAL).toCompletableFuture().join();
        assertTrue(stored.isSuccess());
        assertArrayEquals("persistent".getBytes(StandardCharsets.UTF_8),
                stored.value().get().bytes());

        ApiResult<List<StorageService.StorageKey>> page = reopened.keys(
                StorageService.StorageScope.GLOBAL, 1, alpha.value())
                .toCompletableFuture().join();
        assertTrue(page.isSuccess());
        assertEquals(Collections.singletonList(beta), page.value().get());
    }

    @Test
    void corruptEntryFailsClosedWithoutReturningPartialData() throws Exception {
        Path root = temporary.resolve("corrupt-storage");
        CoreStorageService first = service(root);
        assertTrue(first.put(new StorageService.StorageKey("state/value"),
                StorageService.StorageScope.GLOBAL,
                new StorageService.StoredValue(StorageService.StorageFormat.BINARY, 1,
                        new byte[] {1, 2, 3})).toCompletableFuture().join().isSuccess());

        Path global = root.resolve("global");
        Path entry;
        try (java.util.stream.Stream<Path> files = Files.list(global)) {
            entry = files.filter(path -> path.getFileName().toString().endsWith(".e4s"))
                    .findFirst().orElseThrow(AssertionError::new);
        }
        Files.write(entry, new byte[] {0, 1, 2});

        CoreStorageService reopened = service(root);
        assertFalse(reopened.quota(StorageService.StorageScope.GLOBAL).isSuccess());
    }

    private static CoreStorageService service(Path root) {
        return new CoreStorageService(new CoreCapabilityService(
                Collections.singleton(Capabilities.STORAGE_PRIVATE),
                Collections.singleton(Capabilities.STORAGE_PRIVATE)), root);
    }
}
