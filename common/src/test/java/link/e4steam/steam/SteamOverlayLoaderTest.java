package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamOverlayLoaderTest {
    @TempDir Path temporary;

    @Test void findsNativeLinuxOverlay() throws Exception {
        Path overlay = temporary.resolve(
                ".local/share/Steam/ubuntu12_64/gameoverlayrenderer.so"
        );
        Files.createDirectories(overlay.getParent());
        Files.write(overlay, new byte[] { 1 });

        NativePlatform linux = NativePlatform.normalize("Linux", "amd64");
        assertEquals(overlay.toRealPath(),
                SteamOverlayLoader.findOverlayLibrary(linux, temporary).orElseThrow());
    }

    @Test void findsFlatpakLinuxOverlay() throws Exception {
        Path overlay = temporary.resolve(
                ".var/app/com.valvesoftware.Steam/.local/share/Steam/ubuntu12_64/"
                        + "gameoverlayrenderer.so"
        );
        Files.createDirectories(overlay.getParent());
        Files.write(overlay, new byte[] { 1 });

        assertTrue(SteamOverlayLoader.findOverlayLibrary(
                NativePlatform.normalize("Linux", "x86_64"), temporary).isPresent());
    }

    @Test void findsUniversalMacOverlayForIntelAndArm() throws Exception {
        Path overlay = temporary.resolve(
                "Library/Application Support/Steam/Steam.AppBundle/Steam/Contents/MacOS/"
                        + "gameoverlayrenderer.dylib"
        );
        Files.createDirectories(overlay.getParent());
        Files.write(overlay, new byte[] { 1 });

        assertTrue(SteamOverlayLoader.findOverlayLibrary(
                NativePlatform.normalize("Mac OS X", "x86_64"), temporary).isPresent());
        assertTrue(SteamOverlayLoader.findOverlayLibrary(
                NativePlatform.normalize("Mac OS X", "aarch64"), temporary).isPresent());
    }

    @Test void WindowsDoesNotAttemptUnixOverlayDiscovery() throws Exception {
        assertFalse(SteamOverlayLoader.findOverlayLibrary(
                NativePlatform.normalize("Windows 11", "amd64"), temporary).isPresent());
    }
}
