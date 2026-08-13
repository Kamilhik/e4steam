package link.e4steam.internal.dedicated;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedicatedServerPropertiesValidatorTest {
    @Test void acceptsOnlyExplicitLoopbackWithoutAuxiliaryIngress() {
        Properties properties = new Properties();
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("online-mode", "false");
        assertTrue(DedicatedServerPropertiesValidator.validate(properties).allowed());

        properties.setProperty("server-ip", "::1");
        assertTrue(DedicatedServerPropertiesValidator.validate(properties).allowed());
    }

    @Test void rejectsWildcardRemoteRconAndQuery() {
        Properties properties = new Properties();
        assertEquals("SERVER_IP_MUST_BE_LOOPBACK",
                DedicatedServerPropertiesValidator.validate(properties).category());
        properties.setProperty("server-ip", "0.0.0.0");
        assertFalse(DedicatedServerPropertiesValidator.validate(properties).allowed());
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("enable-rcon", "true");
        assertEquals("RCON_MUST_BE_DISABLED",
                DedicatedServerPropertiesValidator.validate(properties).category());
        properties.setProperty("enable-rcon", "false");
        properties.setProperty("enable-query", "true");
        assertEquals("QUERY_MUST_BE_DISABLED",
                DedicatedServerPropertiesValidator.validate(properties).category());
    }
}
