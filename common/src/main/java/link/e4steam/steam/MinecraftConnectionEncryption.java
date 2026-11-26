package link.e4steam.steam;

import io.netty.channel.Channel;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Reads vanilla's installed cipher handlers without linking to a removed Minecraft getter. */
public final class MinecraftConnectionEncryption {
    private static final ClassValue<Field> CHANNEL = new ClassValue<Field>() {
        @Override protected Field computeValue(Class<?> type) {
            Field channel = null;
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || !Channel.class.isAssignableFrom(field.getType())) continue;
                    if (channel != null) throw new IllegalStateException("Ambiguous Minecraft connection channel");
                    field.setAccessible(true);
                    channel = field;
                }
            }
            if (channel == null) throw new IllegalStateException("Unsupported Minecraft connection channel");
            return channel;
        }
    };

    private MinecraftConnectionEncryption() { }

    public static boolean isEncrypted(Object connection) {
        if (connection == null) throw new IllegalArgumentException("Minecraft connection is required");
        try {
            Channel channel = (Channel) CHANNEL.get(connection.getClass()).get(connection);
            return channel != null && channel.pipeline().get("decrypt") != null
                    && channel.pipeline().get("encrypt") != null;
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot inspect Minecraft connection encryption", failure);
        }
    }
}
