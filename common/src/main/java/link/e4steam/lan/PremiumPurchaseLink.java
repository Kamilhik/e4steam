package link.e4steam.lan;

/** Free-client purchase link; no Premium classes or licence keys are required. */
public final class PremiumPurchaseLink {
    public static final String URL = "https://boosty.to/freeweb1";
    private PremiumPurchaseLink() { }
    public static boolean open() {
        return open(URL);
    }
    public static boolean open(String url) {
        if (!URL.equals(url)) return false;
        // Util moved packages in 26.x. Do not put either path in a bytecode
        // class reference shared by all Minecraft versions.
        for (String className : new String[]{"net.minecraft.Util", "net.minecraft.util.Util", "net.minecraft.class_156"}) {
            try {
                Class<?> util = Class.forName(className);
                Object platform = null;
                for (String name : new String[]{"getPlatform", "m_137581_", "method_668"}) {
                    try {
                        platform = util.getMethod(name).invoke(null);
                        break;
                    } catch (NoSuchMethodException nextMapping) { }
                }
                if (platform == null) continue;
                for (java.lang.reflect.Method method : platform.getClass().getMethods()) {
                    if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == java.net.URI.class
                            && method.getReturnType() == void.class) {
                        method.invoke(platform, java.net.URI.create(url));
                        return true;
                    }
                }
            } catch (ReflectiveOperationException | LinkageError unavailable) { }
        }
        try {
            if (!java.awt.Desktop.isDesktopSupported()) return false;
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            return true;
        } catch (Exception unavailable) { return false; }
    }
}
