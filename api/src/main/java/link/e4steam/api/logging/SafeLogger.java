package link.e4steam.api.logging;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Structured addon logger that accepts only bounded allowlisted safe fields. */
public interface SafeLogger {
    /** Supported severity independent from an implementation logging framework. */
    enum Level { DEBUG, INFO, WARN, ERROR }

    /** Writes one safe machine-code message; raw throwables and free-form packet dumps are not accepted. */
    ApiResult<Boolean> log(Level level, String messageCode, Map<String, SafeValue> fields);

    /** Immutable safe primitive whose text is validated and redacted at construction. */
    final class SafeValue {
        /** Supported safe value type. */ public enum Type { TEXT, INTEGER, DECIMAL, BOOLEAN }
        private final Type type;
        private final String encoded;
        private SafeValue(Type type, String encoded) { this.type = type; this.encoded = encoded; }
        /** Creates bounded non-sensitive text. */ public static SafeValue text(String value) {
            String checked = ApiValidation.text(value, "log value", 512);
            if (looksSensitive(checked)) throw new IllegalArgumentException("log value appears sensitive");
            return new SafeValue(Type.TEXT, checked);
        }
        /** Creates an integer. */ public static SafeValue integer(long value) { return new SafeValue(Type.INTEGER, Long.toString(value)); }
        /** Creates a finite decimal. */ public static SafeValue decimal(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException("value must be finite");
            return new SafeValue(Type.DECIMAL, Double.toString(value));
        }
        /** Creates a boolean. */ public static SafeValue bool(boolean value) { return new SafeValue(Type.BOOLEAN, Boolean.toString(value)); }
        /** Returns primitive type. */ public Type type() { return type; }
        /** Returns canonical safe encoding. */ public String encoded() { return encoded; }
        @Override public String toString() { return type == Type.TEXT ? "TEXT{redacted-from-toString}" : type + ":" + encoded; }
        private static boolean looksSensitive(String value) {
            String lower = value.toLowerCase(Locale.ROOT);
            return lower.contains("bearer ") || lower.contains("password=") || lower.contains("token=")
                    || lower.contains("ticket=") || lower.contains("secret=") || lower.contains("cookie=")
                    || lower.contains("authorization=") || lower.contains("joinaddress=");
        }
    }

    /** Validates and defensively copies one structured field map. */
    static Map<String, SafeValue> fields(Map<String, SafeValue> fields) {
        if (fields == null || fields.size() > ApiLimits.MAX_DIAGNOSTIC_FIELDS) {
            throw new IllegalArgumentException("invalid safe fields");
        }
        LinkedHashMap<String, SafeValue> copy = new LinkedHashMap<>();
        for (Map.Entry<String, SafeValue> entry : fields.entrySet()) {
            String key = ApiValidation.text(entry.getKey(), "log field", 64);
            ApiValidation.rejectSensitiveName(key, "log field");
            copy.put(key, java.util.Objects.requireNonNull(entry.getValue(), "log value"));
        }
        return Collections.unmodifiableMap(copy);
    }
}
