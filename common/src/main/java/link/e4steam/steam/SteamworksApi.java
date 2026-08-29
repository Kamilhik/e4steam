package link.e4steam.steam;

import com.codedisaster.steamworks.SteamAPI;

/** Production Steam API backed by steamworks4j. */
class SteamworksApi {
    public boolean loadLibraries(SteamNativeLibraryLoader loader) {
        return SteamAPI.loadLibraries(loader);
    }

    public boolean init() throws Exception {
        return SteamAPI.init();
    }

    public boolean isSteamRunning() {
        return SteamAPI.isSteamRunning(true);
    }

    public void runCallbacks() {
        SteamAPI.runCallbacks();
    }

    public void shutdown() {
        SteamAPI.shutdown();
    }
}