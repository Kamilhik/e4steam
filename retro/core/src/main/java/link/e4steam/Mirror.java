package link.e4steam;

/** Minimal loader-neutral localization boundary for Java 8 retro clients. */
public final class Mirror {
    private Mirror() {
    }

    public static String translatable(String key, Object... arguments) {
        StringBuilder value = new StringBuilder(key == null ? "e4steam" : key);
        if (arguments != null && arguments.length > 0) {
            value.append(':');
            for (Object argument : arguments) {
                value.append(' ').append(String.valueOf(argument));
            }
        }
        return value.toString();
    }
}
