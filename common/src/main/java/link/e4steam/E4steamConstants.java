package link.e4steam;

/** Values shared by client and headless entrypoints without loading client code. */
public final class E4steamConstants {
    public static final String MOD_ID = "e4steam";
    private static final String MOD_VERSION = loadModVersion();

    private E4steamConstants() {
    }

    /** Returns the version embedded by the active build without loading a mod-loader API. */
    public static String modVersion() {
        return MOD_VERSION;
    }

    private static String loadModVersion() {
        java.util.Properties properties = new java.util.Properties();
        try (java.io.InputStream input = E4steamConstants.class.getClassLoader()
                .getResourceAsStream("e4steam-version.properties")) {
            if (input != null) {
                properties.load(input);
                String value = properties.getProperty("version", "").trim();
                if (value.matches("[0-9A-Za-z][0-9A-Za-z.+_-]{0,63}")) return value;
            }
        } catch (java.io.IOException ignored) {
        }
        Package owner = E4steamConstants.class.getPackage();
        String implementation = owner == null ? null : owner.getImplementationVersion();
        return implementation == null || implementation.trim().isEmpty()
                ? "unknown" : implementation.trim();
    }
}
