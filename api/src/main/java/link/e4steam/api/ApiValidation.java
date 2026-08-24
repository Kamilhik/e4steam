package link.e4steam.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Shared validation helpers for immutable third-party API values. */
public final class ApiValidation {
    private ApiValidation() {
    }

    /** Returns a trimmed, non-empty, bounded string without control characters. */
    public static String text(String value, String field, int maximumLength) {
        if (value == null) {
            throw new NullPointerException(field);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maximumLength || containsControls(trimmed)) {
            throw new IllegalArgumentException(field + " has an invalid length");
        }
        return trimmed;
    }

    /** Returns a validated identifier matching the supplied stable pattern. */
    public static String identifier(String value, String field, Pattern pattern) {
        String checked = text(value, field, ApiLimits.MAX_IDENTIFIER_LENGTH);
        if (!pattern.matcher(checked).matches()) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return checked;
    }

    /** Returns a defensive bounded immutable list. */
    public static <T> List<T> immutableList(List<T> values, int maximumSize, String field) {
        if (values == null) {
            throw new NullPointerException(field);
        }
        if (values.size() > maximumSize) {
            throw new IllegalArgumentException(field + " exceeds its size limit");
        }
        ArrayList<T> copy = new ArrayList<>(values.size());
        for (T value : values) {
            if (value == null) {
                throw new NullPointerException(field + " contains null");
            }
            copy.add(value);
        }
        return Collections.unmodifiableList(copy);
    }

    /** Returns a defensive bounded immutable set. */
    public static <T> Set<T> immutableSet(Set<T> values, int maximumSize, String field) {
        if (values == null) {
            throw new NullPointerException(field);
        }
        if (values.size() > maximumSize) {
            throw new IllegalArgumentException(field + " exceeds its size limit");
        }
        LinkedHashSet<T> copy = new LinkedHashSet<>();
        for (T value : values) {
            if (value == null) {
                throw new NullPointerException(field + " contains null");
            }
            copy.add(value);
        }
        return Collections.unmodifiableSet(copy);
    }

    /** Returns an empty or validated bounded optional string. */
    public static String optionalText(String value, String field, int maximumLength) {
        if (value == null || value.trim().isEmpty()) return "";
        return text(value, field, maximumLength);
    }

    /** Returns a defensive bounded byte array. */
    public static byte[] bytes(byte[] value, int maximumSize, String field) {
        if (value == null) throw new NullPointerException(field);
        if (value.length > maximumSize) {
            throw new IllegalArgumentException(field + " exceeds its size limit");
        }
        return value.clone();
    }

    /** Rejects names commonly associated with credentials and join secrets. */
    public static void rejectSensitiveName(String value, String field) {
        String normalized = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        String[] forbidden = {"password", "token", "ticket", "secret", "cookie", "authorization", "joinaddress", "gslt"};
        for (String candidate : forbidden) {
            if (normalized.contains(candidate)) {
                throw new IllegalArgumentException(field + " contains a forbidden sensitive name");
            }
        }
    }

    /** Returns whether a text contains disallowed control characters. */
    public static boolean containsControls(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) return true;
        }
        return false;
    }
}
