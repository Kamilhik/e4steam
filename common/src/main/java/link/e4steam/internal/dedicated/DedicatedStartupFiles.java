package link.e4steam.internal.dedicated;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Properties;

/** Physical-server startup only; a disabled backend never edits Minecraft properties. */
public final class DedicatedStartupFiles {
    private static final long MAX_PROPERTIES_BYTES = 128 * 1024;
    private static final String DEFAULTS =
            "# e4steam dedicated server (Minecraft 1.7.10-26.3)\n"
            + "# Steam-only mode starts automatically. Set enabled to false to opt out.\n"
            + "# Explicit E4STEAM_DEDICATED_* variables override this file; JVM properties override both.\n"
            + "# When enabled: Minecraft binds to localhost; direct IP, query and RCON are disabled.\n"
            + "# UNLISTED accepts authenticated Steam players who know the d-...steam address.\n"
            + "# Use PRIVATE or WHITELIST and fill whitelist to restrict players.\n"
            + "schema-version = 1\n"
            + "enabled = true\n"
            + "access-mode = \"UNLISTED\"\n"
            + "max-peers = 8\n"
            + "query-port = 0\n"
            + "server-name = \"e4steam Minecraft server\"\n"
            + "whitelist = []\n"
            + "auth-mode = \"ANONYMOUS\"\n"
            + "# Set true to allow a Public Worlds addon to advertise this server anonymously.\n"
            + "publication = false\n"
            + "ingress-guard = \"STEAM_ONLY\"\n"
            + "diagnostics-level = \"BASIC\"\n"
            + "relay-policy = \"OFFICIAL_AUTOMATIC\"\n";

    private DedicatedStartupFiles() { }

    public static Path ensureDefaults(Path serverDirectory) throws IOException {
        Path root = serverDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IOException("Missing Minecraft server directory");
        Path config = root.resolve("config");
        Files.createDirectories(config);
        if (!Files.isDirectory(config, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Dedicated config directory must not be a symbolic link");
        }
        Path file = config.resolve("e4steam-dedicated.toml");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            try {
                writeNewPrivate(file, DEFAULTS.getBytes(StandardCharsets.UTF_8));
            } catch (FileAlreadyExistsException concurrentCreation) {
                // The strict config reader validates the winner; never overwrite it.
            }
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Dedicated config must be a regular file");
        }
        return file;
    }

    public static boolean prepareMinecraft(Path serverDirectory, boolean enabled) throws IOException {
        if (!enabled) return false;
        Path root = serverDirectory.toAbsolutePath().normalize();
        Path file = root.resolve("server.properties");
        byte[] before = readProperties(file);
        String original = new String(before, StandardCharsets.ISO_8859_1);
        Properties parsed = parse(original);
        if (DedicatedServerPropertiesValidator.validate(parsed).allowed()) return false;
        String bind = parsed.getProperty("server-ip", "").trim();
        // Retain explicit IPv6 loopback, otherwise use the portable IPv4 loopback.
        String replacement = "::1".equals(bind) ? "::1" : "127.0.0.1";
        String result = original + "\n\n# e4steam Steam-only ingress (original saved in config/e4steam-dedicated/)\n"
                + "server-ip=" + replacement + "\nenable-query=false\nenable-rcon=false\n";
        Properties verified = parse(result);
        if (!DedicatedServerPropertiesValidator.validate(verified).allowed()) {
            throw new IOException("Cannot prepare Steam-only Minecraft ingress");
        }
        for (String key : parsed.stringPropertyNames()) {
            if (!key.equals("server-ip") && !key.equals("enable-query") && !key.equals("enable-rcon")
                    && !parsed.getProperty(key).equals(verified.getProperty(key))) {
                throw new IOException("Preparing ingress would change an unrelated Minecraft property");
            }
        }
        byte[] after = result.getBytes(StandardCharsets.ISO_8859_1);
        if (after.length > MAX_PROPERTIES_BYTES) throw new IOException("Minecraft properties exceed size limit");
        Path backupDirectory = root.resolve("config").resolve("e4steam-dedicated");
        Files.createDirectories(root.resolve("config"));
        if (!Files.isDirectory(root.resolve("config"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Dedicated config directory must not be a symbolic link");
        }
        Files.createDirectories(backupDirectory);
        if (!Files.isDirectory(root.resolve("config"), LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(backupDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Dedicated backup directory must not be a symbolic link");
        }
        Path backup = backupDirectory.resolve("server.properties.before-steam-only");
        try {
            writeNewPrivate(backup, before);
        } catch (FileAlreadyExistsException alreadyBackedUp) {
            if (!Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Dedicated backup must be a regular file");
            }
        }
        Path pending = Files.createTempFile(root, ".e4steam-properties-", ".tmp");
        try {
            privatePermissions(pending);
            Files.write(pending, after);
            if (!Arrays.equals(before, readProperties(file))) {
                throw new IOException("Minecraft properties changed during dedicated setup; retry startup");
            }
            try {
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(pending);
        }
        return true;
    }

    private static byte[] readProperties(Path file) throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return new byte[0];
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_PROPERTIES_BYTES) {
            throw new IOException("Missing or unsafe Minecraft server.properties");
        }
        byte[] data = Files.readAllBytes(file);
        if (data.length > MAX_PROPERTIES_BYTES) throw new IOException("Minecraft properties exceed size limit");
        return data;
    }

    private static Properties parse(String text) throws IOException {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Malformed Minecraft server.properties", invalid);
        }
        return properties;
    }

    private static void writeNewPrivate(Path file, byte[] contents) throws IOException {
        if (Files.getFileStore(file.getParent()).supportsFileAttributeView("posix")) {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.write(file, contents);
        } else {
            Files.write(file, contents, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
    }

    private static void privatePermissions(Path file) throws IOException {
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }
}
