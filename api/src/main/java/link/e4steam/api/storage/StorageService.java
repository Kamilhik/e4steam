package link.e4steam.api.storage;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Pathless addon-private storage with quotas and atomic operations. */
public interface StorageService {
    /** Returns a defensive value or a typed not-found result. */ CompletionStage<ApiResult<StoredValue>> get(StorageKey key, StorageScope scope);
    /** Atomically inserts or replaces one value. */ CompletionStage<ApiResult<QuotaSnapshot>> put(StorageKey key, StorageScope scope, StoredValue value);
    /** Atomically deletes one key. */ CompletionStage<ApiResult<Boolean>> delete(StorageKey key, StorageScope scope);
    /** Returns a bounded key page without exposing filesystem paths. */ CompletionStage<ApiResult<List<StorageKey>>> keys(StorageScope scope, int limit, String cursor);
    /** Returns current quota use. */ ApiResult<QuotaSnapshot> quota(StorageScope scope);

    /** Safe storage scope. */ enum StorageScope { GLOBAL, WORLD }
    /** Stored encoding. */ enum StorageFormat { BINARY, UTF8, JSON }

    /** Validated logical key, never a path. */
    final class StorageKey {
        private static final Pattern FORMAT = Pattern.compile("^[a-z0-9][a-z0-9_.-]{0,63}(?:/[a-z0-9][a-z0-9_.-]{0,63}){0,7}$");
        private final String value;
        /** Creates a key that rejects traversal/reserved separators. */ public StorageKey(String value) { this.value = ApiValidation.identifier(value, "storageKey", FORMAT); ApiValidation.rejectSensitiveName(this.value, "storageKey"); if (this.value.contains("..")) throw new IllegalArgumentException("path traversal is forbidden"); }
        /** Returns logical key. */ public String value() { return value; }
        @Override public boolean equals(Object other) { return this == other || other instanceof StorageKey && value.equals(((StorageKey) other).value); }
        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    /** Defensive bounded stored value. */
    final class StoredValue {
        private final StorageFormat format; private final int schemaVersion; private final byte[] bytes;
        /** Creates a stored value. */ public StoredValue(StorageFormat format, int schemaVersion, byte[] bytes) { this.format = Objects.requireNonNull(format, "format"); if (schemaVersion < 1) throw new IllegalArgumentException("invalid schemaVersion"); this.schemaVersion = schemaVersion; this.bytes = ApiValidation.bytes(bytes, ApiLimits.MAX_STORAGE_BLOB_BYTES, "bytes"); }
        /** Returns format. */ public StorageFormat format() { return format; }
        /** Returns version. */ public int schemaVersion() { return schemaVersion; }
        /** Returns defensive bytes. */ public byte[] bytes() { return bytes.clone(); }
        /** Returns byte count. */ public int size() { return bytes.length; }
        @Override public String toString() { return "StoredValue{format=" + format + ", version=" + schemaVersion + ", bytes=" + bytes.length + '}'; }
    }

    /** Quota usage without paths. */
    final class QuotaSnapshot {
        private final long usedBytes; private final long maximumBytes; private final int entries; private final int maximumEntries;
        /** Creates a quota snapshot. */ public QuotaSnapshot(long usedBytes, long maximumBytes, int entries, int maximumEntries) { if (usedBytes < 0 || maximumBytes < 0 || usedBytes > maximumBytes || entries < 0 || maximumEntries < 0 || entries > maximumEntries) throw new IllegalArgumentException("invalid quota"); this.usedBytes = usedBytes; this.maximumBytes = maximumBytes; this.entries = entries; this.maximumEntries = maximumEntries; }
        /** Returns used bytes. */ public long usedBytes() { return usedBytes; }
        /** Returns maximum bytes. */ public long maximumBytes() { return maximumBytes; }
        /** Returns entries. */ public int entries() { return entries; }
        /** Returns maximum entries. */ public int maximumEntries() { return maximumEntries; }
    }
}
