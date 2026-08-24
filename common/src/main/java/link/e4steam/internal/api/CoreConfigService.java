package link.e4steam.internal.api;

import link.e4steam.Agnos;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.capability.CapabilityId;
import link.e4steam.api.config.ConfigService;
import link.e4steam.api.storage.StorageService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Typed addon config backed by the same atomic, pathless storage invariants as private data. */
final class CoreConfigService implements ConfigService {
    private static final int MAGIC = 0x45344346; // E4CF
    private static final int FORMAT_VERSION = 1;

    private final AddonId owner;
    private final CoreCapabilityService capabilities;
    private final CoreStorageService persistence;
    private final Map<String, ConfigSchema> schemas = new LinkedHashMap<>();
    private final Map<String, ConfigSnapshot> snapshots = new LinkedHashMap<>();

    CoreConfigService(AddonId owner, CoreCapabilityService capabilities) {
        this(owner, capabilities, defaultRoot(owner));
    }

    CoreConfigService(AddonId owner, CoreCapabilityService capabilities, Path root) {
        this.owner = java.util.Objects.requireNonNull(owner, "owner");
        this.capabilities = java.util.Objects.requireNonNull(capabilities, "capabilities");
        CoreCapabilityService storageCapabilities = new CoreCapabilityService(
                Collections.singleton(Capabilities.STORAGE_PRIVATE),
                Collections.singleton(Capabilities.STORAGE_PRIVATE));
        this.persistence = new CoreStorageService(storageCapabilities, root);
    }

    @Override public synchronized ApiResult<ConfigSnapshot> open(ConfigSchema schema, ConfigScope scope) {
        if (!capabilities.has(Capabilities.CONFIG_READ)) return denied("config.open");
        if (schema == null || scope == null || !owned(schema.id())) return SafeApiErrors.failure(
                ApiErrorCode.SECURITY_REJECTION, "config.open", "WrongOwnerNamespace");
        ConfigSchema existing = schemas.get(schema.id());
        if (existing != null && existing.version() > schema.version()) return SafeApiErrors.failure(
                ApiErrorCode.INCOMPATIBLE_VERSION, "config.open", "SchemaDowngrade");
        schemas.put(schema.id(), schema);
        String memoryKey = key(schema.id(), scope);
        ConfigSnapshot current = snapshots.get(memoryKey);
        if (current != null) return current.version() > schema.version()
                ? SafeApiErrors.failure(ApiErrorCode.INCOMPATIBLE_VERSION,
                "config.open", "SchemaDowngrade") : ApiResult.success(current);

        ConfigSnapshot loaded = null;
        if (scope != ConfigScope.SESSION) {
            ApiResult<StorageService.StoredValue> stored = persistence.get(
                    storageKey(schema.id(), scope, false), StorageService.StorageScope.GLOBAL)
                    .toCompletableFuture().join();
            if (stored.isSuccess()) {
                try {
                    loaded = decode(stored.value().get().bytes());
                } catch (IOException | RuntimeException failure) {
                    return SafeApiErrors.failure(ApiErrorCode.UNAVAILABLE,
                            "config.open", "CorruptConfig");
                }
            } else if (!"NotFound".equals(stored.error().get().causeCategory())) {
                return ApiResult.failure(stored.error().get());
            }
        }

        if (loaded != null && (!loaded.schemaId().equals(schema.id()) || loaded.scope() != scope)) {
            return SafeApiErrors.failure(ApiErrorCode.SECURITY_REJECTION,
                    "config.open", "ConfigBinding");
        }
        if (loaded != null && loaded.version() > schema.version()) {
            return SafeApiErrors.failure(ApiErrorCode.INCOMPATIBLE_VERSION,
                    "config.open", "SchemaDowngrade");
        }
        ApiResult<ConfigSnapshot> normalized = normalize(schema, scope, loaded);
        if (!normalized.isSuccess()) return normalized;
        ConfigSnapshot snapshot = normalized.value().get();
        if (scope != ConfigScope.SESSION && (loaded == null || loaded.version() != schema.version()
                || !loaded.values().equals(snapshot.values()))) {
            ApiResult<Boolean> persisted = persist(snapshot, false);
            if (!persisted.isSuccess()) return ApiResult.failure(persisted.error().get());
        }
        snapshots.put(memoryKey, snapshot);
        return ApiResult.success(snapshot);
    }

    @Override public synchronized ApiResult<ConfigSnapshot> snapshot(
            String schemaId, ConfigScope scope) {
        if (!capabilities.has(Capabilities.CONFIG_READ)) return denied("config.snapshot");
        if (schemaId == null || scope == null || !owned(schemaId)) return SafeApiErrors.failure(
                ApiErrorCode.SECURITY_REJECTION, "config.snapshot", "WrongOwnerNamespace");
        ConfigSnapshot snapshot = snapshots.get(key(schemaId, scope));
        return snapshot == null ? SafeApiErrors.failure(ApiErrorCode.UNAVAILABLE,
                "config.snapshot", "NotOpen") : ApiResult.success(snapshot);
    }

    @Override public synchronized CompletionStage<ApiResult<ConfigSnapshot>> update(
            ConfigTransaction transaction) {
        if (!capabilities.has(Capabilities.CONFIG_WRITE)) return completed(denied("config.update"));
        if (transaction == null || !owned(transaction.schemaId())) return completed(
                SafeApiErrors.failure(ApiErrorCode.SECURITY_REJECTION,
                        "config.update", "WrongOwnerNamespace"));
        ConfigSchema schema = schemas.get(transaction.schemaId());
        ConfigSnapshot current = snapshots.get(key(transaction.schemaId(), transaction.scope()));
        if (schema == null || current == null) return completed(SafeApiErrors.failure(
                ApiErrorCode.UNAVAILABLE, "config.update", "NotOpen"));
        if (schema.version() != transaction.expectedVersion()) return completed(SafeApiErrors.failure(
                ApiErrorCode.INCOMPATIBLE_VERSION, "config.update", "VersionMismatch"));
        Map<String, ConfigKey> keys = schemaKeys(schema);
        LinkedHashMap<String, ConfigValue> next = new LinkedHashMap<>(current.values());
        for (Map.Entry<String, ConfigValue> entry : transaction.replacements().entrySet()) {
            ConfigKey configKey = keys.get(entry.getKey());
            if (configKey == null || configKey.type() != entry.getValue().type()) return completed(
                    SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT,
                            "config.update", "KeyOrType"));
            next.put(entry.getKey(), entry.getValue());
        }
        ConfigSnapshot updated = new ConfigSnapshot(
                schema.id(), schema.version(), transaction.scope(), next);
        if (transaction.scope() != ConfigScope.SESSION) {
            ApiResult<Boolean> backup = persist(current, true);
            if (!backup.isSuccess()) return completed(ApiResult.failure(backup.error().get()));
            ApiResult<Boolean> persisted = persist(updated, false);
            if (!persisted.isSuccess()) return completed(ApiResult.failure(persisted.error().get()));
        }
        snapshots.put(key(schema.id(), transaction.scope()), updated);
        return completed(ApiResult.success(updated));
    }

    private ApiResult<ConfigSnapshot> normalize(
            ConfigSchema schema, ConfigScope scope, ConfigSnapshot loaded) {
        LinkedHashMap<String, ConfigValue> values = new LinkedHashMap<>();
        if (loaded != null) values.putAll(loaded.values());
        Map<String, ConfigKey> known = schemaKeys(schema);
        for (Map.Entry<String, ConfigValue> entry : values.entrySet()) {
            ConfigKey key = known.get(entry.getKey());
            if (key != null && key.type() != entry.getValue().type()) {
                return SafeApiErrors.failure(ApiErrorCode.INCOMPATIBLE_VERSION,
                        "config.migrate", "ConfigTypeChanged");
            }
        }
        for (ConfigKey key : schema.keys()) values.putIfAbsent(key.name(), key.defaultValue());
        return ApiResult.success(new ConfigSnapshot(schema.id(), schema.version(), scope, values));
    }

    private ApiResult<Boolean> persist(ConfigSnapshot snapshot, boolean backup) {
        try {
            byte[] encoded = encode(snapshot);
            ApiResult<StorageService.QuotaSnapshot> stored = persistence.put(
                    storageKey(snapshot.schemaId(), snapshot.scope(), backup),
                    StorageService.StorageScope.GLOBAL,
                    new StorageService.StoredValue(StorageService.StorageFormat.BINARY,
                            FORMAT_VERSION, encoded)).toCompletableFuture().join();
            return stored.isSuccess() ? ApiResult.success(Boolean.TRUE)
                    : ApiResult.failure(stored.error().get());
        } catch (IOException failure) {
            return SafeApiErrors.failure(ApiErrorCode.UNAVAILABLE,
                    "config.persist", "ConfigIo");
        }
    }

    private static byte[] encode(ConfigSnapshot snapshot) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeByte(FORMAT_VERSION);
            output.writeUTF(snapshot.schemaId());
            output.writeInt(snapshot.version());
            output.writeByte(snapshot.scope().ordinal());
            output.writeInt(snapshot.values().size());
            for (Map.Entry<String, ConfigValue> entry : snapshot.values().entrySet()) {
                output.writeUTF(entry.getKey());
                output.writeByte(entry.getValue().type().ordinal());
                output.writeUTF(entry.getValue().encoded());
            }
        }
        return bytes.toByteArray();
    }

    private static ConfigSnapshot decode(byte[] encoded) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC || input.readUnsignedByte() != FORMAT_VERSION) {
                throw new IOException("invalid config header");
            }
            String schemaId = input.readUTF();
            int schemaVersion = input.readInt();
            int scopeIndex = input.readUnsignedByte();
            if (scopeIndex >= ConfigScope.values().length) throw new IOException("invalid config scope");
            int count = input.readInt();
            if (count < 0 || count > link.e4steam.api.ApiLimits.MAX_MAP_ENTRIES) {
                throw new IOException("invalid config entry count");
            }
            LinkedHashMap<String, ConfigValue> values = new LinkedHashMap<>();
            for (int index = 0; index < count; index++) {
                String name = input.readUTF();
                int typeIndex = input.readUnsignedByte();
                if (typeIndex >= ValueType.values().length) throw new IOException("invalid config type");
                ConfigValue value = decodeValue(ValueType.values()[typeIndex], input.readUTF());
                if (values.put(name, value) != null) throw new IOException("duplicate config key");
            }
            if (input.available() != 0) throw new IOException("trailing config data");
            return new ConfigSnapshot(schemaId, schemaVersion, ConfigScope.values()[scopeIndex], values);
        } catch (IllegalArgumentException failure) {
            throw new IOException("invalid config value", failure);
        }
    }

    private static ConfigValue decodeValue(ValueType type, String encoded) {
        switch (type) {
            case STRING: return ConfigValue.text(encoded);
            case INTEGER: return ConfigValue.integer(Integer.parseInt(encoded));
            case LONG: return ConfigValue.longValue(Long.parseLong(encoded));
            case DOUBLE: return ConfigValue.decimal(Double.parseDouble(encoded));
            case BOOLEAN:
                if (!"true".equals(encoded) && !"false".equals(encoded)) {
                    throw new IllegalArgumentException("invalid boolean");
                }
                return ConfigValue.bool(Boolean.parseBoolean(encoded));
            default: throw new IllegalArgumentException("invalid config type");
        }
    }

    private static Map<String, ConfigKey> schemaKeys(ConfigSchema schema) {
        LinkedHashMap<String, ConfigKey> values = new LinkedHashMap<>();
        for (ConfigKey key : schema.keys()) values.put(key.name(), key);
        return values;
    }

    private boolean owned(String id) {
        return id.startsWith(owner.value().replace(':', '-') + ':');
    }

    private static StorageService.StorageKey storageKey(
            String schemaId, ConfigScope scope, boolean backup) {
        return new StorageService.StorageKey((backup ? "backup/" : "current/")
                + digest(schemaId + '|' + scope.name()));
    }

    private static String digest(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder(64);
            for (byte item : digest) text.append(String.format(
                    java.util.Locale.ROOT, "%02x", item & 0xff));
            return text.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static Path defaultRoot(AddonId owner) {
        return Agnos.configDir().resolve("e4steam-addon-data")
                .resolve(owner.value().replace(':', '_')).resolve("config");
    }

    private static String key(String id, ConfigScope scope) { return id + '|' + scope.name(); }
    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }
    private <T> ApiResult<T> denied(String operation) { return SafeApiErrors.failure(
            ApiErrorCode.CAPABILITY_DENIED, operation, "PolicyDenied"); }
}
