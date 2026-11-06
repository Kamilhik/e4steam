package link.e4steam.internal.dedicated;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DedicatedStartupFilesTest {
    @TempDir Path root;

    private Path properties(String text) throws IOException {
        Path file = root.resolve("server.properties");
        Files.write(file, text.getBytes(StandardCharsets.ISO_8859_1));
        return file;
    }

    private Properties readProperties() throws IOException {
        Properties result = new Properties();
        result.load(new StringReader(Files.readString(root.resolve("server.properties"), StandardCharsets.ISO_8859_1)));
        return result;
    }

    @Test void createsEnabledConfigAndNeverReplacesAdministratorOptOut() throws Exception {
        Path file = DedicatedStartupFiles.ensureDefaults(root);
        DedicatedRuntimeConfig config = DedicatedRuntimeConfig.load(file, new HashMap<>(), new Properties());
        assertTrue(config.enabled());
        assertEquals(0, config.queryPort());
        assertEquals(8, config.maxPeers());
        assertEquals("UNLISTED", config.accessMode().name());
        Files.writeString(file, "# administrator config\nschema-version = 1\nenabled = false\nmax-peers = 64\n");
        byte[] before = Files.readAllBytes(file);
        DedicatedStartupFiles.ensureDefaults(root);
        assertArrayEquals(before, Files.readAllBytes(file));
        assertFalse(DedicatedRuntimeConfig.load(file, new HashMap<>(), new Properties()).enabled());
    }

    @Test void disabledStartupDoesNotTouchPropertiesOrCreateBackups() throws Exception {
        Path file = properties("server-ip=0.0.0.0\nserver-port=21812\nenable-query=true\n");
        byte[] before = Files.readAllBytes(file);
        DedicatedStartupFiles.ensureDefaults(root);
        assertFalse(DedicatedStartupFiles.prepareMinecraft(root, false));
        assertArrayEquals(before, Files.readAllBytes(file));
        assertFalse(Files.exists(root.resolve("config/e4steam-dedicated")));
    }

    @Test void generatedTemplateCannotDisableAnExplicitDeploymentOptIn() throws Exception {
        Path file = DedicatedStartupFiles.ensureDefaults(root);
        Files.writeString(file, Files.readString(file).replace("enabled = true", "enabled = false"));
        HashMap<String, String> environment = new HashMap<>();
        environment.put("E4STEAM_DEDICATED_ENABLED", "true");
        environment.put("E4STEAM_DEDICATED_MAX_PEERS", "64");
        assertTrue(DedicatedRuntimeConfig.load(file, environment, new Properties()).enabled());
        assertEquals(64, DedicatedRuntimeConfig.load(file, environment, new Properties()).maxPeers());
        Properties system = new Properties();
        system.setProperty("e4steam.dedicated.enabled", "false");
        assertFalse(DedicatedRuntimeConfig.load(file, environment, system).enabled());
    }

    @Test void enabledStartupPreservesPortAuthenticationWorldAndOriginalBytes() throws Exception {
        String original = "# admin comment\r\nserver-ip=0.0.0.0\r\nserver-port=21812\r\nonline-mode=true\r\n"
                + "enforce-secure-profile=true\r\nlevel-name=existing-world\r\nrcon.password=private-value\r\n"
                + "enable-query=true\r\nenable-rcon=true\r\n";
        properties(original);
        assertTrue(DedicatedStartupFiles.prepareMinecraft(root, true));
        Properties p = readProperties();
        assertEquals("127.0.0.1", p.getProperty("server-ip"));
        assertEquals("false", p.getProperty("enable-query"));
        assertEquals("false", p.getProperty("enable-rcon"));
        assertEquals("21812", p.getProperty("server-port"));
        assertEquals("true", p.getProperty("online-mode"));
        assertEquals("true", p.getProperty("enforce-secure-profile"));
        assertEquals("existing-world", p.getProperty("level-name"));
        assertEquals("private-value", p.getProperty("rcon.password"));
        assertArrayEquals(original.getBytes(StandardCharsets.ISO_8859_1),
                Files.readAllBytes(root.resolve("config/e4steam-dedicated/server.properties.before-steam-only")));
        byte[] prepared = Files.readAllBytes(root.resolve("server.properties"));
        assertFalse(DedicatedStartupFiles.prepareMinecraft(root, true));
        assertArrayEquals(prepared, Files.readAllBytes(root.resolve("server.properties")));
    }

    @Test void reappliesPanelResetWithoutOverwritingFirstBackupOrOfflineChoice() throws Exception {
        properties("server-ip=\nserver-port=21812\nonline-mode=false\n");
        DedicatedStartupFiles.prepareMinecraft(root, true);
        Path backup = root.resolve("config/e4steam-dedicated/server.properties.before-steam-only");
        byte[] firstBackup = Files.readAllBytes(backup);
        properties("server-ip=0.0.0.0\nserver-port=29999\nonline-mode=false\nenable-query=true\n");
        assertTrue(DedicatedStartupFiles.prepareMinecraft(root, true));
        assertEquals("29999", readProperties().getProperty("server-port"));
        assertEquals("false", readProperties().getProperty("online-mode"));
        assertArrayEquals(firstBackup, Files.readAllBytes(backup));
    }

    @Test void escapedDuplicateKeysCannotRestorePublicIngress() throws Exception {
        properties("server-ip=127.0.0.1\nserver\\-ip=0.0.0.0\nenable\\-query=true\n");
        DedicatedStartupFiles.prepareMinecraft(root, true);
        assertTrue(DedicatedServerPropertiesValidator.validate(readProperties()).allowed());
    }

    @Test void existingSafeIpv6PropertiesAreUnchanged() throws Exception {
        Path file = properties("server-ip=::1\nserver-port=25565\n");
        byte[] before = Files.readAllBytes(file);
        assertFalse(DedicatedStartupFiles.prepareMinecraft(root, true));
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test void missingPropertiesAreCreatedOnlyForExplicitSteamOptIn() throws Exception {
        assertFalse(DedicatedStartupFiles.prepareMinecraft(root, false));
        assertFalse(Files.exists(root.resolve("server.properties")));
        assertTrue(DedicatedStartupFiles.prepareMinecraft(root, true));
        assertTrue(DedicatedServerPropertiesValidator.validate(readProperties()).allowed());
        assertNull(readProperties().getProperty("online-mode"));
        assertNull(readProperties().getProperty("server-port"));
    }

    @Test void invalidOrOversizedPropertiesAreNotOverwritten() throws Exception {
        Path file = properties("server-ip=\\uZZZZ\n");
        byte[] before = Files.readAllBytes(file);
        assertThrows(IOException.class, () -> DedicatedStartupFiles.prepareMinecraft(root, true));
        assertArrayEquals(before, Files.readAllBytes(file));
        Files.write(file, new byte[131073]);
        assertThrows(IOException.class, () -> DedicatedStartupFiles.prepareMinecraft(root, true));
        assertEquals(131073, Files.size(file));
    }

    @Test void symbolicLinkPropertiesCannotBeRewritten() throws Exception {
        Path target = root.resolve("other.properties");
        Files.writeString(target, "server-ip=0.0.0.0\n");
        try { Files.createSymbolicLink(root.resolve("server.properties"), target); }
        catch (IOException | UnsupportedOperationException unavailable) { assumeTrue(false, "Symlinks unavailable"); }
        assertThrows(IOException.class, () -> DedicatedStartupFiles.prepareMinecraft(root, true));
        assertEquals("server-ip=0.0.0.0\n", Files.readString(target));
    }
}
