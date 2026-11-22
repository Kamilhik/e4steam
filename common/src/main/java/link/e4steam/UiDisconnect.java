package link.e4steam;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Full save/disconnect entry points; never substitutes the renderer-only clearClientLevel. */
final class UiDisconnect {
    private static final String[] NAMES={"disconnect","clearLevel","method_56134","method_18096",
            "method_18099","m_91320_"};
    private UiDisconnect() { }
    static void invoke(Object client,Object nextScreen) throws ReflectiveOperationException {
        invoke(client,nextScreen,()->{});
    }
    static void invoke(Object client,Object nextScreen,Runnable leaveWorld) throws ReflectiveOperationException {
        // Prefer the one-argument overload; newer games also have (Screen, boolean).
        for (int count:new int[]{1,2,3}) for (String name:NAMES) for (Method method:client.getClass().getMethods()) {
            Class<?>[] args=method.getParameterTypes();
            if (!method.getName().equals(name) || Modifier.isStatic(method.getModifiers())
                    || method.getReturnType()!=void.class || args.length!=count
                    || !args[0].isAssignableFrom(nextScreen.getClass())) continue;
            boolean valid=true;for(int i=1;i<count;i++)if(args[i]!=boolean.class)valid=false;
            if(!valid)continue;
            leaveWorld.run();
            Object[] values=new Object[count];values[0]=nextScreen;for(int i=1;i<count;i++)values[i]=false;
            method.invoke(client,values);
            return;
        }
        throw new NoSuchMethodException("MINECRAFT_DISCONNECT_UNAVAILABLE");
    }
    static void leaveWorld(Object level,Object reason) throws ReflectiveOperationException {
        for(int count:new int[]{0,1}) for(String name:new String[]{"disconnect","method_8525","m_7462_"})
            for(Method method:level.getClass().getMethods()) {
                Class<?>[] args=method.getParameterTypes();
                if(!method.getName().equals(name) || Modifier.isStatic(method.getModifiers()) || method.getReturnType()!=void.class
                        || args.length!=count || count==1 && !args[0].isAssignableFrom(reason.getClass()))continue;
                if(count==0)method.invoke(level);else method.invoke(level,reason);return;
            }
        throw new NoSuchMethodException("MINECRAFT_WORLD_QUIT_UNAVAILABLE");
    }
}
