package link.e4steam.internal.dedicated;

import link.e4steam.api.dedicated.DedicatedServerService.DedicatedAccessMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedicatedRuntimeConfigTest {
    @TempDir Path temporary;

    @Test
    void defaultsArePrivateAnonymousAndDisabled() {
        DedicatedRuntimeConfig config = DedicatedRuntimeConfig.fromEnvironment(
                new HashMap<>(), new Properties());
        assertFalse(config.enabled());
        assertEquals(DedicatedAccessMode.PRIVATE, config.accessMode());
        assertEquals(8, config.maxPeers());
        assertEquals("ANONYMOUS", config.safeSnapshot().loginMode());
        assertFalse(config.safeSnapshot().publicationEnabled());
    }

    @Test
    void readsBoundedNonSecretEnvironment() {
        HashMap<String, String> environment = new HashMap<>();
        environment.put("E4STEAM_DEDICATED_ENABLED", "true");
        environment.put("E4STEAM_DEDICATED_ACCESS", "whitelist");
        environment.put("E4STEAM_DEDICATED_MAX_PEERS", "12");
        DedicatedRuntimeConfig config = DedicatedRuntimeConfig.fromEnvironment(
                environment, new Properties());
        assertTrue(config.enabled());
        assertEquals(DedicatedAccessMode.WHITELIST, config.accessMode());
        assertEquals(12, config.maxPeers());
    }

    @Test
    void refusesRemoteMinecraftBindAndUnsafeValues() throws Exception {
        DedicatedRuntimeConfig config = new DedicatedRuntimeConfig(
                true, DedicatedAccessMode.PRIVATE, 8, 65535, "server");
        config.validateMinecraftBind(InetAddress.getByName("127.0.0.1"));
        assertThrows(IllegalStateException.class,
                () -> config.validateMinecraftBind(InetAddress.getByName("0.0.0.0")));
        assertThrows(IllegalArgumentException.class, () -> new DedicatedRuntimeConfig(
                true, DedicatedAccessMode.CUSTOM, 8, 65535, "server"));
        assertThrows(IllegalArgumentException.class, () -> new DedicatedRuntimeConfig(
                true, DedicatedAccessMode.PRIVATE, 65, 65535, "server"));
    }

    @Test
    void loadsVersionedTomlAndLetsExplicitSystemPropertiesOverrideIt() throws Exception {
        Path configFile = temporary.resolve("e4steam-dedicated.toml");
        Files.write(configFile, java.util.Arrays.asList(
                "schema-version = 1",
                "enabled = true",
                "access-mode = \"WHITELIST\"",
                "max-peers = 12",
                "query-port = 65535",
                "server-name = \"Private test world\"",
                "whitelist = [\"76561198000000001\", \"76561198000000002\"]",
                "auth-mode = \"ANONYMOUS\"",
                "publication = false",
                "ingress-guard = \"STEAM_ONLY\"",
                "diagnostics-level = \"BASIC\"",
                "relay-policy = \"OFFICIAL_AUTOMATIC\""
        ), StandardCharsets.UTF_8);
        Properties overrides = new Properties();
        overrides.setProperty("e4steam.dedicated.maxPeers", "6");

        DedicatedRuntimeConfig config = DedicatedRuntimeConfig.load(
                configFile, new HashMap<>(), overrides);

        assertTrue(config.enabled());
        assertEquals(DedicatedAccessMode.WHITELIST, config.accessMode());
        assertEquals(6, config.maxPeers());
        assertTrue(config.isWhitelisted(76561198000000001L));
    }

    @Test
    void rejectsUnknownSecretFieldsAndPermissiveSecurityModes() throws Exception {
        Path secret = temporary.resolve("secret.toml");
        Files.write(secret, java.util.Arrays.asList(
                "schema-version = 1",
                "steam-password = \"must-not-be-supported\""
        ), StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> DedicatedRuntimeConfig.load(
                secret, new HashMap<>(), new Properties()));

        Path unsafe = temporary.resolve("unsafe.toml");
        Files.write(unsafe, java.util.Arrays.asList(
                "schema-version = 1",
                "publication = true"
        ), StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> DedicatedRuntimeConfig.load(
                unsafe, new HashMap<>(), new Properties()));
    }
}
