package link.e4steam.steam;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/** Adapts Authlib profiles to vanilla's owner-key type without linking to newer Minecraft classes. */
public final class MinecraftOwnerCheck {
    private static final String[] METHODS={"isSingleplayerOwner","method_19466","m_7779_"};
    private MinecraftOwnerCheck() { }

    public static boolean isOwner(Object server,Object profile) {
        if(server==null||profile==null)throw new IllegalArgumentException("Server and player identity are required");
        ReflectiveOperationException last=null;
        for(String name:METHODS)for(Method method:server.getClass().getMethods()) {
            if(!method.getName().equals(name)||method.getParameterCount()!=1||method.getReturnType()!=boolean.class)continue;
            try {
                Object argument=adapt(method.getParameterTypes()[0],profile);
                method.setAccessible(true);
                return (boolean)method.invoke(server,argument);
            } catch(InvocationTargetException failed) {
                throw new IllegalStateException("Minecraft owner check failed",failed.getCause());
            } catch(ReflectiveOperationException unavailable) {last=unavailable;}
        }
        // Unknown APIs must fail closed, never silently turn an authenticated guest into the owner.
        throw new IllegalStateException("Unsupported Minecraft singleplayer-owner identity API",last);
    }

    private static Object adapt(Class<?> expected,Object profile) throws ReflectiveOperationException {
        if(expected.isInstance(profile))return profile;
        // Minecraft 1.21.9+/26.x: NameAndId(GameProfile), including loader-remapped names.
        for(Constructor<?> constructor:expected.getConstructors()) {
            Class<?>[] parameters=constructor.getParameterTypes();
            if(parameters.length==1&&parameters[0].isInstance(profile))return constructor.newInstance(profile);
        }
        // Some loader versions expose only the equivalent UUID/name constructor.
        return expected.getConstructor(UUID.class,String.class).newInstance(
                SteamMinecraftProfile.profileId(profile),SteamMinecraftIdentity.profileName(profile));
    }
}
