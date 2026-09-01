package link.e4steam.retro;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RetroForgeSplashPolicyTest {
    private String originalOsName;
    private String originalOsArch;
    private String originalRelaunch;
    private String originalRelaunched;

    @Before
    public void saveProperties() {
        originalOsName = System.getProperty("os.name");
        originalOsArch = System.getProperty("os.arch");
        originalRelaunch = System.getProperty("e4steam.overlayRelaunch");
        originalRelaunched = System.getProperty("e4steam.overlayRelaunched");
    }

    @After
    public void restoreProperties() {
        restore("os.name", originalOsName);
        restore("os.arch", originalOsArch);
        restore("e4steam.overlayRelaunch", originalRelaunch);
        restore("e4steam.overlayRelaunched", originalRelaunched);
    }

    @Test
    public void leavesLegacyForgeSplashEnabledByDefaultOnSupportedUnix() {
        System.setProperty("os.name", "Linux");
        System.setProperty("os.arch", "amd64");
        System.clearProperty("e4steam.overlayRelaunch");
        assertFalse(RetroForgeSplashPolicy.shouldDisable());

        System.setProperty("e4steam.overlayRelaunch", "true");
        assertTrue(RetroForgeSplashPolicy.shouldDisable());
    }

    @Test
    public void leavesWindowsAndExplicitOptOutUntouched() {
        System.setProperty("os.name", "Windows 11");
        System.setProperty("os.arch", "amd64");
        System.clearProperty("e4steam.overlayRelaunch");
        assertFalse(RetroForgeSplashPolicy.shouldDisable());

        System.setProperty("os.name", "Mac OS X");
        System.setProperty("os.arch", "x86_64");
        System.setProperty("e4steam.overlayRelaunch", "false");
        assertFalse(RetroForgeSplashPolicy.shouldDisable());
    }

    @Test
    public void leavesLegacyForgeMacOsSplashUntouched() {
        System.setProperty("os.name", "Mac OS X");
        System.setProperty("os.arch", "x86_64");
        System.clearProperty("e4steam.overlayRelaunch");
        assertFalse(RetroForgeSplashPolicy.shouldDisable());
    }

    @Test
    public void doesNotRepairLegacyDisplayContextOnMacOs() {
        System.setProperty("os.name", "Mac OS X");
        System.setProperty("os.arch", "x86_64");
        System.clearProperty("e4steam.overlayRelaunch");
        System.clearProperty("e4steam.overlayRelaunched");
        assertFalse(RetroForgeSplashPolicy.shouldRepairLegacyDisplayContext());

        System.setProperty("e4steam.overlayRelaunched", "true");
        assertFalse(RetroForgeSplashPolicy.shouldRepairLegacyDisplayContext());

        System.setProperty("os.name", "Linux");
        assertFalse(RetroForgeSplashPolicy.shouldRepairLegacyDisplayContext());
    }

    private static void restore(String name, String value) {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
    }
}
