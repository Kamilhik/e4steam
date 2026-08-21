package link.e4steam.retro;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

/**
 * Keeps Minecraft client classes out of Forge's physical-server entrypoint.
 * Client adapters are resolved only after the loader has confirmed CLIENT.
 */
public final class RetroClientLoader {
    private RetroClientLoader() {
    }

    public static void install(String implementationClass) {
        if (implementationClass == null || !implementationClass.startsWith("link.e4steam.")) {
            throw new IllegalArgumentException("implementationClass");
        }
        try {
            ClassLoader loader = RetroClientLoader.class.getClassLoader();
            Class<?> implementation = Class.forName(implementationClass, true, loader);
            Constructor<?> constructor = implementation.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof VirtualMachineError) throw (VirtualMachineError) cause;
            if (cause instanceof ThreadDeath) throw (ThreadDeath) cause;
            throw new IllegalStateException("Could not initialize the e4steam client adapter", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not load the e4steam client adapter", failure);
        } catch (LinkageError failure) {
            throw new IllegalStateException("The e4steam client adapter is incompatible", failure);
        }
    }

    /** Forge 1.13+ distribution check without a verifier-time dependency on client classes. */
    public static boolean isModernForgeClient() {
        try {
            Class<?> environment = Class.forName(
                    "net.minecraftforge.fml.loading.FMLEnvironment", false,
                    RetroClientLoader.class.getClassLoader());
            Field distribution = environment.getField("dist");
            Object value = distribution.get(null);
            return value != null && "CLIENT".equals(String.valueOf(value));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not determine the Forge distribution", failure);
        } catch (LinkageError failure) {
            throw new IllegalStateException("Forge distribution API is unavailable", failure);
        }
    }
}
