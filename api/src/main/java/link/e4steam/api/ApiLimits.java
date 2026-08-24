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
    /** Maximum UTF-8 payload accepted by a single addon channel message. */
    public static final int MAX_CHANNEL_MESSAGE_BYTES = 1_048_576;
    /** Maximum virtual UDP datagram size. */
    public static final int MAX_DATAGRAM_BYTES = 65_507;
    /** Maximum number of entries in a bounded API page. */
    public static final int MAX_PAGE_SIZE = 100;
    /** Maximum number of metadata/config/storage entries in one object. */
    public static final int MAX_MAP_ENTRIES = 256;
    /** Maximum UTF-16 length of a metadata/config value. */
    public static final int MAX_VALUE_LENGTH = 4_096;
    /** Maximum private storage blob size per operation. */
    public static final int MAX_STORAGE_BLOB_BYTES = 4 * 1_048_576;
    /** Maximum modpack manifest entries. */
    public static final int MAX_MODPACK_ENTRIES = 2_048;
    /** Maximum encoded skin image size. */
    public static final int MAX_SKIN_BYTES = 2 * 1_048_576;
    /** Maximum bounded diagnostics fields. */
    public static final int MAX_DIAGNOSTIC_FIELDS = 256;
    /** Maximum bounded completion suggestions. */
    public static final int MAX_COMMAND_SUGGESTIONS = 100;
    /** Maximum registration count per contribution family and addon. */
    public static final int MAX_REGISTRATIONS_PER_FAMILY = 1_000;
    /** Maximum default timeout exposed by public asynchronous operations. */
    public static final long MAX_OPERATION_TIMEOUT_MILLIS = 30_000L;

    private ApiLimits() {
    }
}
