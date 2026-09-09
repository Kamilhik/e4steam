package link.e4steam.steam;

/** Replaceable boundary around the process-global Steamworks API. */
interface SteamApi {
    boolean loadLibraries(SteamNativeLibraryLoader loader);

    boolean init() throws Exception;

    /** Returns whether the process-global steamworks4j wrapper is initialized. */
    boolean isInitialized();

    /**
     * Performs Steamworks' native client-process probe.
     *
     * <p>This is deliberately separate from {@link #isInitialized()} because
     * the native probe is not a reliable initialization check in sandboxed
     * launchers.</p>
     */
    boolean isNativeSteamClientRunning();

    void runCallbacks();

    void shutdown();
}
