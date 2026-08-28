package link.e4steam.internal.dedicated;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Strict, bounded parser for the server-owned e4steam dedicated TOML subset. */
public final class DedicatedConfigFile {
    static final int SCHEMA_VERSION = 1;
    private static final long MAX_BYTES = 32L * 1024L;
    private static final int MAX_LINES = 256;
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "schema-version", "enabled", "access-mode", "max-peers", "query-port",
            "server-name", "whitelist", "auth-mode", "publication",
            "ingress-guard", "diagnostics-level", "relay-policy"
    ));

    private DedicatedConfigFile() {
    }

    public static Properties load(Path requested) throws IOException {
        Properties result = new Properties();
        if (requested == null) return result;
        Path path = requested.toAbsolutePath().normalize();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return result;
        BasicFileAttributes before = attributes(path);
        if (!before.isRegularFile() || Files.isSymbolicLink(path)
                || before.size() < 0L || before.size() > MAX_BYTES) {
            throw new IOException("Unsafe dedicated configuration file");
        }
        byte[] encoded = Files.readAllBytes(path);
        BasicFileAttributes after = attributes(path);
        if (encoded.length > MAX_BYTES || !sameFile(before, after)) {
            Arrays.fill(encoded, (byte) 0);
            throw new IOException("Dedicated configuration changed while reading");
        }
        String text = new String(encoded, StandardCharsets.UTF_8);
        Arrays.fill(encoded, (byte) 0);
        Map<String, String> parsed = parse(text);
        validateSentinels(parsed);
        copy(parsed, result, "enabled", "e4steam.dedicated.enabled");
        copy(parsed, result, "access-mode", "e4steam.dedicated.access");
        copy(parsed, result, "max-peers", "e4steam.dedicated.maxPeers");
        copy(parsed, result, "query-port", "e4steam.dedicated.queryPort");
        copy(parsed, result, "server-name", "e4steam.dedicated.name");
        copy(parsed, result, "whitelist", "e4steam.dedicated.whitelist");
        return result;
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean sameFile(BasicFileAttributes before, BasicFileAttributes after) {
        if (!after.isRegularFile() || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())) return false;
        Object firstKey = before.fileKey();
        Object secondKey = after.fileKey();
        return firstKey == null || secondKey == null || firstKey.equals(secondKey);
    }

    private static Map<String, String> parse(String text) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        if (lines.length > MAX_LINES) throw invalid("too many lines");
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < lines.length; index++) {
            String line = stripComment(lines[index]).trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("[") || line.indexOf('=') < 1) {
                throw invalid("unsupported syntax at line " + (index + 1));
            }
            int equals = line.indexOf('=');
            String key = line.substring(0, equals).trim();
            String raw = line.substring(equals + 1).trim();
            if (!key.matches("[a-z][a-z0-9-]{0,47}") || !ALLOWED.contains(key)) {
                throw invalid("unknown field " + safeKey(key));
            }
            if (raw.isEmpty() || values.containsKey(key)) {
                throw invalid("invalid or duplicate field " + key);
            }
            values.put(key, parseValue(key, raw));
        }
        return values;
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (current == '\\' && quoted) {
                escaped = true;
            } else if (current == '"') {
                quoted = !quoted;
            } else if (current == '#' && !quoted) {
                return line.substring(0, index);
            }
        }
        if (quoted || escaped) throw invalid("unterminated string");
        return line;
    }

    private static String parseValue(String key, String raw) {
        if ("whitelist".equals(key)) return parseArray(raw);
        if (raw.startsWith("\"") || raw.endsWith("\"")) return parseString(raw);
        if (raw.matches("-?[0-9]{1,20}")) return raw;
        if ("true".equals(raw) || "false".equals(raw)) return raw;
        throw invalid("invalid value for " + key);
    }

    private static String parseString(String raw) {
        if (raw.length() < 2 || raw.charAt(0) != '"'
                || raw.charAt(raw.length() - 1) != '"') {
            throw invalid("invalid string");
        }
        StringBuilder value = new StringBuilder(raw.length() - 2);
        boolean escaped = false;
        for (int index = 1; index < raw.length() - 1; index++) {
            char current = raw.charAt(index);
            if (escaped) {
                if (current != '"' && current != '\\') {
                    throw invalid("unsupported string escape");
                }
                value.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"' || Character.isISOControl(current)) {
                throw invalid("invalid string character");
            } else {
                value.append(current);
            }
        }
        if (escaped || value.length() > 1_024) throw invalid("invalid string");
        return value.toString();
    }

    private static String parseArray(String raw) {
        if (raw.length() < 2 || raw.charAt(0) != '['
                || raw.charAt(raw.length() - 1) != ']') {
            throw invalid("whitelist must be an array");
        }
        String body = raw.substring(1, raw.length() - 1).trim();
        if (body.isEmpty()) return "";
        String[] entries = body.split(",", -1);
        if (entries.length > 1_024) throw invalid("too many whitelist entries");
        StringBuilder result = new StringBuilder();
        for (String entry : entries) {
            String value = entry.trim();
            if (value.startsWith("\"")) value = parseString(value);
            if (!value.matches("[0-9]{1,20}")) throw invalid("invalid whitelist entry");
            try {
                if (Long.parseUnsignedLong(value) == 0L) throw invalid("invalid whitelist entry");
            } catch (NumberFormatException failure) {
                throw invalid("invalid whitelist entry");
            }
            if (result.length() > 0) result.append(',');
            result.append(value);
        }
        return result.toString();
    }

    private static void validateSentinels(Map<String, String> values) {
        String schema = values.get("schema-version");
        if (schema == null || !Integer.toString(SCHEMA_VERSION).equals(schema)) {
            throw invalid("schema-version must be " + SCHEMA_VERSION);
        }
        require(values, "auth-mode", "ANONYMOUS");
        require(values, "publication", "false");
        require(values, "ingress-guard", "STEAM_ONLY");
        requireOneOf(values, "diagnostics-level", "OFF", "BASIC");
        require(values, "relay-policy", "OFFICIAL_AUTOMATIC");
    }

    private static void require(Map<String, String> values, String key, String expected) {
        String value = values.get(key);
        if (value != null && !expected.equalsIgnoreCase(value)) {
            throw invalid(key + " cannot disable the security baseline");
        }
    }

    private static void requireOneOf(Map<String, String> values, String key, String... allowed) {
        String value = values.get(key);
        if (value == null) return;
        for (String candidate : allowed) {
            if (candidate.equalsIgnoreCase(value)) return;
        }
        throw invalid("unsupported " + key);
    }

    private static void copy(Map<String, String> parsed, Properties target,
                             String source, String destination) {
        String value = parsed.get(source);
        if (value != null && !("whitelist".equals(source) && value.isEmpty())) {
            target.setProperty(destination, value);
        }
    }

    private static String safeKey(String key) {
        return key != null && key.matches("[A-Za-z0-9_-]{1,48}") ? key : "invalid";
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Invalid e4steam dedicated config: " + reason);
    }
}
