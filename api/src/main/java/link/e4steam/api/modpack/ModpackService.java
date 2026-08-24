package link.e4steam.api.modpack;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;
import link.e4steam.api.Registration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Safe contracts for optional modpack addons; core never downloads or hot-loads JARs. */
public interface ModpackService {
    /** Registers an external manifest/analysis/staging provider. */ ApiResult<Registration> registerProvider(ModpackProvider provider);
    /** Inspects a bounded manifest without installation. */ CompletionStage<ApiResult<CompatibilityReport>> inspect(ModpackManifest manifest, Environment environment);
    /** Builds a user-confirmable staging/restart/rollback plan. */ CompletionStage<ApiResult<InstallPlan>> plan(ModpackManifest manifest, CompatibilityReport report);

    /** Entry side applicability. */ enum Side { REQUIRED, OPTIONAL, CLIENT_ONLY, SERVER_ONLY }
    /** Compatibility status. */ enum CompatibilityStatus { COMPATIBLE, CHANGES_REQUIRED, INCOMPATIBLE, INVALID }
    /** Planned action. */ enum InstallAction { KEEP, STAGE_ADD, STAGE_UPDATE, STAGE_REMOVE_WITH_BACKUP }

    /** Modpack id and semantic version. */
    final class ModpackId {
        private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9_.-]{0,63}$");
        private final String id; private final String version;
        /** Creates an id. */ public ModpackId(String id, String version) { this.id = ApiValidation.identifier(id, "modpackId", FORMAT); this.version = ApiValidation.text(version, "version", 64); }
        /** Returns id. */ public String id() { return id; }
        /** Returns version. */ public String version() { return version; }
        @Override public String toString() { return id + '@' + version; }
    }

    /** HTTPS artifact source descriptor without credentials. */
    final class ArtifactSource {
        private final String uri; private final long expectedBytes;
        /** Creates a source. */ public ArtifactSource(String uri, long expectedBytes) { this.uri = validateUri(uri); if (expectedBytes < 1 || expectedBytes > 512L * 1_048_576L) throw new IllegalArgumentException("invalid expectedBytes"); this.expectedBytes = expectedBytes; }
        /** Returns credential-free HTTPS URI. */ public String uri() { return uri; }
        /** Returns maximum expected bytes. */ public long expectedBytes() { return expectedBytes; }
        @Override public String toString() { return "ArtifactSource{hostOnly, bytes=" + expectedBytes + '}'; }
        private static String validateUri(String value) { String checked = ApiValidation.text(value, "uri", 2_048); try { URI parsed = new URI(checked); if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getUserInfo() != null || parsed.getHost() == null || parsed.getRawQuery() != null) throw new IllegalArgumentException("artifact URI must be credential-free HTTPS without query"); } catch (URISyntaxException exception) { throw new IllegalArgumentException("invalid artifact URI"); } return checked; }
    }

    /** One manifest entry with pathless logical filename and SHA-256. */
    final class ModEntry {
        private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9_.-]{0,95}$");
        private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
        private final String modId; private final String version; private final Side side; private final String sha256; private final ArtifactSource source;
        /** Creates an entry. */ public ModEntry(String modId, String version, Side side, String sha256, ArtifactSource source) { this.modId = ApiValidation.identifier(modId, "modId", ID); this.version = ApiValidation.text(version, "version", 64); this.side = Objects.requireNonNull(side, "side"); this.sha256 = ApiValidation.identifier(sha256, "sha256", SHA256); this.source = Objects.requireNonNull(source, "source"); }
        /** Returns mod id. */ public String modId() { return modId; }
        /** Returns version. */ public String version() { return version; }
        /** Returns side. */ public Side side() { return side; }
        /** Returns SHA-256. */ public String sha256() { return sha256; }
        /** Returns source. */ public ArtifactSource source() { return source; }
        @Override public String toString() { return "ModEntry{id='" + modId + "', version='" + version + "', sha256=present}"; }
    }

    /** Loader/Minecraft environment constraint. */
    final class Environment {
        private final String minecraftVersion; private final String loaderId; private final String loaderVersion;
        /** Creates an environment. */ public Environment(String minecraftVersion, String loaderId, String loaderVersion) { this.minecraftVersion = ApiValidation.text(minecraftVersion, "minecraftVersion", 64); this.loaderId = ApiValidation.text(loaderId, "loaderId", 32); this.loaderVersion = ApiValidation.text(loaderVersion, "loaderVersion", 64); }
        /** Returns Minecraft version. */ public String minecraftVersion() { return minecraftVersion; }
        /** Returns loader id. */ public String loaderId() { return loaderId; }
        /** Returns loader version. */ public String loaderVersion() { return loaderVersion; }
    }

    /** Bounded content-free manifest. */
    final class ModpackManifest {
        private final int schemaVersion; private final ModpackId id; private final Environment requiredEnvironment; private final List<ModEntry> entries; private final String signatureMetadata;
        /** Creates a manifest. */ public ModpackManifest(int schemaVersion, ModpackId id, Environment requiredEnvironment, List<ModEntry> entries, String signatureMetadata) { if (schemaVersion < 1 || schemaVersion > 64) throw new IllegalArgumentException("invalid schemaVersion"); this.schemaVersion = schemaVersion; this.id = Objects.requireNonNull(id, "id"); this.requiredEnvironment = Objects.requireNonNull(requiredEnvironment, "requiredEnvironment"); this.entries = ApiValidation.immutableList(entries, ApiLimits.MAX_MODPACK_ENTRIES, "entries"); java.util.HashSet<String> ids = new java.util.HashSet<>(); for (ModEntry entry : this.entries) if (!ids.add(entry.modId())) throw new IllegalArgumentException("duplicate manifest entry"); this.signatureMetadata = ApiValidation.optionalText(signatureMetadata, "signatureMetadata", 512); ApiValidation.rejectSensitiveName(this.signatureMetadata, "signatureMetadata"); }
        /** Returns schema version. */ public int schemaVersion() { return schemaVersion; }
        /** Returns id. */ public ModpackId id() { return id; }
        /** Returns required environment. */ public Environment requiredEnvironment() { return requiredEnvironment; }
        /** Returns immutable entries. */ public List<ModEntry> entries() { return entries; }
        /** Returns optional signature metadata, never a private key. */ public String signatureMetadata() { return signatureMetadata; }
        @Override public String toString() { return "ModpackManifest{id=" + id + ", entries=" + entries.size() + '}'; }
    }

    /** Immutable compatibility report. */
    final class CompatibilityReport {
        private final CompatibilityStatus status; private final List<String> safeCodes;
        /** Creates a report. */ public CompatibilityReport(CompatibilityStatus status, List<String> safeCodes) { this.status = Objects.requireNonNull(status, "status"); this.safeCodes = ApiValidation.immutableList(safeCodes, 512, "safeCodes"); }
        /** Returns status. */ public CompatibilityStatus status() { return status; }
        /** Returns safe machine-readable codes. */ public List<String> safeCodes() { return safeCodes; }
    }

    /** One user-visible staged action. */
    final class PlannedEntry {
        private final String modId; private final InstallAction action; private final long bytes;
        /** Creates an action. */ public PlannedEntry(String modId, InstallAction action, long bytes) { this.modId = ApiValidation.text(modId, "modId", 96); this.action = Objects.requireNonNull(action, "action"); if (bytes < 0) throw new IllegalArgumentException("invalid bytes"); this.bytes = bytes; }
        /** Returns mod id. */ public String modId() { return modId; }
        /** Returns action. */ public InstallAction action() { return action; }
        /** Returns expected bytes. */ public long bytes() { return bytes; }
    }

    /** Immutable staging plan; applying it remains an external addon/user action before restart. */
    final class InstallPlan {
        private final String planId; private final List<PlannedEntry> entries; private final boolean restartRequired; private final boolean rollbackAvailable;
        /** Creates a plan. */ public InstallPlan(String planId, List<PlannedEntry> entries, boolean restartRequired, boolean rollbackAvailable) { this.planId = ApiValidation.text(planId, "planId", 96); ApiValidation.rejectSensitiveName(this.planId, "planId"); this.entries = ApiValidation.immutableList(entries, ApiLimits.MAX_MODPACK_ENTRIES, "entries"); this.restartRequired = restartRequired; this.rollbackAvailable = rollbackAvailable; }
        /** Returns opaque plan id. */ public String planId() { return planId; }
        /** Returns staged actions. */ public List<PlannedEntry> entries() { return entries; }
        /** Returns restart requirement. */ public boolean restartRequired() { return restartRequired; }
        /** Returns rollback availability. */ public boolean rollbackAvailable() { return rollbackAvailable; }
        @Override public String toString() { return "InstallPlan{id='" + planId + "', entries=" + entries.size() + ", restart=" + restartRequired + '}'; }
    }

    /** External provider contract; no method can hot-load or execute an artifact. */
    interface ModpackProvider {
        /** Returns namespaced provider id. */ String id();
        /** Inspects compatibility. */ CompletionStage<ApiResult<CompatibilityReport>> inspect(ModpackManifest manifest, Environment environment);
        /** Builds a staging-only plan. */ CompletionStage<ApiResult<InstallPlan>> plan(ModpackManifest manifest, CompatibilityReport report);
    }
}
