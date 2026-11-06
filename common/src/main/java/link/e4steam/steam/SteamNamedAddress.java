package link.e4steam.steam;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Human-readable names are case-insensitive. They contain no transport credentials. */
public final class SteamNamedAddress {
    private static final java.util.Map<String, String> DISPLAY = new java.util.LinkedHashMap<>();
    private SteamNamedAddress() { }

    public static String normalize(String value) {
        String name = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(".steam")) name = name.substring(0, name.length() - 6);
        if (!name.matches("[a-z0-9][a-z0-9-]{1,30}[a-z0-9]")
                || name.startsWith("s-") || name.startsWith("d-") || name.startsWith("e4steam-")) {
            throw new IllegalArgumentException("Имя: 3–32 латинские буквы, цифры или дефис внутри имени.");
        }
        return name;
    }

    public static Optional<String> tryParse(String address) {
        if (address == null || !address.toLowerCase(Locale.ROOT).endsWith(".steam")) return Optional.empty();
        try { return Optional.of(normalize(address)); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    public static final class Identity {
        private final long owner; private final UUID world;
        public Identity(long owner, UUID world) { this.owner=owner; this.world=world; }
        public long owner() { return owner; } public UUID world() { return world; }
        @Override public boolean equals(Object other) { return other instanceof Identity && owner==((Identity)other).owner && java.util.Objects.equals(world, ((Identity)other).world); }
        @Override public int hashCode() { return java.util.Objects.hash(owner,world); }
    }
    public static final class Target {
        private final long lobby; private final Identity identity; private final String minecraftVersion;
        public Target(long lobby, Identity identity, String minecraftVersion) { this.lobby=lobby; this.identity=identity; this.minecraftVersion=minecraftVersion; }
        public long lobby() { return lobby; } public Identity identity() { return identity; } public String minecraftVersion() { return minecraftVersion; }
        @Override public boolean equals(Object other) { if (!(other instanceof Target)) return false; Target t=(Target)other; return lobby==t.lobby && java.util.Objects.equals(identity,t.identity) && java.util.Objects.equals(minecraftVersion,t.minecraftVersion); }
        @Override public int hashCode() { return java.util.Objects.hash(lobby,identity,minecraftVersion); }
    }

    static boolean acceptSearchOwner(long memberOnlyOwner, long advertisedOwner) {
        return advertisedOwner != 0 && (memberOnlyOwner == 0 || memberOnlyOwner == advertisedOwner);
    }

    static synchronized void rememberDisplay(String endpoint, String name) {
        if (DISPLAY.size() >= 64) DISPLAY.remove(DISPLAY.keySet().iterator().next());
        DISPLAY.put(endpoint, normalize(name) + ".steam");
    }

    public static synchronized String displayFor(String endpoint) {
        return DISPLAY.getOrDefault(endpoint, endpoint);
    }

    static Target choose(List<Target> targets, Identity pinned, String minecraftVersion) throws IOException {
        List<Target> matching = targets.stream()
                .filter(target -> pinned == null || pinned.equals(target.identity())).distinct().collect(java.util.stream.Collectors.toList());
        if (matching.isEmpty()) throw new IOException(pinned == null
                ? "Мир с этим адресом сейчас не открыт." : "Сохранённый мир сейчас недоступен. Другой мир с этим именем не будет выбран.");
        if (matching.size() != 1) throw new IOException("Найдено несколько миров с этим именем. Хозяину нужно выбрать другое имя.");
        Target selected = matching.get(0);
        if (!minecraftVersion.equals(selected.minecraftVersion()))
            throw new IOException("Для этого мира нужен Minecraft " + selected.minecraftVersion());
        return selected;
    }
}
