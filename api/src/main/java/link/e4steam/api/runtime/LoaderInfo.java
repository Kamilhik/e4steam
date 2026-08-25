package link.e4steam.api.runtime;

import link.e4steam.api.ApiValidation;

import java.util.Objects;

/** Immutable Minecraft loader id and version snapshot. */
public final class LoaderInfo {
    private final String id;
    private final String version;

    /** Creates one sanitized loader snapshot. */
    public LoaderInfo(String id, String version) {
        this.id = ApiValidation.text(id, "loader id", 32);
        this.version = ApiValidation.text(version, "loader version", 64);
    }

    /** Returns the lowercase loader id. */
    public String id() { return id; }

    /** Returns the loader version string. */
    public String version() { return version; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LoaderInfo)) return false;
        LoaderInfo info = (LoaderInfo) other;
        return id.equals(info.id) && version.equals(info.version);
    }

    @Override
    public int hashCode() { return Objects.hash(id, version); }

    @Override
    public String toString() { return id + '-' + version; }
}
