package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SteamLifecycleTest {
    @Test
    void startStopAndRestartReuseLoadedLibraries() throws Exception {
        FakeSteamApi api = new FakeSteamApi();
        SteamLifecycle lifecycle = new SteamLifecycle(api);

        lifecycle.start();
        assertTrue(lifecycle.isRunning());
        lifecycle.close();
        assertFalse(lifecycle.isRunning());
        lifecycle.start();
        assertTrue(lifecycle.isRunning());
        lifecycle.close();

        assertEquals(1, api.loadCalls);
        assertEquals(2, api.initCalls);
        assertEquals(2, api.shutdownCalls);
    }

    @Test
    void steamDisconnectDuringGameIsObservable() throws Exception {
        FakeSteamApi api = new FakeSteamApi();
        SteamLifecycle lifecycle = new SteamLifecycle(api);
        lifecycle.start();
        api.wrapperRunning = false;
        assertFalse(lifecycle.isRunning());
        lifecycle.close();
        assertEquals(1, api.shutdownCalls);
    }

    @Test
    void failedSteamStartupCanBeRetried() {
        FakeSteamApi api = new FakeSteamApi();
        api.initResult = false;
        SteamLifecycle lifecycle = new SteamLifecycle(api);
        assertThrows(IOException.class, lifecycle::start);
        api.initResult = true;
        assertDoesNotThrow(lifecycle::start);
        lifecycle.close();
    }

    @Test
    void successfulInitializationIsNotRejectedByUnreliableNativeProbe() throws Exception {
        FakeSteamApi api = new FakeSteamApi();
        api.nativeClientRunning = false;
        SteamLifecycle lifecycle = new SteamLifecycle(
                api,
                new SteamClientHealthMonitor(false, 100L, 3)
        );

        lifecycle.start();

        assertTrue(lifecycle.isRunning());
        assertTrue(lifecycle.isHealthy(0L));
        assertEquals(0, api.nativeProbeCalls);
        lifecycle.close();
    }

    @Test
    void startupFailureExplainsPlatformSpecificProcessMismatch() {
        assertTrue(SteamLifecycle.initializationFailureMessage("Windows 11")
                .contains("same privilege level"));
        assertTrue(SteamLifecycle.initializationFailureMessage("Linux")
                .contains("sandboxed launcher"));
        assertTrue(SteamLifecycle.initializationFailureMessage("Mac OS X")
                .contains("same macOS user"));
        assertFalse(SteamLifecycle.initializationFailureMessage("Windows 11")
                .contains("non-Steam game"));
    }

    private static final class FakeSteamApi implements SteamApi {
        private boolean initResult = true;
        private boolean wrapperRunning;
        private boolean nativeClientRunning = true;
        private int loadCalls;
        private int initCalls;
        private int nativeProbeCalls;
        private int shutdownCalls;

        @Override
        public boolean loadLibraries(SteamNativeLibraryLoader loader) {
            loadCalls++;
            return true;
        }

        @Override
        public boolean init() {
            initCalls++;
            wrapperRunning = initResult;
            return initResult;
        }

        @Override
        public boolean isInitialized() {
            return wrapperRunning;
        }

        @Override
        public boolean isNativeSteamClientRunning() {
            nativeProbeCalls++;
            return nativeClientRunning;
        }

        @Override
        public void runCallbacks() {
        }

        @Override
        public void shutdown() {
            shutdownCalls++;
            wrapperRunning = false;
        }
    }
}
