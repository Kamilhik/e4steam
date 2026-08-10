package link.e4steam.steam;

import com.codedisaster.steamworks.SteamAPI;
import com.sun.jna.NativeLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SteamNativeLibraryLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void selectsWindowsX64Libraries() throws Exception {
        SteamNativeLibraryLoader.NativeNames names =
                SteamNativeLibraryLoader.nativeNames("Windows 11", "amd64");

        assertEquals("windows-x64", names.platformDirectory());
        assertEquals("steam_api64.dll", names.steamApi());
        assertEquals("steamworks4j64.dll", names.steamworks4j());
    }

    @Test
    void selectsLinuxX64Libraries() throws Exception {
        SteamNativeLibraryLoader.NativeNames names =
                SteamNativeLibraryLoader.nativeNames("Linux", "x86_64");

        assertEquals("linux-x64", names.platformDirectory());
        assertEquals("libsteam_api.so", names.steamApi());
        assertEquals("libsteamworks4j.so", names.steamworks4j());
    }

    @Test
    void rejectsUnsupportedArchitecture() {
        assertThrows(
                IOException.class,
                () -> SteamNativeLibraryLoader.nativeNames("Windows 11", "aarch64")
        );
    }

    @Test
    void materializesInCachePathWithSpacesAndUnicode() throws Exception {
        Path cache = SteamNativeLibraryLoader.createPrivateCacheDirectory(
                temporaryDirectory,
                "native cache тест"
        );
        byte[] expected = "verified-library".getBytes(StandardCharsets.UTF_8);

        SteamNativeLibraryLoader.VerifiedLibrary library =
                SteamNativeLibraryLoader.materialize(cache, "steam_api64.dll", expected);

        assertArrayEquals(expected, Files.readAllBytes(library.path()));
        assertEquals(cache, library.path().getParent());
    }

    @Test
    void rejectsPreExistingFileWithWrongHash() throws Exception {
        Path cache = SteamNativeLibraryLoader.createPrivateCacheDirectory(
                temporaryDirectory,
                "cache"
        );
        Files.write(cache.resolve("steam_api64.dll"), new byte[]{9, 9, 9});

        assertThrows(
                IOException.class,
                () -> SteamNativeLibraryLoader.materialize(
                        cache,
                        "steam_api64.dll",
                        new byte[]{1, 2, 3}
                )
        );
    }

    @Test
    void rejectsDirectoryWhereRegularNativeFileIsExpected() throws Exception {
        Path cache = SteamNativeLibraryLoader.createPrivateCacheDirectory(
                temporaryDirectory,
                "cache"
        );
        Files.createDirectory(cache.resolve("steam_api64.dll"));

        assertThrows(
                IOException.class,
                () -> SteamNativeLibraryLoader.materialize(
                        cache,
                        "steam_api64.dll",
                        new byte[]{1, 2, 3}
                )
        );
    }

    @Test
    void rejectsSymlinkCacheAndSymlinkNativeEntryWhenSupported() throws Exception {
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        Path cacheLink = temporaryDirectory.resolve("cache-link");
        try {
            Files.createSymbolicLink(cacheLink, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            assumeTrue(false, "Symbolic links are unavailable for this test account");
        }

        assertThrows(
                IOException.class,
                () -> SteamNativeLibraryLoader.createPrivateCacheDirectory(
                        temporaryDirectory,
                        "cache-link"
                )
        );

        Path cache = SteamNativeLibraryLoader.createPrivateCacheDirectory(
                temporaryDirectory,
                "real-cache"
        );
        Path externalFile = Files.write(outside.resolve("external.dll"), new byte[]{1, 2, 3});
        Files.createSymbolicLink(cache.resolve("steam_api64.dll"), externalFile);
        assertThrows(
                IOException.class,
                () -> SteamNativeLibraryLoader.materialize(
                        cache,
                        "steam_api64.dll",
                        new byte[]{1, 2, 3}
                )
        );
    }

    @Test
    void rejectsSymlinkSwapAfterExtractionWhenSupported() throws Exception {
        Path cache = SteamNativeLibraryLoader.createPrivateCacheDirectory(
                temporaryDirectory,
                "swap-cache"
        );
        byte[] expected = new byte[]{1, 2, 3};
        SteamNativeLibraryLoader.VerifiedLibrary library =
                SteamNativeLibraryLoader.materialize(cache, "steam_api64.dll", expected);
        Path outside = Files.write(temporaryDirectory.resolve("replacement.dll"), expected);
        Files.delete(library.path());
        try {
            Files.createSymbolicLink(library.path(), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            assumeTrue(false, "Symbolic links are unavailable for this test account");
        }

        assertThrows(
                IOException.class,
                () -> SteamNativeLibraryLoader.validateForLoad(library)
        );
    }

    @Test
    void rejectsHardLinkedCacheEntryWhenLinkCountIsAvailable() throws Exception {
        Path cache = SteamNativeLibraryLoader.createPrivateCacheDirectory(
                temporaryDirectory,
                "hardlink-cache"
        );
        byte[] expected = new byte[]{1, 2, 3};
        SteamNativeLibraryLoader.VerifiedLibrary library =
                SteamNativeLibraryLoader.materialize(cache, "steam_api64.dll", expected);
        try {
            Files.createLink(cache.resolve("alias.dll"), library.path());
            Files.getAttribute(library.path(), "unix:nlink");
        } catch (UnsupportedOperationException | IllegalArgumentException | IOException exception) {
            assumeTrue(false, "Hard-link count is unavailable on this filesystem");
        }

        assertThrows(
                IOException.class,
                () -> SteamNativeLibraryLoader.validateForLoad(library)
        );
    }

    @Test
    void concurrentExtractionConvergesOnOneVerifiedFile() throws Exception {
        Path cache = SteamNativeLibraryLoader.createPrivateCacheDirectory(
                temporaryDirectory,
                "concurrent"
        );
        byte[] expected = new byte[32 * 1024];
        for (int index = 0; index < expected.length; index++) {
            expected[index] = (byte) index;
        }

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Path>> tasks = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                tasks.add(() -> SteamNativeLibraryLoader.materialize(
                        cache,
                        "steam_api64.dll",
                        expected
                ).path());
            }
            List<Future<Path>> results = pool.invokeAll(tasks);
            Path first = results.get(0).get();
            for (Future<Path> result : results) {
                assertEquals(first, result.get());
            }
            assertArrayEquals(expected, Files.readAllBytes(first));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void staleInterruptedTemporaryFileIsNeverLoaded() throws Exception {
        Path cache = SteamNativeLibraryLoader.createPrivateCacheDirectory(
                temporaryDirectory,
                "interrupted"
        );
        Path stale = Files.write(
                cache.resolve("steam_api64.dll.interrupted.tmp"),
                "malicious".getBytes(StandardCharsets.UTF_8)
        );
        byte[] expected = "expected".getBytes(StandardCharsets.UTF_8);

        SteamNativeLibraryLoader.VerifiedLibrary library =
                SteamNativeLibraryLoader.materialize(cache, "steam_api64.dll", expected);

        assertArrayEquals(expected, Files.readAllBytes(library.path()));
        assertTrue(Files.exists(stale));
    }

    @Test
    void failureDescriptionHelperDoesNotExposeAbsolutePath() {
        String sensitivePath = temporaryDirectory.resolve("secret library.dll").toString();
        IOException safe = SteamNativeLibraryLoader.safeFailure(
                "Verified native library could not be loaded",
                new IOException("failed at " + sensitivePath)
        );

        assertFalse(safe.getMessage().contains(sensitivePath));
        assertFalse(safe.getMessage().contains(temporaryDirectory.toString()));
        assertTrue(safe.getMessage().contains("IOException"));
    }

    @Test
    void extractsAndLoadsBundledLibraries() throws Exception {
        String os = System.getProperty("os.name", "");
        String arch = System.getProperty("os.arch", "");
        boolean supportedOs = os.toLowerCase(java.util.Locale.ROOT).contains("win")
                || os.toLowerCase(java.util.Locale.ROOT).contains("linux");
        boolean supportedArch = arch.equalsIgnoreCase("amd64")
                || arch.equalsIgnoreCase("x86_64")
                || arch.equalsIgnoreCase("x64");
        assumeTrue(supportedOs && supportedArch);

        SteamNativeLibraryLoader loader = new SteamNativeLibraryLoader();
        assertTrue(SteamAPI.loadLibraries(loader), loader.failureDescription());

        NativeLibrary steamApi = NativeLibrary.getInstance(loader.steamApiPath().toString());
        steamApi.getFunction("SteamAPI_SteamNetworkingMessages_SteamAPI_v002");
        steamApi.getFunction("SteamAPI_SteamNetworkingUtils_SteamAPI_v004");
        steamApi.getFunction("SteamAPI_ISteamNetworkingMessages_SendMessageToUser");
        steamApi.getFunction("SteamAPI_ISteamNetworkingMessages_ReceiveMessagesOnChannel");
        steamApi.getFunction("SteamAPI_ISteamNetworkingUtils_SetGlobalCallback_MessagesSessionRequest");
        steamApi.getFunction("SteamAPI_ISteamNetworkingUtils_SetGlobalCallback_MessagesSessionFailed");
        steamApi.getFunction("SteamAPI_SteamNetworkingMessage_t_Release");
    }

    @Test
    void bindsNetworkingMessagesWhenSteamIsAvailable() throws Exception {
        SteamNativeLibraryLoader loader = new SteamNativeLibraryLoader();
        assertTrue(SteamAPI.loadLibraries(loader), loader.failureDescription());
        assumeTrue(SteamAPI.init());

        try {
            SteamNetworkingMessagesTransport transport = SteamNetworkingMessagesTransport.open(
                    loader.steamApiPath(),
                    new SteamNetworkingMessagesTransport.SessionListener() {
                        @Override
                        public void onSessionRequest(long remoteSteamId) {
                        }

                        @Override
                        public void onSessionFailed(long remoteSteamId, int endReason, String detail) {
                        }
                    }
            );
            transport.close();
        } finally {
            SteamAPI.shutdown();
        }
    }
}
