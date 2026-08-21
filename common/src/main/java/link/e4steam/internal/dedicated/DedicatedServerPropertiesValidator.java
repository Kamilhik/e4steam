package link.e4steam.internal.dedicated;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

/** Fail-closed validation for the Minecraft socket hidden behind Steam. */
public final class DedicatedServerPropertiesValidator {
    private DedicatedServerPropertiesValidator() {
    }

    public static Validation validate(Properties properties) {
        Properties source = properties == null ? new Properties() : properties;
        String bind = source.getProperty("server-ip", "").trim();
        if (bind.isEmpty()) {
            return Validation.deny("SERVER_IP_MUST_BE_LOOPBACK");
        }
        try {
            InetAddress address = InetAddress.getByName(bind);
            if (!address.isLoopbackAddress()) {
                return Validation.deny("SERVER_IP_MUST_BE_LOOPBACK");
            }
        } catch (UnknownHostException failure) {
            return Validation.deny("SERVER_IP_INVALID");
        }
        if (Boolean.parseBoolean(source.getProperty("enable-rcon", "false"))) {
            return Validation.deny("RCON_MUST_BE_DISABLED");
        }
        if (Boolean.parseBoolean(source.getProperty("enable-query", "false"))) {
            return Validation.deny("QUERY_MUST_BE_DISABLED");
        }
        return Validation.allow();
    }

    public static final class Validation {
        private final boolean allowed;
        private final String category;

        private Validation(boolean allowed, String category) {
            this.allowed = allowed;
            this.category = category;
        }

        static Validation allow() { return new Validation(true, ""); }
        static Validation deny(String category) { return new Validation(false, category); }
        public boolean allowed() { return allowed; }
        public String category() { return category; }
        @Override public String toString() {
            return "DedicatedPropertiesValidation{" + (allowed ? "allowed" : category) + '}';
        }
    }
}
