package link.e4steam.api;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable Semantic Versioning 2.0 value used by the Java addon API. */
public final class ApiVersion implements Comparable<ApiVersion> {
    private static final Pattern SEMVER = Pattern.compile(
            "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
                    + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
                    + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$"
    );

    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String buildMetadata;

    /**
     * Creates a semantic version.
     *
     * @param major non-negative major component
     * @param minor non-negative minor component
     * @param patch non-negative patch component
     * @param preRelease optional pre-release label, or an empty string
     * @param buildMetadata optional build label, or an empty string
     */
    public ApiVersion(
            int major,
            int minor,
            int patch,
            String preRelease,
            String buildMetadata
    ) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version components must be non-negative");
        }
        String text = major + "." + minor + "." + patch
                + (empty(preRelease) ? "" : "-" + preRelease)
                + (empty(buildMetadata) ? "" : "+" + buildMetadata);
        Matcher matcher = SEMVER.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid semantic version");
        }
        validateNumericPrerelease(preRelease);
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = empty(preRelease) ? "" : preRelease;
        this.buildMetadata = empty(buildMetadata) ? "" : buildMetadata;
    }

    /** Parses a strict Semantic Versioning 2.0 string. */
    public static ApiVersion parse(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        Matcher matcher = SEMVER.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid semantic version");
        }
        try {
            return new ApiVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    nullToEmpty(matcher.group(4)),
                    nullToEmpty(matcher.group(5))
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Semantic version component is too large");
        }
    }

    /** Returns the major component. */
    public int major() { return major; }

    /** Returns the minor component. */
    public int minor() { return minor; }

    /** Returns the patch component. */
    public int patch() { return patch; }

    /** Returns the pre-release label, or an empty string. */
    public String preRelease() { return preRelease; }

    /** Returns the build metadata label, or an empty string. */
    public String buildMetadata() { return buildMetadata; }

    /** Returns whether this version has no pre-release label. */
    public boolean isStable() { return preRelease.isEmpty(); }

    /** Returns whether this version has the same major compatibility line. */
    public boolean isSameMajor(ApiVersion other) {
        return other != null && major == other.major;
    }

    @Override
    public int compareTo(ApiVersion other) {
        Objects.requireNonNull(other, "other");
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        if (result == 0) result = Integer.compare(patch, other.patch);
        if (result != 0) return result;
        return comparePrerelease(preRelease, other.preRelease);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ApiVersion)) return false;
        ApiVersion version = (ApiVersion) other;
        return major == version.major
                && minor == version.minor
                && patch == version.patch
                && preRelease.equals(version.preRelease)
                && buildMetadata.equals(version.buildMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease, buildMetadata);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch
                + (preRelease.isEmpty() ? "" : "-" + preRelease)
                + (buildMetadata.isEmpty() ? "" : "+" + buildMetadata);
    }

    private static int comparePrerelease(String left, String right) {
        if (left.isEmpty()) return right.isEmpty() ? 0 : 1;
        if (right.isEmpty()) return -1;
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int count = Math.min(leftParts.length, rightParts.length);
        for (int index = 0; index < count; index++) {
            String a = leftParts[index];
            String b = rightParts[index];
            boolean aNumeric = numeric(a);
            boolean bNumeric = numeric(b);
            int result;
            if (aNumeric && bNumeric) {
                result = compareNumericIdentifiers(a, b);
            } else if (aNumeric != bNumeric) {
                result = aNumeric ? -1 : 1;
            } else {
                result = a.compareTo(b);
            }
            if (result != 0) return result;
        }
        return Integer.compare(leftParts.length, rightParts.length);
    }

    private static int compareNumericIdentifiers(String left, String right) {
        int result = Integer.compare(left.length(), right.length());
        return result == 0 ? left.compareTo(right) : result;
    }

    private static void validateNumericPrerelease(String value) {
        if (empty(value)) return;
        for (String part : value.split("\\.")) {
            if (numeric(part) && part.length() > 1 && part.charAt(0) == '0') {
                throw new IllegalArgumentException("Numeric pre-release identifiers cannot have leading zeroes");
            }
        }
    }

    private static boolean numeric(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) return false;
        }
        return !value.isEmpty();
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
