package link.e4steam.internal.dedicated;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedicatedAccessStoreTest {
    @TempDir Path temporary;

    @Test void persistsSteamAndUuidRulesWithoutNames() {
        Path file = temporary.resolve("access.txt");
        DedicatedAccessStore first = new DedicatedAccessStore(file);
        UUID uuid = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        assertTrue(first.setWhitelisted(76561198000000001L, true));
        assertTrue(first.setBanned(uuid, true));

        DedicatedAccessStore second = new DedicatedAccessStore(file);
        assertTrue(second.whitelisted(76561198000000001L));
        assertTrue(second.banned(uuid));
        assertFalse(second.banned(76561198000000001L));
    }

    @Test void refusesSymbolicPolicyFileWhenSupported() throws Exception {
        Path target = temporary.resolve("target.txt");
        Files.write(target, java.util.Collections.singletonList("allow=1"));
        Path link = temporary.resolve("access.txt");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException unavailable) {
            return;
        }
        assertThrows(SecurityException.class, () -> new DedicatedAccessStore(link));
    }
}
