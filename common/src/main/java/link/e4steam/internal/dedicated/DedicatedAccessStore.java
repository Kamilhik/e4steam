package link.e4steam.internal.dedicated;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Owner-local, bounded SteamID access policy used by headless administration. */
final class DedicatedAccessStore {
    private static final int MAX_ENTRIES = 4_096;
    private final Object lock = new Object();
    private final Path path;
    private final LinkedHashSet<Long> whitelist = new LinkedHashSet<>();
    private final LinkedHashSet<Long> bans = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> whitelistUuids = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> bannedUuids = new LinkedHashSet<>();

    DedicatedAccessStore(Path path) {
        this.path = java.util.Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        load();
    }

    boolean whitelisted(long steamId) {
        synchronized (lock) { return whitelist.contains(requireSteamId(steamId)); }
    }

    boolean banned(long steamId) {
        synchronized (lock) { return bans.contains(requireSteamId(steamId)); }
    }

    boolean whitelisted(UUID uuid) {
        synchronized (lock) { return whitelistUuids.contains(requireUuid(uuid)); }
    }

    boolean banned(UUID uuid) {
        synchronized (lock) { return bannedUuids.contains(requireUuid(uuid)); }
    }

    boolean setWhitelisted(long steamId, boolean present) {
        synchronized (lock) {
            boolean changed = update(whitelist, requireSteamId(steamId), present);
            if (changed) save();
            return changed;
        }
    }

    boolean setBanned(long steamId, boolean present) {
        synchronized (lock) {
            boolean changed = update(bans, requireSteamId(steamId), present);
            if (changed) save();
            return changed;
        }
    }

    boolean setWhitelisted(UUID uuid, boolean present) {
        synchronized (lock) {
            boolean changed = update(whitelistUuids, requireUuid(uuid), present);
            if (changed) save();
            return changed;
        }
    }

    boolean setBanned(UUID uuid, boolean present) {
        synchronized (lock) {
            boolean changed = update(bannedUuids, requireUuid(uuid), present);
            if (changed) save();
            return changed;
        }
    }

    int whitelistSize() { synchronized (lock) { return whitelist.size(); } }
    int banSize() { synchronized (lock) { return bans.size(); } }

    private void load() {
        synchronized (lock) {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
            ensureSafeRegularFile(path);
            try {
                List<String> lines = Files.readAllLines(path, StandardCharsets.US_ASCII);
                if (lines.size() > MAX_ENTRIES * 4 + 16) {
                    throw new IllegalStateException("Dedicated access file is too large");
                }
                for (String line : lines) {
                    String value = line.trim();
                    if (value.isEmpty() || value.startsWith("#")) continue;
                    int separator = value.indexOf('=');
                    if (separator < 1 || separator == value.length() - 1) {
                        throw new IllegalStateException("Invalid dedicated access entry");
                    }
                    String kind = value.substring(0, separator);
                    String identity = value.substring(separator + 1);
                    if ("allow".equals(kind) || "ban".equals(kind)) {
                        long steamId;
                        try { steamId = Long.parseUnsignedLong(identity); }
                        catch (NumberFormatException failure) {
                            throw new IllegalStateException("Invalid dedicated access SteamID", failure);
                        }
                        requireSteamId(steamId);
                        Set<Long> target = "allow".equals(kind) ? whitelist : bans;
                        addLoaded(target, steamId);
                    } else if ("allow-uuid".equals(kind) || "ban-uuid".equals(kind)) {
                        UUID uuid;
                        try { uuid = UUID.fromString(identity); }
                        catch (IllegalArgumentException failure) {
                            throw new IllegalStateException("Invalid dedicated access UUID", failure);
                        }
                        Set<UUID> target = "allow-uuid".equals(kind)
                                ? whitelistUuids : bannedUuids;
                        addLoaded(target, uuid);
                    } else {
                        throw new IllegalStateException("Unknown dedicated access entry");
                    }
                }
            } catch (IOException failure) {
                throw new IllegalStateException("Could not read dedicated access policy", failure);
            }
        }
    }

    private void save() {
        Path parent = path.getParent();
        if (parent == null) throw new IllegalStateException("Dedicated access path has no parent");
        try {
            Files.createDirectories(parent);
            if (Files.isSymbolicLink(parent)) {
                throw new SecurityException("Dedicated access directory cannot be a symbolic link");
            }
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) ensureSafeRegularFile(path);
            Path temporary = Files.createTempFile(parent, ".e4steam-access-", ".tmp");
            try {
                applyOwnerOnlyPermissions(temporary);
                java.util.ArrayList<String> lines = new java.util.ArrayList<>();
                lines.add("# e4steam dedicated access policy v1");
                for (Long value : whitelist) lines.add("allow=" + Long.toUnsignedString(value));
                for (Long value : bans) lines.add("ban=" + Long.toUnsignedString(value));
                for (UUID value : whitelistUuids) lines.add("allow-uuid=" + value);
                for (UUID value : bannedUuids) lines.add("ban-uuid=" + value);
                Files.write(temporary, lines, StandardCharsets.US_ASCII,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
                applyOwnerOnlyPermissions(path);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not persist dedicated access policy", failure);
        }
    }

    private static <T> boolean update(Set<T> values, T identity, boolean present) {
        if (present) {
            if (values.size() >= MAX_ENTRIES && !values.contains(identity)) {
                throw new IllegalStateException("Dedicated access policy is full");
            }
            return values.add(identity);
        }
        return values.remove(identity);
    }

    private static <T> void addLoaded(Set<T> target, T value) {
        if (target.size() >= MAX_ENTRIES || !target.add(value)) {
            throw new IllegalStateException("Duplicate or excessive dedicated access entry");
        }
    }

    private static long requireSteamId(long value) {
        if (value == 0L) throw new IllegalArgumentException("SteamID must be non-zero");
        return value;
    }

    private static UUID requireUuid(UUID value) {
        return java.util.Objects.requireNonNull(value, "uuid");
    }

    private static void ensureSafeRegularFile(Path value) {
        if (Files.isSymbolicLink(value)
                || !Files.isRegularFile(value, LinkOption.NOFOLLOW_LINKS)) {
            throw new SecurityException("Dedicated access policy must be a regular file");
        }
    }

    private static void applyOwnerOnlyPermissions(Path value) {
        try {
            Files.setPosixFilePermissions(value, new LinkedHashSet<>(java.util.Arrays.asList(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows ACL ownership is inherited from the user-owned config directory.
        }
    }
}
