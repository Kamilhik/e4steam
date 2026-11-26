package link.e4steam.worldtransfer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.UUID;

/** Reads the actual player UUID across the Entity interface rename on older Forge. */
public final class MinecraftPlayerUuid {
    private static final String[] NAMES={"getUUID","getUuid","method_5667","m_20148_","m_142081_"};
    private static final ClassValue<Optional<Method>> READERS=new ClassValue<Optional<Method>>() {
        @Override protected Optional<Method> computeValue(Class<?> type) {
            for(String name:NAMES) try {
                Method method=type.getMethod(name);
                if(method.getReturnType()!=UUID.class || Modifier.isStatic(method.getModifiers()))continue;
                method.trySetAccessible();
                return Optional.of(method);
            } catch(NoSuchMethodException | SecurityException ignored) { }
            return Optional.empty();
        }
    };
    private MinecraftPlayerUuid() { }
    /** Missing identity disables accepting a handoff; never invent a replacement save UUID. */
    public static UUID read(Object player) {
        if(player==null)return null;
        try {
            Optional<Method> reader=READERS.get(player.getClass());
            return reader.isPresent()?(UUID)reader.get().invoke(player):null;
        } catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }
}
