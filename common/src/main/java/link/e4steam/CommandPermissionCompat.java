package link.e4steam;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Bridges the command permission API replaced in Minecraft 26.1. */
final class CommandPermissionCompat {
    private CommandPermissionCompat() {
    }

    static boolean hasPermission(Object source, int level) {
        if (source == null) return false;
        if (level <= 0) return true;

        Boolean legacy = invokeLegacyPermission(source, level);
        if (legacy != null) return legacy;

        Boolean modern = invokeModernPermission(source, level);
        if (modern != null) return modern;
        return false;
    }

    private static Boolean invokeLegacyPermission(Object source, int level) {
        try {
            Method method = source.getClass().getMethod("hasPermission", int.class);
            return (Boolean) method.invoke(source, level);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException | ClassCastException failure) {
            return false;
        }
    }

    private static Boolean invokeModernPermission(Object source, int level) {
        Object permissionSet = invokeNoArgs(source, "permissions");
        if (permissionSet == null) return null;

        try {
            ClassLoader loader = source.getClass().getClassLoader();
            Class<?> permissionsClass = Class.forName(
                    "net.minecraft.server.permissions.Permissions", true, loader);
            String fieldName;
            if (level >= 4) {
                fieldName = "COMMANDS_OWNER";
            } else if (level == 3) {
                fieldName = "COMMANDS_ADMIN";
            } else if (level == 2) {
                fieldName = "COMMANDS_GAMEMASTER";
            } else {
                fieldName = "COMMANDS_MODERATOR";
            }
            Field field = permissionsClass.getField(fieldName);
            Object permission = field.get(null);
            for (Method method : permissionSet.getClass().getMethods()) {
                if (!method.getName().equals("hasPermission")
                        || method.getParameterCount() != 1
                        || !method.getParameterTypes()[0].isInstance(permission)) {
                    continue;
                }
                return (Boolean) method.invoke(permissionSet, permission);
            }
            return null;
        } catch (ClassNotFoundException | NoSuchFieldException ignored) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException | ClassCastException failure) {
            return false;
        }
    }

    private static Object invokeNoArgs(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
            return null;
        }
    }
}
