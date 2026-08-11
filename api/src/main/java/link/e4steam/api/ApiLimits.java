package link.e4steam.api;

/** Central safe defaults for inputs accepted by the public addon API. */
public final class ApiLimits {
    /** Maximum UTF-16 length of a namespaced identifier. */
    public static final int MAX_IDENTIFIER_LENGTH = 96;
    /** Maximum UTF-16 length of a human-readable label. */
    public static final int MAX_DISPLAY_NAME_LENGTH = 64;
    /** Maximum number of declared dependencies for one addon. */
    public static final int MAX_ADDON_DEPENDENCIES = 64;
    /** Maximum number of capabilities requested by one addon. */
    public static final int MAX_REQUESTED_CAPABILITIES = 64;
    /** Maximum number of event subscriptions owned by one addon. */
    public static final int MAX_EVENT_SUBSCRIPTIONS = 1024;
    /** Maximum number of queued worker tasks owned by one addon. */
    public static final int MAX_QUEUED_TASKS = 256;
    /** Maximum lifecycle callback duration in milliseconds. */
    public static final long MAX_LIFECYCLE_CALLBACK_MILLIS = 10_000L;

    private ApiLimits() {
    }
}
