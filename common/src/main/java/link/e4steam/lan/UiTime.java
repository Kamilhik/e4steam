package link.e4steam.lan;

/** Monotonic UI time without depending on Minecraft's changing Util class. */
public final class UiTime {
    private static final long ORIGIN = System.nanoTime();
    private UiTime() { }
    public static long millis() {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - ORIGIN);
    }
}
