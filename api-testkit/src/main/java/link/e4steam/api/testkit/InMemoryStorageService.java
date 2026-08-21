package link.e4steam.api.testkit;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Retryability;
import link.e4steam.api.storage.StorageService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Pathless quota-bounded in-memory storage backend for addon tests. */
public final class InMemoryStorageService implements StorageService {
    private static final long MAX_BYTES = 16L * 1_048_576L;
    private static final int MAX_ENTRIES = 1_000;
    private final Map<StorageScope, Map<StorageKey, StoredValue>> data = new EnumMap<>(StorageScope.class);
    private boolean failNextWrite;

    /** Creates empty scopes. */ public InMemoryStorageService() { for (StorageScope scope : StorageScope.values()) data.put(scope, new LinkedHashMap<StorageKey, StoredValue>()); }

    @Override public synchronized CompletionStage<ApiResult<StoredValue>> get(StorageKey key, StorageScope scope) { StoredValue value = map(scope).get(key); return completed(value == null ? failure(ApiErrorCode.UNAVAILABLE, "storage.not_found", "storage.get") : ApiResult.success(new StoredValue(value.format(), value.schemaVersion(), value.bytes()))); }

    @Override
    public synchronized CompletionStage<ApiResult<QuotaSnapshot>> put(StorageKey key, StorageScope scope, StoredValue value) {
        if (key == null || scope == null || value == null) throw new NullPointerException("storage");
        Map<StorageKey, StoredValue> map = map(scope); StoredValue previous = map.get(key);
        long nextBytes = usedBytes(map) - (previous == null ? 0 : previous.size()) + value.size();
        int nextEntries = map.size() + (previous == null ? 1 : 0);
        if (nextBytes > MAX_BYTES || nextEntries > MAX_ENTRIES) return completed(failure(ApiErrorCode.QUEUE_FULL, "storage.quota", "storage.put"));
        if (failNextWrite) { failNextWrite = false; return completed(failure(ApiErrorCode.ADDON_FAILURE, "storage.injected_write_failure", "storage.put")); }
        map.put(key, new StoredValue(value.format(), value.schemaVersion(), value.bytes()));
        return completed(ApiResult.success(quotaValue(map)));
    }

    @Override public synchronized CompletionStage<ApiResult<Boolean>> delete(StorageKey key, StorageScope scope) { return completed(ApiResult.success(Boolean.valueOf(map(scope).remove(key) != null))); }
    @Override public synchronized CompletionStage<ApiResult<List<StorageKey>>> keys(StorageScope scope, int limit, String cursor) { if (limit < 1 || limit > ApiLimits.MAX_PAGE_SIZE) return completed(failure(ApiErrorCode.INVALID_ARGUMENT, "storage.invalid_limit", "storage.keys")); ArrayList<StorageKey> keys = new ArrayList<>(map(scope).keySet()); if (keys.size() > limit) keys = new ArrayList<>(keys.subList(0, limit)); return completed(ApiResult.success(java.util.Collections.unmodifiableList(keys))); }
    @Override public synchronized ApiResult<QuotaSnapshot> quota(StorageScope scope) { return ApiResult.success(quotaValue(map(scope))); }
    /** Makes the next write fail atomically. */ public synchronized void failNextWrite() { failNextWrite = true; }
    private Map<StorageKey, StoredValue> map(StorageScope scope) { if (scope == null) throw new NullPointerException("scope"); return data.get(scope); }
    private static long usedBytes(Map<StorageKey, StoredValue> values) { long bytes = 0L; for (StoredValue value : values.values()) bytes += value.size(); return bytes; }
    private static QuotaSnapshot quotaValue(Map<StorageKey, StoredValue> values) { return new QuotaSnapshot(usedBytes(values), MAX_BYTES, values.size(), MAX_ENTRIES); }
    private static <T> CompletionStage<T> completed(T value) { return CompletableFuture.completedFuture(value); }
    private static <T> ApiResult<T> failure(ApiErrorCode code, String key, String operation) { return ApiResult.failure(new ApiError(code, "e4steam:" + key, Retryability.PERMANENT, operation, "", "testkit")); }
}
