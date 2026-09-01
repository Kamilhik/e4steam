package net.minecraftforge.fml.common;

/** Test double for the Forge 1.8-1.12 shutdown contract. */
public final class FMLCommonHandler {
    private static final FMLCommonHandler INSTANCE = new FMLCommonHandler();

    public static int exitCode;
    public static boolean abortive;

    private FMLCommonHandler() {
    }

    public static FMLCommonHandler instance() {
        return INSTANCE;
    }

    public void exitJava(int code, boolean force) {
        exitCode = code;
        abortive = force;
    }

    public static void reset() {
        exitCode = Integer.MIN_VALUE;
        abortive = true;
    }
}
