package link.e4steam.steam;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

/** Rebinds authenticated identity while retaining Authlib's cosmetic properties. */
public final class SteamMinecraftProfile {
    private SteamMinecraftProfile() { }

    @SuppressWarnings("unchecked")
    public static <T> T bind(T original, long authenticatedSteamId) {
        if (authenticatedSteamId == 0L || original == null) return original;
        UUID uuid = SteamMinecraftIdentity.uuid(authenticatedSteamId);
        String name = SteamMinecraftIdentity.profileName(original);
        Class<?> type = original.getClass();
        try {
            Object properties = properties(original);
            // Authlib 7 is immutable. Pass its PropertyMap to the record constructor.
            for (Constructor<?> constructor : type.getConstructors()) {
                Class<?>[] args = constructor.getParameterTypes();
                if (args.length == 3 && args[0] == UUID.class && args[1] == String.class
                        && properties != null && args[2].isInstance(properties)) {
                    return (T) constructor.newInstance(uuid, name, properties);
                }
            }
            T result = (T) type.getConstructor(UUID.class, String.class).newInstance(uuid, name);
            // Legacy Authlib owns a mutable multimap. Copy values; never replace identity.
            if (properties != null) {
                Object destination = properties(result);
                if (destination != null) {
                    for (Method method : destination.getClass().getMethods()) {
                        if (method.getName().equals("putAll") && method.getParameterTypes().length == 1
                                && method.getParameterTypes()[0].isInstance(properties)) {
                            method.invoke(destination, properties);
                            break;
                        }
                    }
                }
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            // Cosmetic incompatibility must not reject a valid Steam login.
            try {
                return (T) type.getConstructor(UUID.class, String.class).newInstance(uuid, name);
            } catch (ReflectiveOperationException identityFailure) {
                throw new IllegalStateException("Cannot construct authenticated Minecraft profile", identityFailure);
            }
        }
    }

    private static Object properties(Object profile) throws ReflectiveOperationException {
        for (String name : new String[]{"properties", "getProperties"}) {
            try { return profile.getClass().getMethod(name).invoke(profile); }
            catch (NoSuchMethodException ignored) { }
        }
        return null;
    }
}
