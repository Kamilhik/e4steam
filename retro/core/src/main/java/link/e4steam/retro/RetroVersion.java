package link.e4steam.retro;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves the running Minecraft patch version for a branch-scoped retro artifact. */
public final class RetroVersion {
    private static final String RESOURCE = "/e4steam-retro.properties";
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("(?<![0-9])(1\\.[0-9]+(?:\\.[0-9]+)?)(?![0-9])");
    private static final Metadata METADATA = loadMetadata();
    private static volatile String detectedMinecraftVersion;

    private RetroVersion() {
    }

    /** Actual Minecraft version advertised in Steam lobby metadata. */
    public static String minecraft() {
        String cached = detectedMinecraftVersion;
        if (cached != null) return cached;
        synchronized (RetroVersion.class) {
            cached = detectedMinecraftVersion;
            if (cached != null) return cached;

            String[] candidates = {
                    detectFabricVersion(),
                    detectModernForgeVersion(),
                    detectLegacyForgeVersion()
            };
            String detected = null;
            String firstDetected = null;
            for (String candidate : candidates) {
                if (candidate == null) continue;
                if (firstDetected == null) firstDetected = candidate;
                if (belongsToBranch(candidate, METADATA.branch)) {
                    detected = candidate;
                    break;
                }
            }
            if (detected == null) {
                detected = firstDetected == null ? METADATA.baseline : firstDetected;
            }
            if (!belongsToBranch(detected, METADATA.branch)) {
                throw new IllegalStateException(
                        "This e4steam artifact supports Minecraft " + METADATA.branch
                                + ", but the running game is " + detected);
            }
            detectedMinecraftVersion = detected;
            return detected;
        }
    }

    /** Representative patch version used to compile this artifact. */
    public static String baseline() {
        return METADATA.baseline;
    }

    /** Public compatibility branch, for example {@code 1.12.x}. */
    public static String branch() {
        return METADATA.branch;
    }

    private static String detectFabricVersion() {
        try {
            Class<?> loaderType = Class.forName(
                    "net.fabricmc.loader.api.FabricLoader", false,
                    RetroVersion.class.getClassLoader());
            Object loader = loaderType.getMethod("getInstance").invoke(null);
            Object optionalValue = loaderType
                    .getMethod("getModContainer", String.class)
                    .invoke(loader, "minecraft");
            if (!(optionalValue instanceof Optional)) return null;
            Optional<?> optional = (Optional<?>) optionalValue;
            if (!optional.isPresent()) return null;
            Object metadata = invokeNoArg(optional.get(), "getMetadata");
            Object version = invokeNoArg(metadata, "getVersion");
            return normalizeVersion(invokeNoArg(version, "getFriendlyString"));
        } catch (Throwable failure) {
            rethrowFatal(failure);
            return null;
        }
    }

    private static String detectModernForgeVersion() {
        try {
            Class<?> loaderType = Class.forName(
                    "net.minecraftforge.fml.loading.FMLLoader", false,
                    RetroVersion.class.getClassLoader());
            Object versionInfo = loaderType.getMethod("versionInfo").invoke(null);
            return normalizeVersion(invokeNoArg(versionInfo, "mcVersion"));
        } catch (Throwable failure) {
            rethrowFatal(failure);
            return null;
        }
    }

    private static String detectLegacyForgeVersion() {
        String[] loaderClasses = {
                "net.minecraftforge.fml.common.Loader",
                "cpw.mods.fml.common.Loader"
        };
        for (String loaderClass : loaderClasses) {
            try {
                Class<?> loaderType = Class.forName(
                        loaderClass, false, RetroVersion.class.getClassLoader());
                String fieldVersion = normalizeVersion(readStaticField(loaderType, "MC_VERSION"));
                if (fieldVersion != null) return fieldVersion;
                Object loader = loaderType.getMethod("instance").invoke(null);
                String methodVersion = normalizeVersion(invokeNoArg(loader, "getMCVersionString"));
                if (methodVersion != null) return methodVersion;
            } catch (Throwable failure) {
                rethrowFatal(failure);
            }
        }

        try {
            Class<?> forgeVersion = Class.forName(
                    "net.minecraftforge.common.ForgeVersion", false,
                    RetroVersion.class.getClassLoader());
            return normalizeVersion(readStaticField(forgeVersion, "mcVersion"));
        } catch (Throwable failure) {
            rethrowFatal(failure);
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        try {
            return method.invoke(target);
        } catch (IllegalAccessException inaccessible) {
            method.setAccessible(true);
            return method.invoke(target);
        }
    }

    private static Object readStaticField(Class<?> owner, String fieldName) throws Exception {
        Field field;
        try {
            field = owner.getField(fieldName);
        } catch (NoSuchFieldException missingPublicField) {
            field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
        }
        return field.get(null);
    }

    private static String normalizeVersion(Object value) {
        if (value == null) return null;
        Matcher matcher = VERSION_PATTERN.matcher(String.valueOf(value));
        return matcher.find() ? matcher.group(1) : null;
    }

    static boolean belongsToBranch(String version, String branch) {
        if (version == null || branch == null) return false;
        if (!branch.endsWith(".x")) return branch.equals(version);
        String minor = branch.substring(0, branch.length() - 2);
        return version.equals(minor) || version.startsWith(minor + ".");
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError) throw (VirtualMachineError) failure;
        if (failure instanceof ThreadDeath) throw (ThreadDeath) failure;
    }

    private static Metadata loadMetadata() {
        InputStream stream = RetroVersion.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Missing " + RESOURCE + " in the e4steam retro artifact");
        }
        try {
            Properties properties = new Properties();
            properties.load(stream);
            String baseline = properties.getProperty("minecraftVersion", "").trim();
            String branch = properties.getProperty("minecraftBranch", "").trim();
            if (!baseline.matches("[0-9]+(?:\\.[0-9]+){1,2}")) {
                throw new IllegalStateException("Invalid retro Minecraft build baseline metadata");
            }
            if (!branch.matches("(?:[0-9]+(?:\\.[0-9]+){1,2}|[0-9]+\\.[0-9]+\\.x)")) {
                throw new IllegalStateException("Invalid retro Minecraft branch metadata");
            }
            if (!belongsToBranch(baseline, branch)) {
                throw new IllegalStateException("Retro Minecraft baseline is outside its branch");
            }
            return new Metadata(baseline, branch);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read e4steam retro version metadata", exception);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Nothing useful can be done while closing a classpath resource.
            }
        }
    }

    private static final class Metadata {
        private final String baseline;
        private final String branch;

        private Metadata(String baseline, String branch) {
            this.baseline = baseline;
            this.branch = branch;
        }
    }
}
