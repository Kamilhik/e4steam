package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamOverlayRelauncherTest {
    @TempDir Path temporary;

    @Test void selectsOnlySupportedUnixDynamicLinkerVariables() throws Exception {
        assertEquals("LD_PRELOAD", SteamOverlayRelauncher.insertLibrariesEnvName(
                NativePlatform.normalize("Linux", "amd64")));
        assertEquals("DYLD_INSERT_LIBRARIES", SteamOverlayRelauncher.insertLibrariesEnvName(
                NativePlatform.normalize("Mac OS X", "x86_64")));
        assertEquals("DYLD_INSERT_LIBRARIES", SteamOverlayRelauncher.insertLibrariesEnvName(
                NativePlatform.normalize("Mac OS X", "aarch64")));
        assertNull(SteamOverlayRelauncher.insertLibrariesEnvName(
                NativePlatform.normalize("Windows 11", "amd64")));
    }

    @Test void preservesExistingPreloads() {
        assertEquals("overlay.so", SteamOverlayRelauncher.mergeInsertedLibrary(
                null, "overlay.so"));
        assertEquals("existing.so" + File.pathSeparator + "overlay.so",
                SteamOverlayRelauncher.mergeInsertedLibrary("existing.so", "overlay.so"));
    }

    @Test void configuresPermanentSpacewarEnvironmentForReplacementJvm() {
        Map<String, String> environment = new HashMap<>();
        environment.put("SteamAppId", "999");

        SteamOverlayRelauncher.configureSteamAppEnvironment(environment);

        assertEquals("480", environment.get("SteamAppId"));
        assertEquals("480", environment.get("SteamGameId"));
        assertEquals("480", environment.get("SteamOverlayGameId"));
    }

    @Test void parsesProcSelfCmdlineWithoutShellQuoting() {
        byte[] raw = ("/usr/bin/java\0-Xmx2G\0org.prismlauncher.EntryPoint\0")
                .getBytes(StandardCharsets.UTF_8);
        List<String> command = SteamOverlayRelauncher.parseNullSeparatedCommand(raw)
                .orElseThrow();
        assertEquals(Arrays.asList(
                "/usr/bin/java", "-Xmx2G", "org.prismlauncher.EntryPoint"
        ), command);
        assertEquals("org.prismlauncher.EntryPoint",
                SteamOverlayRelauncher.detectStdinLauncherWrapper(command));
    }

    @Test void acceptsOnlyBoundedRegularCaptureFiles() throws Exception {
        Path capture = temporary.resolve("capture.bin");
        Files.write(capture, new byte[] { 1, 2, 3 });
        assertTrue(SteamOverlayRelauncher.validateCapturedStdin(capture, false).isPresent());
        assertFalse(SteamOverlayRelauncher.validateCapturedStdin(capture, true).isPresent());
        assertFalse(SteamOverlayRelauncher.validateCapturedStdin(
                temporary.resolve("missing.bin"), false).isPresent());
    }

    @Test void rewritesCurrentPrismBootstrapIntoDirectMinecraftLaunch() {
        List<String> wrapper = Arrays.asList(
                "/usr/bin/java", "-Xmx2G", "-cp", "NewLaunch.jar:game.jar",
                "org.prismlauncher.EntryPoint"
        );
        List<String> direct = SteamOverlayRelauncher.rewritePublishedLauncherCommand(
                wrapper,
                "org.prismlauncher.EntryPoint",
                "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "--username\u001fPlayer Name\u001f--gameDir\u001f/home/user/My Instance"
        ).orElseThrow();

        assertEquals(Arrays.asList(
                "/usr/bin/java", "-Xmx2G", "-cp", "NewLaunch.jar:game.jar",
                "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "--username", "Player Name", "--gameDir", "/home/user/My Instance"
        ), direct);
    }

    @Test void refusesIncompleteOrUnsafePublishedPrismLaunchData() {
        List<String> wrapper = Arrays.asList(
                "java", "org.prismlauncher.EntryPoint"
        );
        assertFalse(SteamOverlayRelauncher.rewritePublishedLauncherCommand(
                wrapper, "org.prismlauncher.EntryPoint", null, "--username\u001fPlayer"
        ).isPresent());
        assertFalse(SteamOverlayRelauncher.rewritePublishedLauncherCommand(
                wrapper, "org.prismlauncher.EntryPoint", "bad main", "--username\u001fPlayer"
        ).isPresent());
        assertFalse(SteamOverlayRelauncher.rewritePublishedLauncherCommand(
                wrapper, "org.prismlauncher.EntryPoint", ".bad.Main", "--username\u001fPlayer"
        ).isPresent());
    }
}
