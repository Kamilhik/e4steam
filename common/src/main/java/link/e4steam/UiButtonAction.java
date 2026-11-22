package link.e4steam;

import java.lang.reflect.*;

/** Invoke a button's original action without fabricating version-specific mouse/key input. */
final class UiButtonAction {
    private UiButtonAction() { }
    static void invoke(Object button) throws ReflectiveOperationException {
        for(Class<?> type=button.getClass();type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers()) || !field.getType().isInterface())continue;
            for(Method method:field.getType().getMethods()) {
                if(Modifier.isStatic(method.getModifiers()) || method.getReturnType()!=void.class || method.getParameterCount()!=1
                        || !method.getParameterTypes()[0].isAssignableFrom(button.getClass()))continue;
                field.setAccessible(true);Object action=field.get(button);
                if(action!=null){method.invoke(action,button);return;}
            }
        }
        throw new NoSuchMethodException("MINECRAFT_BUTTON_ACTION_UNAVAILABLE");
    }
}
