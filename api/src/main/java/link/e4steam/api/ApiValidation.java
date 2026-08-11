package link.e4steam.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class ApiValidation {
    private ApiValidation() {
    }

    static String text(String value, String field, int maximumLength) {
        if (value == null) {
            throw new NullPointerException(field);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maximumLength) {
            throw new IllegalArgumentException(field + " has an invalid length");
        }
        return trimmed;
    }

    static String identifier(String value, String field, Pattern pattern) {
        String checked = text(value, field, ApiLimits.MAX_IDENTIFIER_LENGTH);
        if (!pattern.matcher(checked).matches()) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return checked;
    }

    static <T> List<T> immutableList(List<T> values, int maximumSize, String field) {
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

    static <T> Set<T> immutableSet(Set<T> values, int maximumSize, String field) {
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
}
