package link.e4steam.steam;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Credential-free descriptor for an unlisted dedicated e4steam server.
 * The generation makes descriptors from an earlier server process fail closed.
 */
public final class SteamDedicatedAddress {
    private static final Pattern PATTERN = Pattern.compile(
            "^d-([0-9a-z]{1,13})-([0-9a-z]{1,13})\\.steam\\.?$",
            Pattern.CASE_INSENSITIVE
    );

    private final long steamId;
    private final long generation;

    public SteamDedicatedAddress(long steamId, long generation) {
        if (steamId == 0L) throw new IllegalArgumentException("Steam ID must be non-zero");
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        this.steamId = steamId;
        this.generation = generation;
    }

    public long steamId() { return steamId; }

    public long generation() { return generation; }

    public String descriptor() {
        return "d-" + Long.toUnsignedString(steamId, Character.MAX_RADIX)
                + '-' + Long.toUnsignedString(generation, Character.MAX_RADIX) + ".steam";
    }

    public static Optional<SteamDedicatedAddress> tryParse(String value) {
        if (value == null) return Optional.empty();
        Matcher matcher = PATTERN.matcher(value.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) return Optional.empty();
        try {
            long steamId = Long.parseUnsignedLong(matcher.group(1), Character.MAX_RADIX);
            long generation = Long.parseUnsignedLong(matcher.group(2), Character.MAX_RADIX);
            if (steamId == 0L || generation <= 0L) return Optional.empty();
            return Optional.of(new SteamDedicatedAddress(steamId, generation));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    @Override public String toString() {
        return "SteamDedicatedAddress{steamId=" + Long.toUnsignedString(steamId)
                + ", generation=" + Long.toUnsignedString(generation) + '}';
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SteamDedicatedAddress)) return false;
        SteamDedicatedAddress that = (SteamDedicatedAddress) other;
        return steamId == that.steamId && generation == that.generation;
    }

    @Override public int hashCode() {
        return Objects.hash(steamId, generation);
    }
}
