package link.e4steam.retro;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Reads the exact Minecraft target embedded into every retro artifact. */
public final class RetroVersion {
    private static final String RESOURCE = "/e4steam-retro.properties";
    private static final String MINECRAFT_VERSION = loadMinecraftVersion();

    private RetroVersion() {
    }

    public static String minecraft() {
        return MINECRAFT_VERSION;
    }

    private static String loadMinecraftVersion() {
        InputStream stream = RetroVersion.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Missing " + RESOURCE + " in the e4steam retro artifact");
        }
        try {
            Properties properties = new Properties();
            properties.load(stream);
            String version = properties.getProperty("minecraftVersion", "").trim();
            if (!version.matches("[0-9]+(?:\\.[0-9]+){1,2}")) {
                throw new IllegalStateException("Invalid retro Minecraft version metadata");
            }
            return version;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read e4steam retro version metadata", exception);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Nothing useful can be done while closing a classpath resource.
            }
        }
    }
}
