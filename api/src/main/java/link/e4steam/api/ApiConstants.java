package link.e4steam.api;

/** Public version constants; mod and wire versions intentionally remain independent. */
public final class ApiConstants {
    /** First public Java addon API baseline. */
    public static final ApiVersion API_VERSION = ApiVersion.parse("0.1.0");
    /** Current core wire protocol, independent from the Java API version. */
    public static final int WIRE_PROTOCOL_VERSION = 4;

    private ApiConstants() {
    }
}
