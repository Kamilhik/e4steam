package link.e4steam.steam;

import link.e4steam.Agnos;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.UUID;

/** Pin names to Steam owner AND world, so a reused name cannot silently replace a map. */
final class SteamNamePins {
    private SteamNamePins() { }
    private static Path path() { return Agnos.configDir().resolve("e4steam/named-worlds.properties"); }

    private static Properties load() throws IOException {
        Properties values = new Properties();
        Path file = path();
        if (Files.exists(file)) {
            if (Files.size(file) > 256 * 1024) throw new IOException("Файл сохранённых адресов слишком большой.");
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
        }
        return values;
    }

    static synchronized SteamNamedAddress.Identity get(String name) throws IOException {
        String value = load().getProperty(name);
        if (value == null) return null;
        try {
            String[] parts = value.split("/", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            return new SteamNamedAddress.Identity(Long.parseUnsignedLong(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Повреждена сохранённая привязка адреса.");
        }
    }

    static synchronized void remember(String name, SteamNamedAddress.Identity identity) throws IOException {
        SteamNamedAddress.Identity previous = get(name);
        if (previous != null && !previous.equals(identity)) throw new IOException("Владелец адреса изменился.");
        if (previous != null) return;
        Properties values = load();
        if (values.size() >= 1024) throw new IOException("Достигнут лимит сохранённых адресов.");
        values.setProperty(name, Long.toUnsignedString(identity.owner()) + "/" + identity.world());
        Path file = path();
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "names-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                values.store(writer, "e4steam name bindings; no endpoint tokens");
            }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
