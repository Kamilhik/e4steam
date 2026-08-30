package link.e4steam;

import link.e4steam.api.runtime.RuntimeMode;
import link.e4steam.internal.api.RuntimeEnvironment;

/** Loader-agnostic helpers shared by the Forge and NeoForge entrypoints. */
public final class LoaderSupport {
    private LoaderSupport() {
    }

    public static String versionOf(Class<?> type) {
        String version = type.getPackage() == null
                ? null : type.getPackage().getImplementationVersion();
        return version == null || version.trim().isEmpty() ? "unknown" : version;
    }

    public static void initializeClient(String bootstrapClassName, String loaderVersion) {
        try {
            Class<?> bootstrap = Class.forName(
                    bootstrapClassName, true, LoaderSupport.class.getClassLoader());
            java.lang.reflect.Method initialize =
                    bootstrap.getDeclaredMethod("initialize", String.class);
            initialize.invoke(null, loaderVersion);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not initialize the e4steam client", failure);
        }
    }

    public static RuntimeEnvironment clientEnvironment(String loaderId, String loaderVersion) {
        return new RuntimeEnvironment(loaderId, loaderVersion, MinecraftVersion.current(),
                RuntimeMode.CLIENT, !System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("windows"));
    }
}