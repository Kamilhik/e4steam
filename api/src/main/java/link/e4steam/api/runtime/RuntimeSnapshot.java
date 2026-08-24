package link.e4steam.api.runtime;

import link.e4steam.api.ApiVersion;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable, privacy-safe snapshot of runtime readiness and compatibility. */
public final class RuntimeSnapshot {
    private final ApiVersion apiVersion;
    private final String modVersion;
    private final int wireVersion;
    private final Platform platform;
    private final Architecture architecture;
    private final RuntimeMode runtimeMode;
    private final LoaderInfo loader;
    private final String minecraftVersion;
    private final SteamRuntimeState steamState;
    private final LifecyclePhase lifecyclePhase;
    private final Set<TransportCapability> transports;
    private final Set<CompatibilityFlag> compatibilityFlags;
    private final String lastFailureCategory;

    /** Creates a defensive runtime snapshot containing no identifiers or filesystem paths. */
    public RuntimeSnapshot(
            ApiVersion apiVersion,
            String modVersion,
            int wireVersion,
            Platform platform,
            Architecture architecture,
            RuntimeMode runtimeMode,
            LoaderInfo loader,
            String minecraftVersion,
            SteamRuntimeState steamState,
            LifecyclePhase lifecyclePhase,
            Set<TransportCapability> transports,
            Set<CompatibilityFlag> compatibilityFlags,
            String lastFailureCategory
    ) {
        this.apiVersion = Objects.requireNonNull(apiVersion, "apiVersion");
        this.modVersion = safe(modVersion, "modVersion", 64, false);
        if (wireVersion < 0) throw new IllegalArgumentException("wireVersion must be non-negative");
        this.wireVersion = wireVersion;
        this.platform = Objects.requireNonNull(platform, "platform");
        this.architecture = Objects.requireNonNull(architecture, "architecture");
        this.runtimeMode = Objects.requireNonNull(runtimeMode, "runtimeMode");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.minecraftVersion = safe(minecraftVersion, "minecraftVersion", 64, false);
        this.steamState = Objects.requireNonNull(steamState, "steamState");
        this.lifecyclePhase = Objects.requireNonNull(lifecyclePhase, "lifecyclePhase");
        this.transports = immutableEnumSet(transports, TransportCapability.class);
        this.compatibilityFlags = immutableEnumSet(compatibilityFlags, CompatibilityFlag.class);
        this.lastFailureCategory = safe(lastFailureCategory, "lastFailureCategory", 64, true);
    }

    /** Returns the Java addon API version. */
    public ApiVersion apiVersion() { return apiVersion; }

    /** Returns the e4steam mod version. */
    public String modVersion() { return modVersion; }

    /** Returns the independent core wire protocol version. */
    public int wireVersion() { return wireVersion; }

    /** Returns the normalized operating system. */
    public Platform platform() { return platform; }

    /** Returns the normalized JVM/native architecture. */
    public Architecture architecture() { return architecture; }

    /** Returns the current process role. */
    public RuntimeMode runtimeMode() { return runtimeMode; }

    /** Returns the loader snapshot. */
    public LoaderInfo loader() { return loader; }

    /** Returns the Minecraft version. */
    public String minecraftVersion() { return minecraftVersion; }

    /** Returns the sanitized Steam state. */
    public SteamRuntimeState steamState() { return steamState; }

    /** Returns the current lifecycle phase. */
    public LifecyclePhase lifecyclePhase() { return lifecyclePhase; }

    /** Returns immutable available transport features. */
    public Set<TransportCapability> transports() { return transports; }

    /** Returns immutable compatibility markers. */
    public Set<CompatibilityFlag> compatibilityFlags() { return compatibilityFlags; }

    /** Returns a sanitized failure category, or an empty string. */
    public String lastFailureCategory() { return lastFailureCategory; }

    @Override
    public String toString() {
        return "RuntimeSnapshot{api=" + apiVersion
                + ", mod='" + modVersion + '\''
                + ", wire=" + wireVersion
                + ", platform=" + platform
                + ", architecture=" + architecture
                + ", mode=" + runtimeMode
                + ", loader=" + loader
                + ", minecraft='" + minecraftVersion + '\''
                + ", steam=" + steamState
                + ", phase=" + lifecyclePhase
                + '}';
    }

    private static String safe(String value, String field, int maximum, boolean optional) {
        if (value == null) throw new NullPointerException(field);
        String checked = value.trim();
        if ((!optional && checked.isEmpty()) || checked.length() > maximum) {
            throw new IllegalArgumentException(field + " has an invalid length");
        }
        return checked;
    }

    private static <E extends Enum<E>> Set<E> immutableEnumSet(Set<E> values, Class<E> type) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) return Collections.emptySet();
        EnumSet<E> copy = EnumSet.noneOf(type);
        copy.addAll(values);
        return Collections.unmodifiableSet(copy);
    }
}
