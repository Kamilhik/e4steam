package link.e4steam.internal.api;

import link.e4steam.Agnos;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.storage.StorageService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Pathless, defensive, quota-bounded and atomically persisted scoped storage. */
final class CoreStorageService implements StorageService {
    private static final int MAGIC = 0x45345344; // E4SD
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_FILE_OVERHEAD = 512;
    private static final long MAX_BYTES = 16L * 1_048_576L;
    private static final int MAX_ENTRIES = 1_000;

    private final CoreCapabilityService capabilities;
    private final Path root;
    private final Map<StorageScope, Map<StorageKey, StoredValue>> data =
            new EnumMap<>(StorageScope.class);
    private final Set<StorageScope> loaded = EnumSet.noneOf(StorageScope.class);

    CoreStorageService(AddonId owner, CoreCapabilityService capabilities) {
        this(capabilities, defaultRoot(owner));
    }

    CoreStorageService(CoreCapabilityService capabilities, Path root) {
        this.capabilities = java.util.Objects.requireNonNull(capabilities, "capabilities");
        this.root = java.util.Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        for (StorageScope scope : StorageScope.values()) data.put(scope, new LinkedHashMap<>());
    }

    @Override public synchronized CompletionStage<ApiResult<StoredValue>> get(
            StorageKey key, StorageScope scope) {
        if (!allowed()) return completed(denied("storage.get"));
        if (key == null || scope == null) return completed(invalid("storage.get"));
        ApiResult<Boolean> ready = load(scope);
        if (!ready.isSuccess()) return completed(ApiResult.failure(ready.error().get()));
        StoredValue value = data.get(scope).get(key);
        return completed(value == null ? SafeApiErrors.failure(ApiErrorCode.UNAVAILABLE,
                "storage.get", "NotFound") : ApiResult.success(copy(value)));
    }

    @Override public synchronized CompletionStage<ApiResult<QuotaSnapshot>> put(
            StorageKey key, StorageScope scope, StoredValue value) {
        if (!allowed()) return completed(denied("storage.put"));
        if (key == null || scope == null || value == null) return completed(invalid("storage.put"));
        ApiResult<Boolean> ready = load(scope);
        if (!ready.isSuccess()) return completed(ApiResult.failure(ready.error().get()));
        Map<StorageKey, StoredValue> map = data.get(scope);
        StoredValue old = map.get(key);
        long bytes = used(map) - (old == null ? 0L : old.size()) + value.size();
        int entries = map.size() + (old == null ? 1 : 0);
        if (bytes > MAX_BYTES || entries > MAX_ENTRIES) return completed(SafeApiErrors.failure(
                ApiErrorCode.QUEUE_FULL, "storage.put", "Quota"));
        try {
            Path directory = scopeDirectory(scope);
            atomicWrite(directory, fileName(key), encode(key, value));
            map.put(key, copy(value));
            return completed(ApiResult.success(quotaValue(map)));
        } catch (IOException failure) {
            return completed(ioFailure("storage.put", failure));
        }
    }

    @Override public synchronized CompletionStage<ApiResult<Boolean>> delete(
            StorageKey key, StorageScope scope) {
        if (!allowed()) return completed(denied("storage.delete"));
        if (key == null || scope == null) return completed(invalid("storage.delete"));
        ApiResult<Boolean> ready = load(scope);
        if (!ready.isSuccess()) return completed(ApiResult.failure(ready.error().get()));
        try {
            Path target = confined(scopeDirectory(scope), fileName(key));
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) requireRegularOwnedFile(target);
            boolean removed = Files.deleteIfExists(target);
            data.get(scope).remove(key);
            return completed(ApiResult.success(removed));
        } catch (IOException failure) {
            return completed(ioFailure("storage.delete", failure));
        }
    }

    @Override public synchronized CompletionStage<ApiResult<List<StorageKey>>> keys(
            StorageScope scope, int limit, String cursor) {
        if (!allowed()) return completed(denied("storage.keys"));
        if (scope == null || limit < 1 || limit > ApiLimits.MAX_PAGE_SIZE) {
            return completed(invalid("storage.keys"));
        }
        final String after;
        try {
            after = cursor == null || cursor.trim().isEmpty() ? "" : new StorageKey(cursor).value();
        } catch (RuntimeException failure) {
            return completed(invalid("storage.keys"));
        }
        ApiResult<Boolean> ready = load(scope);
        if (!ready.isSuccess()) return completed(ApiResult.failure(ready.error().get()));
        ArrayList<StorageKey> values = new ArrayList<>(data.get(scope).keySet());
        values.sort(Comparator.comparing(StorageKey::value));
        ArrayList<StorageKey> page = new ArrayList<>();
        for (StorageKey value : values) {
            if (!after.isEmpty() && value.value().compareTo(after) <= 0) continue;
            page.add(value);
            if (page.size() == limit) break;
        }
        return completed(ApiResult.success(Collections.unmodifiableList(page)));
    }

    @Override public synchronized ApiResult<QuotaSnapshot> quota(StorageScope scope) {
        if (!allowed()) return denied("storage.quota");
        if (scope == null) return invalid("storage.quota");
        ApiResult<Boolean> ready = load(scope);
        return ready.isSuccess() ? ApiResult.success(quotaValue(data.get(scope)))
                : ApiResult.failure(ready.error().get());
    }

    private ApiResult<Boolean> load(StorageScope scope) {
        if (loaded.contains(scope)) return ApiResult.success(Boolean.TRUE);
        Map<StorageKey, StoredValue> values = data.get(scope);
        values.clear();
        try {
            Path directory = scopeDirectory(scope);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.e4s")) {
                for (Path file : files) {
                    if (values.size() >= MAX_ENTRIES) throw new IOException("storage entry limit exceeded");
                    requireRegularOwnedFile(file);
                    long size = Files.size(file);
                    if (size < 1L || size > ApiLimits.MAX_STORAGE_BLOB_BYTES + MAX_FILE_OVERHEAD) {
                        throw new IOException("invalid storage file size");
                    }
                    StoredRecord record = decode(Files.readAllBytes(file));
                    if (!file.getFileName().toString().equals(fileName(record.key))) {
                        throw new IOException("storage filename does not match its key");
                    }
                    if (values.put(record.key, record.value) != null) {
                        throw new IOException("duplicate storage key");
                    }
                }
            }
            if (used(values) > MAX_BYTES) throw new IOException("storage byte quota exceeded");
            loaded.add(scope);
            return ApiResult.success(Boolean.TRUE);
        } catch (IOException | RuntimeException failure) {
            values.clear();
            return ioFailure("storage.load", failure);
        }
    }

    private Path scopeDirectory(StorageScope scope) throws IOException {
        Path parent = ensureOwnedDirectory(root.getParent(), root.getFileName().toString());
        return ensureOwnedDirectory(parent, scope.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Path defaultRoot(AddonId owner) {
        if (owner == null) throw new NullPointerException("owner");
        String safeOwner = owner.value().replace(':', '_');
        return Agnos.configDir().resolve("e4steam-addon-data").resolve(safeOwner).resolve("storage");
    }

    private static Path ensureOwnedDirectory(Path parent, String child) throws IOException {
        if (parent == null) throw new IOException("storage parent is unavailable");
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Files.createDirectories(normalizedParent);
        Path directory = confined(normalizedParent, child);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(directory);
            restrict(directory);
        }
        BasicFileAttributes attributes = Files.readAttributes(
                directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("storage directory is not an owned directory");
        }
        return directory;
    }

    private static void requireRegularOwnedFile(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("storage entry is not a regular file");
        }
        try {
            Object links = Files.getAttribute(file, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
            if (links instanceof Number && ((Number) links).longValue() != 1L) {
                throw new IOException("storage hard links are forbidden");
            }
        } catch (UnsupportedOperationException ignored) {
            // The serialized header and atomic replacement still prevent reading/writing arbitrary files.
        }
    }

    private static Path confined(Path parent, String child) throws IOException {
        Path target = parent.resolve(child).toAbsolutePath().normalize();
        if (!target.startsWith(parent.toAbsolutePath().normalize())) throw new IOException("storage path escaped");
        return target;
    }

    private static byte[] encode(StorageKey key, StoredValue value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.size() + 128);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeByte(FORMAT_VERSION);
            output.writeUTF(key.value());
            output.writeByte(value.format().ordinal());
            output.writeInt(value.schemaVersion());
            byte[] payload = value.bytes();
            output.writeInt(payload.length);
            output.write(payload);
        }
        return bytes.toByteArray();
    }

    private static StoredRecord decode(byte[] encoded) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC || input.readUnsignedByte() != FORMAT_VERSION) {
                throw new IOException("invalid storage header");
            }
            StorageKey key = new StorageKey(input.readUTF());
            int format = input.readUnsignedByte();
            if (format >= StorageFormat.values().length) throw new IOException("invalid storage format");
            int schemaVersion = input.readInt();
            int length = input.readInt();
            if (schemaVersion < 1 || length < 0 || length > ApiLimits.MAX_STORAGE_BLOB_BYTES
                    || length > input.available()) throw new IOException("invalid storage bounds");
            byte[] payload = new byte[length];
            input.readFully(payload);
            if (input.available() != 0) throw new IOException("trailing storage data");
            return new StoredRecord(key, new StoredValue(
                    StorageFormat.values()[format], schemaVersion, payload));
        } catch (IllegalArgumentException failure) {
            throw new IOException("invalid storage record", failure);
        }
    }

    private static void atomicWrite(Path directory, String name, byte[] encoded) throws IOException {
        Path target = confined(directory, name);
        Path temporary = Files.createTempFile(directory, ".e4steam-", ".tmp");
        boolean moved = false;
        try {
            restrict(temporary);
            Files.write(temporary, encoded, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            requireRegularOwnedFile(target);
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static void restrict(Path path) throws IOException {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
            }
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows ACL inheritance is used; no broad permissions are added by e4steam.
        }
    }

    private static String fileName(StorageKey key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.value().getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(68);
            for (byte item : digest) value.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
            return value.append(".e4s").toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private boolean allowed() { return capabilities.has(Capabilities.STORAGE_PRIVATE); }
    private <T> ApiResult<T> denied(String operation) { return SafeApiErrors.failure(
            ApiErrorCode.CAPABILITY_DENIED, operation, "PolicyDenied"); }
    private static <T> ApiResult<T> invalid(String operation) { return SafeApiErrors.failure(
            ApiErrorCode.INVALID_ARGUMENT, operation, "Validation"); }
    private static <T> ApiResult<T> ioFailure(String operation, Throwable failure) {
        return SafeApiErrors.failure(ApiErrorCode.UNAVAILABLE, operation,
                failure instanceof SecurityException ? "StorageSecurity" : "StorageIo");
    }
    private static StoredValue copy(StoredValue value) {
        return new StoredValue(value.format(), value.schemaVersion(), value.bytes());
    }
    private static long used(Map<StorageKey, StoredValue> map) {
        long total = 0L;
        for (StoredValue value : map.values()) total += value.size();
        return total;
    }
    private static QuotaSnapshot quotaValue(Map<StorageKey, StoredValue> map) {
        return new QuotaSnapshot(used(map), MAX_BYTES, map.size(), MAX_ENTRIES);
    }
    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private static final class StoredRecord {
        private final StorageKey key;
        private final StoredValue value;
        private StoredRecord(StorageKey key, StoredValue value) {
            this.key = key;
            this.value = value;
        }
    }
}
