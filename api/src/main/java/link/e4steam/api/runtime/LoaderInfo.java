package link.e4steam.api.runtime;

import java.util.Objects;

/** Immutable Minecraft loader id and version snapshot. */
public final class LoaderInfo {
    private final String id;
    private final String version;

    /** Creates one sanitized loader snapshot. */
    public LoaderInfo(String id, String version) {
        this.id = safe(id, "loader id", 32);
        this.version = safe(version, "loader version", 64);
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

    private static String safe(String value, String field, int max) {
        if (value == null) throw new NullPointerException(field);
        String checked = value.trim();
        if (checked.isEmpty() || checked.length() > max) {
            throw new IllegalArgumentException(field + " has an invalid length");
        }
        return checked;
    }
}
