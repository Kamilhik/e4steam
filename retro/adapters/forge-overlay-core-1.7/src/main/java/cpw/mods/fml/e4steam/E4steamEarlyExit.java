package cpw.mods.fml.e4steam;

/**
 * Terminates the original overlay-bootstrap JVM without initializing Forge's
 * Loader. Forge 1.7 permits JVM shutdown only from its own namespace.
 */
public final class E4steamEarlyExit {
    private E4steamEarlyExit() {
    }

    public static void exit(int exitCode) {
        System.exit(exitCode);
    }
}
