package link.e4steam.internal.api;

import link.e4steam.api.runtime.LoaderInfo;
import link.e4steam.api.runtime.RuntimeMode;

/** Sanitized loader/process values supplied by a loader adapter. */
public final class RuntimeEnvironment {
    private final LoaderInfo loader;
    private final String minecraftVersion;
    private final RuntimeMode mode;
    private final boolean experimental;

    public RuntimeEnvironment(String loaderId, String loaderVersion, String minecraftVersion,
                              RuntimeMode mode, boolean experimental) {
        this.loader = new LoaderInfo(loaderId, loaderVersion);
        this.minecraftVersion = safe(minecraftVersion);
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        this.experimental = experimental;
    }
    public LoaderInfo loader() { return loader; }
    public String minecraftVersion() { return minecraftVersion; }
    public RuntimeMode mode() { return mode; }
    public boolean experimental() { return experimental; }
    private static String safe(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 64) {
            throw new IllegalArgumentException("Invalid Minecraft version");
        }
        return value.trim();
    }
}
