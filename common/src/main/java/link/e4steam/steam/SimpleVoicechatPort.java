package link.e4steam.steam;

/** Optional, headless-safe lookup of the socket Simple Voice Chat actually bound. */
final class SimpleVoicechatPort {
    private SimpleVoicechatPort(){}
    private static ClassLoader[] loaders() {
        return new ClassLoader[]{Thread.currentThread().getContextClassLoader(),SimpleVoicechatPort.class.getClassLoader()};
    }
    static boolean present() {
        for(ClassLoader loader:loaders())try {
            Class.forName("de.maxhenkel.voicechat.Voicechat",false,loader);return true;
        } catch(ClassNotFoundException | LinkageError absent) { }
        return false;
    }
    static int bound() {
        for(ClassLoader loader:loaders())try {
            Class<?> voice=Class.forName("de.maxhenkel.voicechat.Voicechat",false,loader);
            Object events=voice.getField("SERVER").get(null);
            if(events==null)continue;
            Object server=events.getClass().getMethod("getServer").invoke(events);
            if(server==null)continue;
            Object value=server.getClass().getMethod("getPort").invoke(server);
            if(value instanceof Number) {
                int port=((Number)value).intValue();if(port>0&&port<=65535)return port;
            }
        } catch(ReflectiveOperationException | LinkageError | RuntimeException unavailable) { }
        return 0;
    }
}
