package link.e4steam;
import cpw.mods.fml.common.Loader;
import java.nio.file.Path;
public final class Agnos { private Agnos(){} public static boolean isClient(){return true;} public static Path configDir(){return Loader.instance().getConfigDir().toPath();} public static Path jarPath(){return configDir();} }
