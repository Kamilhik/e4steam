package link.e4steam.steam;

/** Carries one resolver error to the matching vanilla disconnect screen. */
public final class SteamNamedConnectionFailure {
    private static Object connectionScreen;
    private static String reason;
    private SteamNamedConnectionFailure() { }

    public static synchronized void remember(Object screen, String message) {
        connectionScreen = screen;
        reason = message == null ? "Не удалось найти адрес Steam." : message;
    }

    public static synchronized String take(Object currentScreen) {
        String result = connectionScreen == currentScreen ? reason : null;
        connectionScreen = null;
        reason = null;
        return result;
    }
}
