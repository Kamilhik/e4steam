package link.e4steam.agent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StdinCaptureAgentTest {
    private String originalOs;
    private String originalRelaunch;
    private String originalEarlyWindow;

    @Before
    public void rememberProperties() {
        originalOs = System.getProperty("os.name");
        originalRelaunch = System.getProperty("e4steam.overlayRelaunch");
        originalEarlyWindow = System.getProperty("fml.earlyprogresswindow");
        System.clearProperty("fml.earlyprogresswindow");
    }

    @After
    public void restoreProperties() {
        restore("os.name", originalOs);
        restore("e4steam.overlayRelaunch", originalRelaunch);
        restore("fml.earlyprogresswindow", originalEarlyWindow);
    }

    @Test
    public void disablesRetroForgeEarlyWindowOnLinux() {
        System.setProperty("os.name", "Linux");
        System.setProperty("e4steam.overlayRelaunch", "true");

        StdinCaptureAgent.prepareRetroForgeWindowing();

        assertEquals("false", System.getProperty("fml.earlyprogresswindow"));
    }

    @Test
    public void disablesRetroForgeEarlyWindowByDefaultOnUnix() {
        System.setProperty("os.name", "Linux");
        System.clearProperty("e4steam.overlayRelaunch");

        StdinCaptureAgent.prepareRetroForgeWindowing();

        assertEquals("false", System.getProperty("fml.earlyprogresswindow"));
    }

    @Test
    public void leavesWindowsWindowingUntouched() {
        System.setProperty("os.name", "Windows 11");
        System.setProperty("e4steam.overlayRelaunch", "true");

        StdinCaptureAgent.prepareRetroForgeWindowing();

        assertNull(System.getProperty("fml.earlyprogresswindow"));
    }

    @Test
    public void leavesForgeWindowingUntouchedWhenRelaunchIsDisabled() {
        System.setProperty("os.name", "Mac OS X");
        System.setProperty("e4steam.overlayRelaunch", "false");

        StdinCaptureAgent.prepareRetroForgeWindowing();

        assertNull(System.getProperty("fml.earlyprogresswindow"));
    }

    @Test
    public void treatsMissingRelaunchPropertyAsEnabled() {
        assertEquals(true, StdinCaptureAgent.relaunchEnabled(null));
        assertEquals(true, StdinCaptureAgent.relaunchEnabled("true"));
        assertEquals(false, StdinCaptureAgent.relaunchEnabled("false"));
    }

    private static void restore(String name, String value) {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
    }
}
