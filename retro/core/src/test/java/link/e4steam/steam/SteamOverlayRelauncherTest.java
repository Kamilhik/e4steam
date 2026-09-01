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
    public void rewritesCurrentPrismBootstrapIntoDirectMinecraftLaunch() {
        List<String> wrapper = Arrays.asList(
                "/usr/bin/java", "-Xmx2G", "-cp", "NewLaunch.jar:game.jar",
                "org.prismlauncher.EntryPoint"
        );
        List<String> direct = SteamOverlayRelauncher.rewritePublishedLauncherCommand(
                wrapper,
                "org.prismlauncher.EntryPoint",
                "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "--username\u001FPlayer Name\u001F--gameDir\u001F/home/user/My Instance"
        ).get();

        assertEquals(Arrays.asList(
                "/usr/bin/java", "-Xmx2G", "-cp", "NewLaunch.jar:game.jar",
                "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "--username", "Player Name", "--gameDir", "/home/user/My Instance"
        ), direct);
    }

    @Test
    public void refusesIncompleteOrUnsafePublishedPrismLaunchData() {
        List<String> wrapper = Arrays.asList(
                "java", "org.prismlauncher.EntryPoint"
        );
        assertFalse(SteamOverlayRelauncher.rewritePublishedLauncherCommand(
                wrapper, "org.prismlauncher.EntryPoint", null, "--username\u001FPlayer"
        ).isPresent());
        assertFalse(SteamOverlayRelauncher.rewritePublishedLauncherCommand(
                wrapper, "org.prismlauncher.EntryPoint", "bad main", "--username\u001FPlayer"
        ).isPresent());
        assertFalse(SteamOverlayRelauncher.rewritePublishedLauncherCommand(
                wrapper, "org.prismlauncher.EntryPoint", ".bad.Main", "--username\u001FPlayer"
        ).isPresent());
    }

    @Test
    public void requiresExplicitOptInForRetroOverlayRelaunch() {
        assertFalse(SteamOverlayRelauncher.relaunchEnabled(null));
        assertTrue(SteamOverlayRelauncher.relaunchEnabled("true"));
        assertFalse(SteamOverlayRelauncher.relaunchEnabled("false"));
    }

    @Test
    public void recognizesEitherRelaunchMarker() {
        assertFalse(SteamOverlayRelauncher.isRelaunched(null, null));
        assertTrue(SteamOverlayRelauncher.isRelaunched("1", null));
        assertTrue(SteamOverlayRelauncher.isRelaunched(null, "true"));
        assertFalse(SteamOverlayRelauncher.isRelaunched(null, "false"));
    }

    @Test
    public void detectsMacOsUnixOverlayRelaunchRequest() {
        String originalOsName = System.getProperty("os.name");
        String originalOsArch = System.getProperty("os.arch");
        String originalRelaunch = System.getProperty("e4steam.overlayRelaunch");
        try {
            System.setProperty("os.name", "Mac OS X");
            System.setProperty("os.arch", "x86_64");
            System.clearProperty("e4steam.overlayRelaunch");
            assertFalse(SteamOverlayRelauncher.isMacOsUnixOverlayRelaunchRequested());

            System.setProperty("e4steam.overlayRelaunch", "true");
            assertTrue(SteamOverlayRelauncher.isMacOsUnixOverlayRelaunchRequested());

            System.setProperty("e4steam.overlayRelaunch", "false");
            assertFalse(SteamOverlayRelauncher.isMacOsUnixOverlayRelaunchRequested());

            System.setProperty("os.name", "Linux");
            System.setProperty("os.arch", "amd64");
            System.clearProperty("e4steam.overlayRelaunch");
            assertFalse(SteamOverlayRelauncher.isMacOsUnixOverlayRelaunchRequested());
        } finally {
            restore("os.name", originalOsName);
            restore("os.arch", originalOsArch);
            restore("e4steam.overlayRelaunch", originalRelaunch);
        }
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
    public void insertsMacOsFirstThreadOptionOnlyForMacOs() throws Exception {
        List<String> mac = new ArrayList<String>(Arrays.asList(
                "java", "-Xmx2G", "Main"
        ));
        NativePlatform macOs = NativePlatform.normalize("Mac OS X", "x86_64");
        SteamOverlayRelauncher.addMacOsFirstThreadOption(mac, macOs);
        SteamOverlayRelauncher.addMacOsFirstThreadOption(mac, macOs);
        assertEquals(1, count(mac, "-XstartOnFirstThread"));
        assertEquals("-XstartOnFirstThread", mac.get(1));

        List<String> linux = new ArrayList<String>(Arrays.asList(
                "java", "-Xmx2G", "Main"
        ));
        SteamOverlayRelauncher.addMacOsFirstThreadOption(
                linux,
                NativePlatform.normalize("Linux", "amd64")
        );
        assertEquals(0, count(linux, "-XstartOnFirstThread"));
    }

    @Test
    public void disablesForgeEarlyWindowWhenPrismHidesMinecraftArgumentsInStdin() {
        List<String> prismWrapper = Arrays.asList(
                "java", "org.prismlauncher.EntryPoint"
        );
        assertTrue(SteamOverlayRelauncher.shouldDisableForgeEarlyProgressWindow(
                true, prismWrapper
        ));
    }

    @Test
    public void recognizesRetroForgeVersionFromVisibleModLauncherArguments() {
        List<String> forge = Arrays.asList(
                "java", "cpw.mods.modlauncher.Launcher",
                "--fml.mcVersion", "1.16.5"
        );
        assertTrue(SteamOverlayRelauncher.shouldDisableForgeEarlyProgressWindow(
                false, forge
        ));
    }

    @Test
    public void leavesFabricWrapperCommandUnchanged() {
        List<String> fabricWrapper = Arrays.asList(
                "java", "org.prismlauncher.EntryPoint"
        );
        assertFalse(SteamOverlayRelauncher.shouldDisableForgeEarlyProgressWindow(
                false, fabricWrapper
        ));
    }

    @Test
    public void delegatesParentShutdownToLegacyForge() {
        net.minecraftforge.fml.common.FMLCommonHandler.reset();

        assertTrue(SteamOverlayRelauncher.requestLegacyForgeExit(17));
        assertEquals(17, net.minecraftforge.fml.common.FMLCommonHandler.exitCode);
        assertFalse(net.minecraftforge.fml.common.FMLCommonHandler.abortive);
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

    private static void restore(String name, String value) {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
    }
}
