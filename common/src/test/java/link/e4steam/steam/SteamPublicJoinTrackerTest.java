package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SteamPublicJoinTrackerTest {
    @Test
    void reportsTheCompleteJoinLifecycleWithoutExposingTheTarget() {
        SteamPublicJoinTracker tracker = new SteamPublicJoinTracker();
        tracker.begin(42L);
        assertEquals(SteamPublicJoinTracker.Phase.JOINING_STEAM_TARGET,
                tracker.snapshot(42L).phase());
        tracker.authenticating(42L);
        assertEquals(SteamPublicJoinTracker.Phase.AUTHENTICATING,
                tracker.snapshot(42L).phase());
        tracker.connecting(42L);
        assertEquals(SteamPublicJoinTracker.Phase.CONNECTING_MINECRAFT,
                tracker.snapshot(42L).phase());
        tracker.active(42L);
        assertEquals(SteamPublicJoinTracker.Phase.ACTIVE,
                tracker.snapshot(42L).phase());
        assertEquals("SteamPublicJoinSnapshot{phase=ACTIVE, target=opaque}",
                tracker.snapshot(42L).toString());
    }

    @Test
    void staleCallbacksCannotAdvanceAReplacementAttempt() {
        SteamPublicJoinTracker tracker = new SteamPublicJoinTracker();
        tracker.begin(1L);
        tracker.begin(2L);
        tracker.active(1L);
        assertEquals(SteamPublicJoinTracker.Phase.IDLE,
                tracker.snapshot(1L).phase());
        assertEquals(SteamPublicJoinTracker.Phase.JOINING_STEAM_TARGET,
                tracker.snapshot(2L).phase());
    }

    @Test
    void terminalFailureCannotBeOverwrittenByLateSuccess() {
        SteamPublicJoinTracker tracker = new SteamPublicJoinTracker();
        tracker.begin(42L);
        tracker.fail(42L, SteamPublicJoinTracker.Phase.INCOMPATIBLE,
                "minecraft-version");
        tracker.connecting(42L);
        tracker.active(42L);
        assertEquals(SteamPublicJoinTracker.Phase.INCOMPATIBLE,
                tracker.snapshot(42L).phase());
        assertEquals("minecraft-version", tracker.snapshot(42L).detailCode());
    }

    @Test
    void cancelIsTerminalAndTargetsAreIsolated() {
        SteamPublicJoinTracker tracker = new SteamPublicJoinTracker();
        tracker.begin(42L);
        tracker.cancel(42L);
        tracker.active(42L);
        assertEquals(SteamPublicJoinTracker.Phase.CANCELLED,
                tracker.snapshot(42L).phase());
        assertEquals(SteamPublicJoinTracker.Phase.IDLE,
                tracker.snapshot(43L).phase());
    }
}
