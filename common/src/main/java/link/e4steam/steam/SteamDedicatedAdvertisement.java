package link.e4steam.steam;

import link.e4steam.api.directory.PublicDirectoryService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Bounded public tags survive Steam master results even when direct A2S ping is unavailable. */
final class SteamDedicatedAdvertisement {
    static final String MARKER = "e4s1";
    static String encode(long steamId, long generation, String name, String mc, String loader,
            String core, String addon, int wire, int players, int capacity, long nowMillis) {
        String encoded = "";
        String prefix = MARKER + ",s" + Long.toUnsignedString(steamId, 36)
                + ",g" + Long.toString(generation, 36) + ",m" + mc + ",l" + loader
                + ",c" + core + ",a" + addon + ",w" + wire + ",p" + players + ",x" + capacity
                + ",u" + Long.toString(nowMillis / 1000L, 36) + ",n";
        if (prefix.length() > 119) throw new IllegalArgumentException("advertisement metadata too long");
        String trimmed = name;
        while (!trimmed.isEmpty()) {
            encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(trimmed.getBytes(StandardCharsets.UTF_8));
            if (prefix.length() + encoded.length() <= 127) break;
            trimmed = trimmed.substring(0, trimmed.offsetByCodePoints(trimmed.length(), -1));
        }
        if (trimmed.isEmpty()) throw new IllegalArgumentException("advertisement name too long");
        String tags = prefix + encoded;
        decode(tags, nowMillis); // Validate before publishing any caller-controlled fields.
        return tags;
    }

    static PublicDirectoryService.DedicatedListing decode(String tags, long nowMillis) {
        if (tags == null || tags.length() > 127 || !tags.startsWith(MARKER + ","))
            throw new IllegalArgumentException("invalid advertisement");
        Map<Character,String> fields = new HashMap<>();
        for (String item : tags.substring(MARKER.length() + 1).split(",", -1)) {
            if (item.length() < 2 || fields.put(item.charAt(0), item.substring(1)) != null)
                throw new IllegalArgumentException("duplicate or empty advertisement field");
        }
        if (fields.size() != 11) throw new IllegalArgumentException("missing advertisement fields");
        long updated = Math.multiplyExact(Long.parseLong(required(fields, 'u'), 36), 1000L);
        if (updated > nowMillis + 30_000L || updated < nowMillis - 90_000L)
            throw new IllegalArgumentException("stale advertisement");
        long id = Long.parseUnsignedLong(required(fields, 's'), 36);
        long generation = Long.parseLong(required(fields, 'g'), 36);
        String descriptor = new SteamDedicatedAddress(id, generation).descriptor();
        String target = "ds_" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(descriptor.getBytes(StandardCharsets.US_ASCII));
        byte[] nameBytes = Base64.getUrlDecoder().decode(required(fields, 'n'));
        String name = new String(nameBytes, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(nameBytes, name.getBytes(StandardCharsets.UTF_8)))
            throw new IllegalArgumentException("invalid UTF-8 advertisement");
        return new PublicDirectoryService.DedicatedListing(new PublicDirectoryService.OpaqueTargetRef(target),
                name, required(fields,'m'), required(fields,'l'), required(fields,'c'), required(fields,'a'),
                Integer.parseInt(required(fields,'w')), Integer.parseInt(required(fields,'p')),
                Integer.parseInt(required(fields,'x')), updated);
    }
    private static String required(Map<Character,String> fields, char key) {
        String value = fields.get(key);
        if (value == null) throw new IllegalArgumentException("missing advertisement field");
        return value;
    }
}
