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

    private static Properties load(Path file) throws IOException {
        Properties values = new Properties();
        if (Files.exists(file)) {
            if (Files.size(file) > 256 * 1024) throw new IOException("Файл сохранённых адресов слишком большой.");
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
        }
        return values;
    }

    static synchronized SteamNamedAddress.Identity get(String name) throws IOException {
        return get(path(),name);
    }
    static synchronized SteamNamedAddress.Identity get(Path file,String name) throws IOException {
        String value = load(file).getProperty(name);
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
        remember(path(),name,identity);
    }
    /** Called only after a Steam-authenticated lobby join, never directly from search advertisements. */
    static synchronized void remember(Path file,String name, SteamNamedAddress.Identity identity) throws IOException {
        SteamNamedAddress.Identity previous = get(file,name);
        if (previous != null && previous.owner()!=identity.owner()) throw new IOException("Владелец адреса изменился.");
        if (identity.equals(previous)) return;
        Properties values = load(file);
        if (previous==null && values.size() >= 1024) throw new IOException("Достигнут лимит сохранённых адресов.");
        values.setProperty(name, Long.toUnsignedString(identity.owner()) + "/" + identity.world());
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
