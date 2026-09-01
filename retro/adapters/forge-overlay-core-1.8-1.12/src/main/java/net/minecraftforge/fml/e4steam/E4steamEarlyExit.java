package net.minecraftforge.fml.e4steam;

/**
 * Terminates the original overlay-bootstrap JVM without initializing Forge's
 * Loader. Forge 1.8-1.12 permits JVM shutdown only from its own namespace.
 */
public final class E4steamEarlyExit {
    private E4steamEarlyExit() {
    }

    public static void exit(int exitCode) {
        System.exit(exitCode);
    }
}
