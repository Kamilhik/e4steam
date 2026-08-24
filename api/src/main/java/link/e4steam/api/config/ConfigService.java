package link.e4steam.api.config;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Per-addon typed configuration with validation, migrations and atomic transactions. */
public interface ConfigService {
    /** Registers or validates one schema before use. */ ApiResult<ConfigSnapshot> open(ConfigSchema schema, ConfigScope scope);
    /** Returns current immutable snapshot. */ ApiResult<ConfigSnapshot> snapshot(String schemaId, ConfigScope scope);
    /** Atomically validates and applies one update with backup/rollback. */ CompletionStage<ApiResult<ConfigSnapshot>> update(ConfigTransaction transaction);

    /** Supported config scope. */ enum ConfigScope { GLOBAL, PROFILE, WORLD, SESSION }
    /** Supported primitive kind. */ enum ValueType { STRING, INTEGER, LONG, DOUBLE, BOOLEAN }

    /** Typed immutable primitive value. */
    final class ConfigValue {
        private final ValueType type; private final String encoded;
        private ConfigValue(ValueType type, String encoded) { this.type = type; this.encoded = encoded; }
        /** Creates text. */ public static ConfigValue text(String value) { return new ConfigValue(ValueType.STRING, ApiValidation.text(value, "value", ApiLimits.MAX_VALUE_LENGTH)); }
        /** Creates integer. */ public static ConfigValue integer(int value) { return new ConfigValue(ValueType.INTEGER, Integer.toString(value)); }
        /** Creates long. */ public static ConfigValue longValue(long value) { return new ConfigValue(ValueType.LONG, Long.toString(value)); }
        /** Creates finite double. */ public static ConfigValue decimal(double value) { if (Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException("value must be finite"); return new ConfigValue(ValueType.DOUBLE, Double.toString(value)); }
        /** Creates boolean. */ public static ConfigValue bool(boolean value) { return new ConfigValue(ValueType.BOOLEAN, Boolean.toString(value)); }
        /** Returns type. */ public ValueType type() { return type; }
        /** Returns canonical value; sensitive snapshots use redacted text. */ public String encoded() { return encoded; }
        @Override public String toString() {
            return type == ValueType.STRING ? "STRING{redacted-from-toString}" : type + ":" + encoded;
        }
    }

    /** One schema key with default and sensitivity. */
    final class ConfigKey {
        private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9_.-]{0,63}$");
        private final String name; private final ValueType type; private final ConfigValue defaultValue; private final boolean sensitive;
        /** Creates a key. */ public ConfigKey(String name, ValueType type, ConfigValue defaultValue, boolean sensitive) { this.name = ApiValidation.identifier(name, "config key", FORMAT); ApiValidation.rejectSensitiveName(this.name, "config key"); this.type = Objects.requireNonNull(type, "type"); this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue"); if (type != defaultValue.type()) throw new IllegalArgumentException("default type mismatch"); this.sensitive = sensitive; }
        /** Returns key. */ public String name() { return name; }
        /** Returns type. */ public ValueType type() { return type; }
        /** Returns default. */ public ConfigValue defaultValue() { return defaultValue; }
        /** Returns diagnostic-redaction marker. */ public boolean sensitive() { return sensitive; }
    }

    /** Versioned typed schema. */
    final class ConfigSchema {
        private final String id; private final int version; private final List<ConfigKey> keys;
        /** Creates a schema. */ public ConfigSchema(String id, int version, List<ConfigKey> keys) { this.id = ApiValidation.text(id, "schema id", ApiLimits.MAX_IDENTIFIER_LENGTH); if (version < 1) throw new IllegalArgumentException("invalid version"); this.version = version; this.keys = ApiValidation.immutableList(keys, ApiLimits.MAX_MAP_ENTRIES, "keys"); java.util.HashSet<String> names = new java.util.HashSet<>(); for (ConfigKey key : this.keys) if (!names.add(key.name())) throw new IllegalArgumentException("duplicate config key"); }
        /** Returns schema id. */ public String id() { return id; }
        /** Returns version. */ public int version() { return version; }
        /** Returns keys. */ public List<ConfigKey> keys() { return keys; }
    }

    /** Immutable snapshot whose sensitive values may be redacted by implementation. */
    final class ConfigSnapshot {
        private final String schemaId; private final int version; private final ConfigScope scope; private final Map<String, ConfigValue> values;
        /** Creates a snapshot. */ public ConfigSnapshot(String schemaId, int version, ConfigScope scope, Map<String, ConfigValue> values) { this.schemaId = ApiValidation.text(schemaId, "schemaId", ApiLimits.MAX_IDENTIFIER_LENGTH); if (version < 1) throw new IllegalArgumentException("invalid version"); this.version = version; this.scope = Objects.requireNonNull(scope, "scope"); this.values = immutable(values); }
        /** Returns schema id. */ public String schemaId() { return schemaId; }
        /** Returns version. */ public int version() { return version; }
        /** Returns scope. */ public ConfigScope scope() { return scope; }
        /** Returns immutable values. */ public Map<String, ConfigValue> values() { return values; }
        @Override public String toString() { return "ConfigSnapshot{schema='" + schemaId + "', version=" + version + ", keys=" + values.keySet() + '}'; }
    }

    /** Atomic update transaction; unknown fields are retained by implementation policy. */
    final class ConfigTransaction {
        private final String schemaId; private final int expectedVersion; private final ConfigScope scope; private final Map<String, ConfigValue> replacements;
        /** Creates a transaction. */ public ConfigTransaction(String schemaId, int expectedVersion, ConfigScope scope, Map<String, ConfigValue> replacements) { this.schemaId = ApiValidation.text(schemaId, "schemaId", ApiLimits.MAX_IDENTIFIER_LENGTH); if (expectedVersion < 1) throw new IllegalArgumentException("invalid expectedVersion"); this.expectedVersion = expectedVersion; this.scope = Objects.requireNonNull(scope, "scope"); this.replacements = immutable(replacements); }
        /** Returns schema id. */ public String schemaId() { return schemaId; }
        /** Returns expected schema version. */ public int expectedVersion() { return expectedVersion; }
        /** Returns scope. */ public ConfigScope scope() { return scope; }
        /** Returns replacements. */ public Map<String, ConfigValue> replacements() { return replacements; }
    }

    /** Schema migration callback; implementations apply it before atomic persistence. */
    interface Migration { /** Migrates one bounded snapshot. */ ApiResult<ConfigSnapshot> migrate(ConfigSnapshot previous); }

    /** Defensive map helper. */
    static Map<String, ConfigValue> immutable(Map<String, ConfigValue> values) {
        if (values == null || values.size() > ApiLimits.MAX_MAP_ENTRIES) throw new IllegalArgumentException("invalid values");
        LinkedHashMap<String, ConfigValue> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigValue> entry : values.entrySet()) { String key = ApiValidation.text(entry.getKey(), "key", 64); ApiValidation.rejectSensitiveName(key, "key"); copy.put(key, Objects.requireNonNull(entry.getValue(), "value")); }
        return Collections.unmodifiableMap(copy);
    }
}
