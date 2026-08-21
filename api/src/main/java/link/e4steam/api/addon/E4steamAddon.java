package link.e4steam.api.addon;

/** Loader-independent entry point implemented by a trusted installed addon. */
public interface E4steamAddon {
    /**
     * Registers addon resources within a bounded lifecycle callback.
     * Implementations must not block Minecraft or native callback threads.
     */
    void initialize(AddonContext context) throws Exception;
}
