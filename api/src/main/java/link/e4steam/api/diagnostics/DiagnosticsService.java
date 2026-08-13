package link.e4steam.api.diagnostics;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;
import link.e4steam.api.Registration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Allowlist-based privacy-safe health, Doctor and export-preview diagnostics. */
public interface DiagnosticsService {
    /** Returns bounded component health without personal data by default. */ ApiResult<HealthSnapshot> health();
    /** Registers a time/size-bounded safe section contributor. */ ApiResult<Registration> registerContributor(DiagnosticsContributor contributor);
    /** Builds a user-triggered Doctor preview; this method never uploads it. */ CompletionStage<ApiResult<DoctorPreview>> doctorPreview(PrivacyOptions options);

    /** Component health. */ enum Health { HEALTHY, DEGRADED, UNHEALTHY, UNAVAILABLE }

    /** One safe component state. */
    final class ComponentHealth {
        private final String componentId; private final Health health; private final String safeCode;
        /** Creates component health. */ public ComponentHealth(String componentId, Health health, String safeCode) { this.componentId = ApiValidation.text(componentId, "componentId", ApiLimits.MAX_IDENTIFIER_LENGTH); this.health = Objects.requireNonNull(health, "health"); this.safeCode = ApiValidation.text(safeCode, "safeCode", 96); }
        /** Returns component id. */ public String componentId() { return componentId; }
        /** Returns health. */ public Health health() { return health; }
        /** Returns safe code. */ public String safeCode() { return safeCode; }
        @Override public String toString() { return "ComponentHealth{id='" + componentId + "', health=" + health + ", code='" + safeCode + "'}"; }
    }

    /** Immutable bounded health inventory. */
    final class HealthSnapshot {
        private final List<ComponentHealth> components;
        /** Creates a snapshot. */ public HealthSnapshot(List<ComponentHealth> components) { this.components = ApiValidation.immutableList(components, ApiLimits.MAX_DIAGNOSTIC_FIELDS, "components"); }
        /** Returns immutable components. */ public List<ComponentHealth> components() { return components; }
        @Override public String toString() { return "HealthSnapshot{components=" + components.size() + '}'; }
    }

    /** Safe structured diagnostics section. */
    final class DiagnosticsSection {
        private final String id; private final Map<String, String> fields;
        /** Creates an allowlisted bounded section and rejects sensitive field names/values. */
        public DiagnosticsSection(String id, Map<String, String> fields) {
            this.id = ApiValidation.text(id, "section id", ApiLimits.MAX_IDENTIFIER_LENGTH);
            if (fields == null || fields.size() > ApiLimits.MAX_DIAGNOSTIC_FIELDS) throw new IllegalArgumentException("invalid diagnostics fields");
            LinkedHashMap<String, String> copy = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                String key = ApiValidation.text(entry.getKey(), "field key", 64); ApiValidation.rejectSensitiveName(key, "field key");
                String value = ApiValidation.text(entry.getValue(), "field value", ApiLimits.MAX_VALUE_LENGTH);
                if (looksSensitive(value)) throw new IllegalArgumentException("diagnostic value appears sensitive");
                copy.put(key, redactHome(value));
            }
            this.fields = Collections.unmodifiableMap(copy);
        }
        /** Returns section id. */ public String id() { return id; }
        /** Returns immutable fields. */ public Map<String, String> fields() { return fields; }
        @Override public String toString() { return "DiagnosticsSection{id='" + id + "', fieldNames=" + fields.keySet() + '}'; }
        private static boolean looksSensitive(String value) { String lower = value.toLowerCase(Locale.ROOT); return lower.contains("bearer ") || lower.contains("begin openssh private key") || lower.contains("steamdatagram cert") || lower.matches(".*(?:token|ticket|password|secret|cookie|authorization|gslt)=[^\\s]+.*"); }
        private static String redactHome(String value) { String home = System.getProperty("user.home", ""); return home.isEmpty() ? value : value.replace(home, "<user-home>"); }
    }

    /** User-selected inclusion of non-secret personal identifiers. */
    final class PrivacyOptions {
        private final boolean includeSteamIds; private final boolean includeLobbyIds;
        /** Creates options; secrets remain impossible to include. */ public PrivacyOptions(boolean includeSteamIds, boolean includeLobbyIds) { this.includeSteamIds = includeSteamIds; this.includeLobbyIds = includeLobbyIds; }
        /** Returns SteamID inclusion choice. */ public boolean includeSteamIds() { return includeSteamIds; }
        /** Returns lobby id inclusion choice. */ public boolean includeLobbyIds() { return includeLobbyIds; }
    }

    /** Preview that the user can inspect before explicit manual export. */
    final class DoctorPreview {
        private final List<DiagnosticsSection> sections; private final List<String> redactions; private final int encodedBytes;
        /** Creates a bounded preview. */ public DoctorPreview(List<DiagnosticsSection> sections, List<String> redactions, int encodedBytes) { this.sections = ApiValidation.immutableList(sections, 128, "sections"); this.redactions = ApiValidation.immutableList(redactions, 128, "redactions"); if (encodedBytes < 0 || encodedBytes > 2 * 1_048_576) throw new IllegalArgumentException("invalid encodedBytes"); this.encodedBytes = encodedBytes; }
        /** Returns immutable sections. */ public List<DiagnosticsSection> sections() { return sections; }
        /** Returns redaction summary. */ public List<String> redactions() { return redactions; }
        /** Returns preview byte size. */ public int encodedBytes() { return encodedBytes; }
        @Override public String toString() { return "DoctorPreview{sections=" + sections.size() + ", redactions=" + redactions.size() + ", bytes=" + encodedBytes + '}'; }
    }

    /** Time/size-bounded contributor; exceptions are isolated. */
    interface DiagnosticsContributor { /** Returns namespaced id. */ String id(); /** Produces one allowlisted section. */ CompletionStage<ApiResult<DiagnosticsSection>> contribute(); }
}
