package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativePlatformTest {
    @Test
    void normalizesDocumentedOsAndArchitectureAliases() throws Exception {
        Object[][] cases = {
                {" Windows 11 ", "AMD64", "windows-x64"},
                {"windows", "x64", "windows-x64"},
                {"Linux", "x86_64", "linux-x64"},
                {"LINUX 6.8", "x86-64", "linux-x64"},
                {"Mac OS X", "amd64", "macos-x64"},
                {" macOS 15 ", "x86_64", "macos-x64"},
                {"OS X", "arm64", "macos-arm64"},
                {"Darwin", "AARCH64", "macos-arm64"}
        };

        for (Object[] value : cases) {
            NativePlatform platform = NativePlatform.normalize(
                    (String) value[0],
                    (String) value[1]
            );
            assertEquals(value[2], platform.directoryName(), value[0] + "/" + value[1]);
        }
    }

    @Test
    void rejectsUnknownOrMisleadingOsNames() {
        String[] invalid = {"", "Plan9", "notwindows", "fake macOS", null};
        for (String os : invalid) {
            assertThrows(IOException.class, () -> NativePlatform.normalize(os, "x86_64"));
        }
    }

    @Test
    void rejectsUnknownAndUnsupportedArchitecturesBeforeLoading() {
        String[] invalid = {"", "x86", "i686", "ppc64le", null};
        for (String architecture : invalid) {
            assertThrows(
                    IOException.class,
                    () -> NativePlatform.normalize("macOS", architecture)
            );
        }
        assertThrows(IOException.class, () -> NativePlatform.normalize("Windows 11", "arm64"));
        assertThrows(IOException.class, () -> NativePlatform.normalize("Linux", "aarch64"));
    }
}
