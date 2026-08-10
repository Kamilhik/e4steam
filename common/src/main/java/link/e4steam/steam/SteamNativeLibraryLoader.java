package link.e4steam.steam;

import com.codedisaster.steamworks.SteamLibraryLoader;
import link.e4steam.HexCodec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Extracts verified steamworks4j native libraries into an owner-controlled
 * cache. Paths, types, ownership and content are rechecked immediately before
 * each native load; arbitrary working-directory/PATH fallbacks are forbidden.
 */
final class SteamNativeLibraryLoader implements SteamLibraryLoader {
    private static final String CACHE_DIRECTORY = ".e4steam-steam-natives";
    private static final int MAX_NATIVE_LIBRARY_BYTES = 64 * 1024 * 1024;
    private static final Set<String> ALLOWED_LIBRARY_NAMES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "steam_api64.dll",
                    "steamworks4j64.dll",
                    "libsteam_api.so",
                    "libsteamworks4j.so"
            ))
    );

    private final Map<String, VerifiedLibrary> libraries;
    private volatile Throwable failureCause;
    private volatile String failedLibrary;

    SteamNativeLibraryLoader() throws IOException {
        NativeNames names = nativeNames(
                System.getProperty("os.name", ""),
                System.getProperty("os.arch", "")
        );

        byte[] steamApi = readBundledLibrary(names.steamApi());
        byte[] steamworks4j = readBundledLibrary(names.steamworks4j());
        String fingerprint = fingerprint(names, steamApi, steamworks4j);
        Path cache = createCacheDirectory(names.platformDirectory() + "-" + fingerprint);

        VerifiedLibrary steamApiLibrary = materialize(cache, names.steamApi(), steamApi);
        VerifiedLibrary steamworks4jLibrary = materialize(
                cache,
                names.steamworks4j(),
                steamworks4j
        );
        Map<String, VerifiedLibrary> resolvedLibraries = new HashMap<>();
        resolvedLibraries.put("steam_api", steamApiLibrary);
        resolvedLibraries.put("steamworks4j", steamworks4jLibrary);
        libraries = Collections.unmodifiableMap(resolvedLibraries);
    }

    @Override
    public boolean loadLibrary(String libraryName) {
        VerifiedLibrary library = libraries.get(libraryName);
        if (library == null) {
            failedLibrary = "unexpected library";
            failureCause = safeFailure("Steam requested a native library outside the allowlist", null);
            return false;
        }

        try {
            validateForLoad(library);
            System.load(library.path().toString());
            return true;
        } catch (IOException | UnsatisfiedLinkError | SecurityException throwable) {
            failedLibrary = library.path().getFileName().toString();
            failureCause = safeFailure("Verified native library could not be loaded", throwable);
            return false;
        }
    }

    Throwable failureCause() {
        return failureCause;
    }

    String failureDescription() {
        Throwable cause = failureCause;
        if (cause == null) {
            return "unknown native loading error";
        }
        return (failedLibrary == null ? "native library" : failedLibrary)
                + " (" + cause.getMessage() + ")";
    }

    Path steamApiPath() {
        VerifiedLibrary library = libraries.get("steam_api");
        try {
            validateForLoad(library);
        } catch (IOException exception) {
            throw new IllegalStateException("Verified Steam native cache entry became unsafe");
        }
        return library.path();
    }

    static NativeNames nativeNames(String osName, String architecture) throws IOException {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = architecture.toLowerCase(Locale.ROOT);
        if (!(arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64"))) {
            throw new IOException(
                    "Unsupported Steam native architecture '" + architecture
                            + "'. This build requires a 64-bit x86 Java runtime"
            );
        }
        if (os.contains("win")) {
            return new NativeNames("windows-x64", "steam_api64.dll", "steamworks4j64.dll");
        }
        if (os.contains("linux")) {
            return new NativeNames("linux-x64", "libsteam_api.so", "libsteamworks4j.so");
        }
        throw new IOException(
                "Unsupported operating system '" + osName
                        + "'. This build supports Windows x64 and Linux x64"
        );
    }

    private static Path createCacheDirectory(String versionDirectory) throws IOException {
        String userHomeValue = System.getProperty("user.home", "").trim();
        if (userHomeValue.isEmpty()) {
            throw new IOException("A private user home is required for the Steam native cache");
        }
        Path userHome = Paths.get(userHomeValue).toAbsolutePath().normalize();
        validateDirectoryChain(userHome);
        UserPrincipal owner = currentProcessOwner(userHome);
        Path root = ensurePrivateChildDirectory(userHome, CACHE_DIRECTORY, owner);
        validateOwnedDirectory(root, owner);
        return ensurePrivateChildDirectory(root, versionDirectory, owner);
    }

    /** Creates one private cache directory for filesystem-focused tests. */
    static Path createPrivateCacheDirectory(Path parent, String child) throws IOException {
        Path safeParent = parent.toAbsolutePath().normalize();
        validateDirectoryChain(safeParent);
        return ensurePrivateChildDirectory(safeParent, child, currentProcessOwner(safeParent));
    }

    private static Path ensurePrivateChildDirectory(
            Path parent,
            String child,
            UserPrincipal expectedOwner
    ) throws IOException {
        if (child.isEmpty() || child.contains("/") || child.contains("\\")) {
            throw new IOException("Invalid native cache directory name");
        }
        validateDirectoryType(parent);
        Path directory = parent.resolve(child).normalize();
        if (!directory.getParent().equals(parent)) {
            throw new IOException("Native cache directory escaped its owner-controlled parent");
        }
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            validateOwnedDirectory(directory, expectedOwner);
        } else {
            try {
                Files.createDirectory(directory);
            } catch (IOException exception) {
                if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Could not create the private native cache directory");
                }
            }
            validateOwnedDirectory(directory, expectedOwner);
        }
        enforcePrivateDirectoryPermissions(directory);
        validateOwnedDirectory(directory, expectedOwner);
        return directory;
    }

    private static byte[] readBundledLibrary(String resourceName) throws IOException {
        try (InputStream stream = SteamNativeLibraryLoader.class.getResourceAsStream("/" + resourceName)) {
            if (stream == null) {
                throw new IOException("Bundled Steam native library is missing: " + resourceName);
            }
            byte[] content = readBounded(stream, MAX_NATIVE_LIBRARY_BYTES);
            if (content.length == 0) {
                throw new IOException("Bundled Steam native library is empty: " + resourceName);
            }
            return content;
        }
    }

    private static byte[] readBounded(InputStream stream, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            total += read;
            if (total > maximumBytes) {
                throw new IOException("Bundled Steam native library exceeds the size limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    static VerifiedLibrary materialize(Path directory, String fileName, byte[] expected)
            throws IOException {
        if (!ALLOWED_LIBRARY_NAMES.contains(fileName)) {
            throw new IOException("Native library filename is outside the platform allowlist");
        }
        if (expected.length == 0 || expected.length > MAX_NATIVE_LIBRARY_BYTES) {
            throw new IOException("Bundled Steam native library has an invalid size");
        }
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        validateDirectoryChain(normalizedDirectory);
        UserPrincipal owner = readOwner(normalizedDirectory);
        validateOwnedDirectory(normalizedDirectory, owner);

        Path target = normalizedDirectory.resolve(fileName).normalize();
        if (!target.getParent().equals(normalizedDirectory)) {
            throw new IOException("Native library filename escaped the cache directory");
        }
        byte[] expectedHash = sha256(expected);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            validateNativeFile(target, expected.length, expectedHash, owner);
            enforcePrivateFilePermissions(target);
            return new VerifiedLibrary(target, expected.length, expectedHash, owner);
        }

        Path temporary = Files.createTempFile(normalizedDirectory, fileName + ".", ".tmp");
        try {
            validateRegularOwnedFile(temporary, owner);
            writeNoFollow(temporary, expected);
            validateNativeFile(temporary, expected.length, expectedHash, owner);
            enforcePrivateFilePermissions(temporary);
            try {
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, target);
                }
            } catch (IOException exception) {
                if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Could not publish the verified native cache entry");
                }
                // A concurrent Minecraft process may have won the race. Its
                // file is accepted only after the same no-follow validation.
                validateNativeFile(target, expected.length, expectedHash, owner);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }

        validateNativeFile(target, expected.length, expectedHash, owner);
        enforcePrivateFilePermissions(target);
        return new VerifiedLibrary(target, expected.length, expectedHash, owner);
    }

    private static void writeNoFollow(Path target, byte[] content) throws IOException {
        Set<OpenOption> options = new HashSet<>();
        options.add(StandardOpenOption.WRITE);
        options.add(StandardOpenOption.TRUNCATE_EXISTING);
        options.add(LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel channel = Files.newByteChannel(target, options)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }

    static void validateForLoad(VerifiedLibrary library) throws IOException {
        if (library == null) {
            throw new IOException("Native library is unavailable");
        }
        validateDirectoryChain(library.path().getParent());
        validateNativeFile(
                library.path(),
                library.size(),
                library.sha256(),
                library.owner()
        );
    }

    private static void validateDirectoryChain(Path directory) throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        if (current == null) {
            throw new IOException("Native cache path is not absolute");
        }
        validateDirectoryType(current);
        for (Path component : absolute) {
            current = current.resolve(component);
            validateDirectoryType(current);
        }
    }

    private static void validateDirectoryType(Path directory) throws IOException {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(
                    directory,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );
        } catch (IOException exception) {
            throw new IOException("Native cache path contains an unavailable component");
        }
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("Native cache path contains a link or non-directory component");
        }
    }

    private static void validateOwnedDirectory(Path directory, UserPrincipal expectedOwner)
            throws IOException {
        validateDirectoryType(directory);
        validateOwner(directory, expectedOwner);
    }

    private static void validateRegularOwnedFile(Path path, UserPrincipal expectedOwner)
            throws IOException {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(
                    path,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );
        } catch (IOException exception) {
            throw new IOException("Native cache entry could not be inspected safely");
        }
        if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("Native cache entry is not a regular no-follow file");
        }
        validateOwner(path, expectedOwner);
        rejectHardLinks(path);
    }

    private static void validateNativeFile(
            Path path,
            long expectedSize,
            byte[] expectedHash,
            UserPrincipal expectedOwner
    ) throws IOException {
        validateRegularOwnedFile(path, expectedOwner);
        BasicFileAttributes attributes = Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (attributes.size() != expectedSize || expectedSize > MAX_NATIVE_LIBRARY_BYTES) {
            throw new IOException("Native cache entry size does not match the bundled library");
        }
        byte[] actualHash = hashNoFollow(path, expectedSize);
        if (!MessageDigest.isEqual(actualHash, expectedHash)) {
            throw new IOException("Native cache entry hash does not match the bundled library");
        }
        // Recheck metadata after hashing to narrow replacement races.
        validateRegularOwnedFile(path, expectedOwner);
    }

    private static byte[] hashNoFollow(Path path, long expectedSize) throws IOException {
        MessageDigest digest = newSha256();
        Set<OpenOption> options = new HashSet<>();
        options.add(StandardOpenOption.READ);
        options.add(LinkOption.NOFOLLOW_LINKS);
        long total = 0;
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        try (SeekableByteChannel channel = Files.newByteChannel(path, options)) {
            int read;
            while ((read = channel.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                total += read;
                if (total > expectedSize || total > MAX_NATIVE_LIBRARY_BYTES) {
                    throw new IOException("Native cache entry changed while being verified");
                }
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        }
        if (total != expectedSize) {
            throw new IOException("Native cache entry changed while being verified");
        }
        return digest.digest();
    }

    private static void rejectHardLinks(Path path) throws IOException {
        try {
            Object links = Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
            if (links instanceof Number && ((Number) links).longValue() != 1L) {
                throw new IOException("Native cache entry has unexpected hard links");
            }
        } catch (UnsupportedOperationException ignored) {
            // Windows and non-Unix providers do not expose unix:nlink.
        }
    }

    private static UserPrincipal readOwner(Path path) throws IOException {
        try {
            return Files.getOwner(path, LinkOption.NOFOLLOW_LINKS);
        } catch (UnsupportedOperationException ignored) {
            return null;
        }
    }

    private static UserPrincipal currentProcessOwner(Path controlledParent) throws IOException {
        Path probe;
        try {
            probe = Files.createTempFile(controlledParent, ".e4steam-owner-", ".tmp");
        } catch (IOException exception) {
            throw new IOException("Could not verify ownership of the native cache parent");
        }
        try {
            return readOwner(probe);
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    private static void validateOwner(Path path, UserPrincipal expectedOwner) throws IOException {
        if (expectedOwner == null) {
            return;
        }
        UserPrincipal actualOwner = readOwner(path);
        if (actualOwner == null || !expectedOwner.equals(actualOwner)) {
            throw new IOException("Native cache entry is owned by another account");
        }
    }

    private static void enforcePrivateDirectoryPermissions(Path directory) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(
                directory,
                PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (view != null) {
            view.setPermissions(EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            ));
        }
    }

    private static void enforcePrivateFilePermissions(Path target) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(
                target,
                PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (view != null) {
            view.setPermissions(EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            ));
        }
    }

    static IOException safeFailure(String message, Throwable cause) {
        String type = cause == null ? "rejected" : cause.getClass().getSimpleName();
        return new IOException(message + ": " + type);
    }

    private static byte[] sha256(byte[] content) throws IOException {
        MessageDigest digest = newSha256();
        return digest.digest(content);
    }

    private static MessageDigest newSha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable");
        }
    }

    private static String fingerprint(NativeNames names, byte[] steamApi, byte[] steamworks4j)
            throws IOException {
        MessageDigest digest = newSha256();
        digest.update(names.platformDirectory().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        digest.update(steamApi);
        digest.update(steamworks4j);
        return HexCodec.encode(digest.digest(), 0, 12);
    }

    static final class VerifiedLibrary {
        private final Path path;
        private final long size;
        private final byte[] sha256;
        private final UserPrincipal owner;

        private VerifiedLibrary(Path path, long size, byte[] sha256, UserPrincipal owner) {
            this.path = path;
            this.size = size;
            this.sha256 = sha256.clone();
            this.owner = owner;
        }

        Path path() { return path; }
        long size() { return size; }
        byte[] sha256() { return sha256.clone(); }
        UserPrincipal owner() { return owner; }
    }

    static final class NativeNames {
        private final String platformDirectory;
        private final String steamApi;
        private final String steamworks4j;

        NativeNames(String platformDirectory, String steamApi, String steamworks4j) {
            this.platformDirectory = platformDirectory;
            this.steamApi = steamApi;
            this.steamworks4j = steamworks4j;
        }

        String platformDirectory() { return platformDirectory; }
        String steamApi() { return steamApi; }
        String steamworks4j() { return steamworks4j; }
    }
}
