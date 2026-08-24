package link.e4steam;

import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Path;

public final class Agnos {
    private Agnos() {}
    public static boolean isClient() { return true; }
    public static Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    public static Path jarPath() { return configDir(); }
}
