package link.e4steam.api.testkit;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.config.ConfigService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static link.e4steam.api.testkit.TestResults.completed;
import static link.e4steam.api.testkit.TestResults.failure;

/** Deterministic atomic in-memory config backend for migrations and addon contract tests. */
public final class InMemoryConfigService implements ConfigService {
    private final Map<String, ConfigSchema> schemas = new LinkedHashMap<>();
    private final Map<String, ConfigSnapshot> snapshots = new LinkedHashMap<>();
    private boolean failNextWrite;

    @Override
    public synchronized ApiResult<ConfigSnapshot> open(ConfigSchema schema, ConfigScope scope) {
        if (schema == null || scope == null) throw new NullPointerException("config");
        String storageKey = key(schema.id(), scope);
        ConfigSchema previous = schemas.get(schema.id());
        if (previous != null && previous.version() > schema.version()) return failure(ApiErrorCode.INCOMPATIBLE_VERSION, "config.schema_downgrade", "config.open");
        schemas.put(schema.id(), schema);
        ConfigSnapshot snapshot = snapshots.get(storageKey);
        if (snapshot == null) {
            LinkedHashMap<String, ConfigValue> defaults = new LinkedHashMap<>();
            for (ConfigKey configKey : schema.keys()) defaults.put(configKey.name(), configKey.defaultValue());
            snapshot = new ConfigSnapshot(schema.id(), schema.version(), scope, defaults);
            snapshots.put(storageKey, snapshot);
        }
        return ApiResult.success(snapshot);
    }

    @Override
    public synchronized ApiResult<ConfigSnapshot> snapshot(String schemaId, ConfigScope scope) {
        ConfigSnapshot snapshot = snapshots.get(key(schemaId, scope));
        return snapshot == null ? failure(ApiErrorCode.UNAVAILABLE, "config.not_open", "config.snapshot") : ApiResult.success(snapshot);
    }

    @Override
    public synchronized CompletionStage<ApiResult<ConfigSnapshot>> update(ConfigTransaction transaction) {
        if (transaction == null) throw new NullPointerException("transaction");
        ConfigSchema schema = schemas.get(transaction.schemaId());
        ConfigSnapshot current = snapshots.get(key(transaction.schemaId(), transaction.scope()));
        if (schema == null || current == null) return completed(failure(ApiErrorCode.UNAVAILABLE, "config.not_open", "config.update"));
        if (transaction.expectedVersion() != schema.version()) return completed(failure(ApiErrorCode.INCOMPATIBLE_VERSION, "config.version_mismatch", "config.update"));
        Map<String, ConfigKey> keys = new LinkedHashMap<>();
        for (ConfigKey configKey : schema.keys()) keys.put(configKey.name(), configKey);
        LinkedHashMap<String, ConfigValue> next = new LinkedHashMap<>(current.values());
        for (Map.Entry<String, ConfigValue> entry : transaction.replacements().entrySet()) {
            ConfigKey configKey = keys.get(entry.getKey());
            if (configKey == null || configKey.type() != entry.getValue().type()) return completed(failure(ApiErrorCode.INVALID_ARGUMENT, "config.invalid_key_or_type", "config.update"));
            next.put(entry.getKey(), entry.getValue());
        }
        if (failNextWrite) { failNextWrite = false; return completed(failure(ApiErrorCode.ADDON_FAILURE, "config.injected_write_failure", "config.update")); }
        ConfigSnapshot updated = new ConfigSnapshot(schema.id(), schema.version(), transaction.scope(), next);
        snapshots.put(key(schema.id(), transaction.scope()), updated);
        return completed(ApiResult.success(updated));
    }

    /** Makes the next atomic write fail without mutating the previous snapshot. */ public synchronized void failNextWrite() { failNextWrite = true; }
    private static String key(String schemaId, ConfigScope scope) { if (schemaId == null || scope == null) throw new NullPointerException("config"); return schemaId + '|' + scope.name(); }
}
