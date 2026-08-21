package link.e4steam.internal.api;

import link.e4steam.api.ApiResult;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.capability.CapabilityId;
import link.e4steam.api.config.ConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreConfigServiceTest {
    @TempDir Path temporary;

    @Test
    void persistsAtomicUpdatesAndPerformsSafeAdditiveSchemaMigration() {
        Path root = temporary.resolve("config-store");
        CoreConfigService first = service(root, true);
        ConfigService.ConfigSchema versionOne = new ConfigService.ConfigSchema(
                "test-addon:settings", 1,
                Collections.singletonList(new ConfigService.ConfigKey(
                        "enabled", ConfigService.ValueType.BOOLEAN,
                        ConfigService.ConfigValue.bool(true), false)));
        assertTrue(first.open(versionOne, ConfigService.ConfigScope.GLOBAL).isSuccess());

        Map<String, ConfigService.ConfigValue> replacements = new LinkedHashMap<>();
        replacements.put("enabled", ConfigService.ConfigValue.bool(false));
        ApiResult<ConfigService.ConfigSnapshot> updated = first.update(
                new ConfigService.ConfigTransaction("test-addon:settings", 1,
                        ConfigService.ConfigScope.GLOBAL, replacements))
                .toCompletableFuture().join();
        assertTrue(updated.isSuccess());

        CoreConfigService reopened = service(root, true);
        ConfigService.ConfigSchema versionTwo = new ConfigService.ConfigSchema(
                "test-addon:settings", 2,
                Arrays.asList(
                        new ConfigService.ConfigKey("enabled", ConfigService.ValueType.BOOLEAN,
                                ConfigService.ConfigValue.bool(true), false),
                        new ConfigService.ConfigKey("retries", ConfigService.ValueType.INTEGER,
                                ConfigService.ConfigValue.integer(3), false)));
        ApiResult<ConfigService.ConfigSnapshot> migrated = reopened.open(
                versionTwo, ConfigService.ConfigScope.GLOBAL);

        assertTrue(migrated.isSuccess());
        assertEquals(2, migrated.value().get().version());
        assertEquals("false", migrated.value().get().values().get("enabled").encoded());
        assertEquals("3", migrated.value().get().values().get("retries").encoded());
    }

    @Test
    void writeCapabilityIsCheckedAtPointOfUse() {
        CoreConfigService readOnly = service(temporary.resolve("read-only"), false);
        ConfigService.ConfigSchema schema = new ConfigService.ConfigSchema(
                "test-addon:settings", 1,
                Collections.singletonList(new ConfigService.ConfigKey(
                        "enabled", ConfigService.ValueType.BOOLEAN,
                        ConfigService.ConfigValue.bool(true), false)));
        assertTrue(readOnly.open(schema, ConfigService.ConfigScope.SESSION).isSuccess());
        assertFalse(readOnly.update(new ConfigService.ConfigTransaction(
                schema.id(), 1, ConfigService.ConfigScope.SESSION,
                Collections.singletonMap("enabled", ConfigService.ConfigValue.bool(false))))
                .toCompletableFuture().join().isSuccess());
    }

    private static CoreConfigService service(Path root, boolean writable) {
        Set<CapabilityId> capabilities = new LinkedHashSet<>();
        capabilities.add(Capabilities.CONFIG_READ);
        if (writable) capabilities.add(Capabilities.CONFIG_WRITE);
        return new CoreConfigService(new AddonId("test:addon"),
                new CoreCapabilityService(capabilities, capabilities), root);
    }
}
