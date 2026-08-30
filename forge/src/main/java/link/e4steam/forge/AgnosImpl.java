package link.e4steam.forge;

import link.e4steam.E4steamConstants;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public class AgnosImpl {
    public static boolean isClient() {
        return FMLLoader.getDist().equals(Dist.CLIENT);
    }

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static Path jarPath() {
        return FMLLoader.getLoadingModList().getMods().stream().filter(modInfo -> modInfo.getModId().equals(E4steamConstants.MOD_ID)).findAny().get().getOwningFile().getFile().getFilePath();
    }

    public static boolean autoRestartBrokenSteamSessionForHandshake() {
        return true;
    }
}
