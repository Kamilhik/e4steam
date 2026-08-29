package link.e4steam.steam;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SteamOverlayRelauncherTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void selectsOnlySupportedUnixInsertionVariables() throws Exception {
        assertEquals("LD_PRELOAD", SteamOverlayRelauncher.insertLibrariesEnvName(
                NativePlatform.normalize("Linux", "amd64")
        ));
        assertEquals("DYLD_INSERT_LIBRARIES", SteamOverlayRelauncher.insertLibrariesEnvName(
                NativePlatform.normalize("Mac OS X", "x86_64")
        ));
        assertEquals("DYLD_INSERT_LIBRARIES", SteamOverlayRelauncher.insertLibrariesEnvName(
                NativePlatform.normalize("Mac OS X", "aarch64")
        ));
        assertNull(SteamOverlayRelauncher.insertLibrariesEnvName(
                NativePlatform.normalize("Windows 11", "amd64")
        ));
    }

    @Test
    public void mergesOverlayAndSetsSteamEnvironment() {
        assertEquals("/overlay.so", SteamOverlayRelauncher.mergeInsertedLibrary(
                null, "/overlay.so"
        ));
        assertEquals(
                "/existing" + java.io.File.pathSeparator + "/overlay.so",
                SteamOverlayRelauncher.mergeInsertedLibrary("/existing", "/overlay.so")
        );

        Map<String, String> environment = new HashMap<String, String>();
        SteamOverlayRelauncher.configureSteamAppEnvironment(environment);
        assertEquals("480", environment.get("SteamAppId"));
        assertEquals("480", environment.get("SteamGameId"));
        assertEquals("480", environment.get("SteamOverlayGameId"));
    }

    @Test
    public void detectsStdinLaunchersAndParsesProcCommand() {
        assertEquals("org.prismlauncher.EntryPoint",
                SteamOverlayRelauncher.detectStdinLauncherWrapper(Arrays.asList(
                        "java", "-cp", "launcher.jar", "org.prismlauncher.EntryPoint"
                )));
        assertNull(SteamOverlayRelauncher.detectStdinLauncherWrapper(Arrays.asList(
                "java", "net.minecraft.client.main.Main"
        )));

        byte[] raw = "java\0-Xmx2G\0Main\0".getBytes(StandardCharsets.UTF_8);
        assertEquals(Arrays.asList("java", "-Xmx2G", "Main"),
                SteamOverlayRelauncher.parseNullSeparatedCommand(raw).get());
    }

    @Test
    public void validatesCapturedStdinAndRejectsTruncation() throws Exception {
        Path capture = temporary.newFile("stdin.bin").toPath();
        Files.write(capture, new byte[] {1, 2, 3});
        assertEquals(capture.toAbsolutePath().normalize(),
                SteamOverlayRelauncher.validateCapturedStdin(capture, false).get());
        assertFalse(SteamOverlayRelauncher.validateCapturedStdin(capture, true).isPresent());
        assertFalse(SteamOverlayRelauncher.validateCapturedStdin(
                temporary.getRoot().toPath().resolve("missing.bin"), false
        ).isPresent());
    }

    @Test
    public void insertsVmOptionBeforeLauncherBoundaryWithoutDuplicates() {
        List<String> command = new ArrayList<String>(Arrays.asList(
                "java", "-Xmx2G", "-cp", "client.jar", "Main"
        ));
        SteamOverlayRelauncher.addVmOption(
                command, "-Dfml.earlyprogresswindow=false"
        );
        assertEquals("-Dfml.earlyprogresswindow=false", command.get(1));
        SteamOverlayRelauncher.addVmOption(
                command, "-Dfml.earlyprogresswindow=false"
        );
        assertEquals(1, count(command, "-Dfml.earlyprogresswindow=false"));
    }

    @Test
    public void keepsMinecraftArgumentValuesWithSpacesTogether() {
        assertEquals(Arrays.asList(
                        "--gameDir", "/Users/test/Library/Application Support/minecraft",
                        "--username", "Player"
                ),
                SteamOverlayRelauncher.splitMinecraftStyleArguments(
                        "--gameDir /Users/test/Library/Application Support/minecraft "
                                + "--username Player"
                ));
    }

    private static int count(List<String> values, String target) {
        int count = 0;
        for (String value : values) {
            if (target.equals(value)) count++;
        }
        return count;
    }
}
