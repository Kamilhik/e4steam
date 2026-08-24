package link.e4steam.api.world;

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

/** Typed allowlisted world settings; raw server.properties and security switches are never exposed. */
public interface WorldSettingsService {
    /** Returns settings supported by this Minecraft/version/runtime adapter. */ ApiResult<WorldSettingsSchema> schema();
    /** Returns immutable current values. */ ApiResult<WorldSettingsSnapshot> snapshot();
    /** Validates a proposal independently and returns an immutable plan before application. */ CompletionStage<ApiResult<WorldSettingsPlan>> plan(WorldSettingsProposal proposal);
    /** Applies a previously validated plan with host/admin confirmation enforced by core. */ CompletionStage<ApiResult<WorldSettingsSnapshot>> apply(WorldSettingsPlan plan, boolean confirmed);

    /** Application timing determined by the version adapter. */ enum ApplyTiming { IMMEDIATE, NEXT_WORLD_START, RESTART_REQUIRED }
    /** Supported value kind. */ enum SettingType { STRING, INTEGER, BOOLEAN, ENUM }

    /** Typed namespaced setting key. */
    final class WorldSettingKey {
        private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9_.-]{0,31}:[a-z][a-z0-9_.-]{0,63}$");
        private final String value; private final SettingType type;
        /** Creates an allowlist candidate and rejects security/network keys. */
        public WorldSettingKey(String value, SettingType type) {
            this.value = ApiValidation.identifier(value, "worldSettingKey", FORMAT);
            this.type = Objects.requireNonNull(type, "type");
            String normalized = this.value.toLowerCase(java.util.Locale.ROOT);
            String[] forbidden = {"online-mode", "authentication", "owner", "hostidentity", "server-ip", "server-port", "bind", "appid", "steam", "ban-disable", "ratelimit-disable"};
            for (String candidate : forbidden) if (normalized.contains(candidate)) throw new IllegalArgumentException("security/network world setting is forbidden");
        }
        /** Returns id. */ public String value() { return value; }
        /** Returns value type. */ public SettingType type() { return type; }
        @Override public boolean equals(Object other) { return this == other || other instanceof WorldSettingKey && value.equals(((WorldSettingKey) other).value) && type == ((WorldSettingKey) other).type; }
        @Override public int hashCode() { return 31 * value.hashCode() + type.hashCode(); }
        @Override public String toString() { return value; }
    }

    /** Canonical immutable setting value. */
    final class WorldSettingValue {
        private final SettingType type; private final String encoded;
        private WorldSettingValue(SettingType type, String encoded) { this.type = type; this.encoded = encoded; }
        /** Creates text. */ public static WorldSettingValue text(String value) { return new WorldSettingValue(SettingType.STRING, ApiValidation.text(value, "value", 256)); }
        /** Creates enum text. */ public static WorldSettingValue enumValue(String value) { return new WorldSettingValue(SettingType.ENUM, ApiValidation.text(value, "value", 64)); }
        /** Creates integer. */ public static WorldSettingValue integer(int value) { return new WorldSettingValue(SettingType.INTEGER, Integer.toString(value)); }
        /** Creates boolean. */ public static WorldSettingValue bool(boolean value) { return new WorldSettingValue(SettingType.BOOLEAN, Boolean.toString(value)); }
        /** Returns type. */ public SettingType type() { return type; }
        /** Returns canonical encoding. */ public String encoded() { return encoded; }
        @Override public String toString() {
            return type == SettingType.STRING || type == SettingType.ENUM
                    ? type + "{redacted-from-toString}" : encoded;
        }
    }

    /** One adapter-supported rule including impact and timing. */
    final class WorldSettingRule {
        private final WorldSettingKey key; private final ApplyTiming timing; private final boolean confirmationRequired; private final List<String> allowedValues;
        /** Creates a rule. */ public WorldSettingRule(WorldSettingKey key, ApplyTiming timing, boolean confirmationRequired, List<String> allowedValues) { this.key = Objects.requireNonNull(key, "key"); this.timing = Objects.requireNonNull(timing, "timing"); this.confirmationRequired = confirmationRequired; this.allowedValues = ApiValidation.immutableList(allowedValues, 128, "allowedValues"); }
        /** Returns key. */ public WorldSettingKey key() { return key; }
        /** Returns timing. */ public ApplyTiming timing() { return timing; }
        /** Returns confirmation requirement. */ public boolean confirmationRequired() { return confirmationRequired; }
        /** Returns bounded enum/range descriptions. */ public List<String> allowedValues() { return allowedValues; }
    }

    /** Adapter-specific immutable allowlist. */
    final class WorldSettingsSchema {
        private final int version; private final List<WorldSettingRule> rules;
        /** Creates a schema. */ public WorldSettingsSchema(int version, List<WorldSettingRule> rules) { if (version < 1) throw new IllegalArgumentException("invalid version"); this.version = version; this.rules = ApiValidation.immutableList(rules, ApiLimits.MAX_MAP_ENTRIES, "rules"); }
        /** Returns version. */ public int version() { return version; }
        /** Returns rules. */ public List<WorldSettingRule> rules() { return rules; }
    }

    /** Immutable current settings. */
    final class WorldSettingsSnapshot {
        private final int schemaVersion; private final Map<WorldSettingKey, WorldSettingValue> values;
        /** Creates a snapshot. */ public WorldSettingsSnapshot(int schemaVersion, Map<WorldSettingKey, WorldSettingValue> values) { if (schemaVersion < 1) throw new IllegalArgumentException("invalid schemaVersion"); this.schemaVersion = schemaVersion; this.values = immutable(values); }
        /** Returns schema version. */ public int schemaVersion() { return schemaVersion; }
        /** Returns immutable values. */ public Map<WorldSettingKey, WorldSettingValue> values() { return values; }
    }

    /** Requested changes, not authority to apply them. */
    final class WorldSettingsProposal {
        private final Map<WorldSettingKey, WorldSettingValue> changes;
        /** Creates a proposal. */ public WorldSettingsProposal(Map<WorldSettingKey, WorldSettingValue> changes) { this.changes = immutable(changes); }
        /** Returns requested changes. */ public Map<WorldSettingKey, WorldSettingValue> changes() { return changes; }
    }

    /** Core-validated immutable apply plan. */
    final class WorldSettingsPlan {
        private final String planId; private final Map<WorldSettingKey, WorldSettingValue> changes; private final ApplyTiming timing; private final boolean confirmationRequired;
        /** Creates a plan. */ public WorldSettingsPlan(String planId, Map<WorldSettingKey, WorldSettingValue> changes, ApplyTiming timing, boolean confirmationRequired) { this.planId = ApiValidation.text(planId, "planId", 96); ApiValidation.rejectSensitiveName(this.planId, "planId"); this.changes = immutable(changes); this.timing = Objects.requireNonNull(timing, "timing"); this.confirmationRequired = confirmationRequired; }
        /** Returns opaque plan id. */ public String planId() { return planId; }
        /** Returns validated changes. */ public Map<WorldSettingKey, WorldSettingValue> changes() { return changes; }
        /** Returns strongest required timing. */ public ApplyTiming timing() { return timing; }
        /** Returns whether host/admin confirmation is mandatory. */ public boolean confirmationRequired() { return confirmationRequired; }
        @Override public String toString() { return "WorldSettingsPlan{id='" + planId + "', keys=" + changes.keySet() + ", timing=" + timing + '}'; }
    }

    /** Defensive map validation shared by DTOs. */
    static Map<WorldSettingKey, WorldSettingValue> immutable(Map<WorldSettingKey, WorldSettingValue> values) {
        if (values == null || values.size() > ApiLimits.MAX_MAP_ENTRIES) throw new IllegalArgumentException("invalid settings map");
        LinkedHashMap<WorldSettingKey, WorldSettingValue> copy = new LinkedHashMap<>();
        for (Map.Entry<WorldSettingKey, WorldSettingValue> entry : values.entrySet()) { WorldSettingKey key = Objects.requireNonNull(entry.getKey(), "key"); WorldSettingValue value = Objects.requireNonNull(entry.getValue(), "value"); if (key.type() != value.type()) throw new IllegalArgumentException("setting type mismatch"); copy.put(key, value); }
        return Collections.unmodifiableMap(copy);
    }
}
