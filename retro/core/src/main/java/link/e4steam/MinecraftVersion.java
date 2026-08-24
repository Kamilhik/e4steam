package link.e4steam;

/** Actual retro game version advertised in Steam lobby metadata. */
public final class MinecraftVersion {
    private static volatile String current = "retro-uninitialized";

    private MinecraftVersion() {
    }

    public static String current() {
        return current;
    }

    public static void install(String version) {
        if (version == null || !version.matches("[0-9A-Za-z._-]{1,32}")) {
            throw new IllegalArgumentException("Invalid Minecraft version");
        }
        current = version;
    }
}
