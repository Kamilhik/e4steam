package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamCustomAccessGateTest {
    private static final String MODE = "example:public";

    @Test
    void defaultsToFailClosedAndRemovesOnlyItsOwnProvider() throws Exception {
        assertFalse(SteamCustomAccessGate.isRegistered(MODE));
        assertFalse(SteamCustomAccessGate.evaluate(MODE, 42L)
                .toCompletableFuture().get(1, TimeUnit.SECONDS));

        AutoCloseable registration = SteamCustomAccessGate.install(
                new SteamCustomAccessGate.Provider() {
                    @Override public boolean isRegistered(String modeId) {
                        return MODE.equals(modeId);
                    }

                    @Override public java.util.concurrent.CompletionStage<Boolean> evaluate(
                            String modeId, long remoteSteamId
                    ) {
                        return CompletableFuture.completedFuture(
                                MODE.equals(modeId) && remoteSteamId == 42L);
                    }
                });
        try {
            assertTrue(SteamCustomAccessGate.isRegistered(MODE));
            assertTrue(SteamCustomAccessGate.evaluate(MODE, 42L)
                    .toCompletableFuture().get(1, TimeUnit.SECONDS));
            assertFalse(SteamCustomAccessGate.evaluate(MODE, 43L)
                    .toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            registration.close();
        }

        assertFalse(SteamCustomAccessGate.isRegistered(MODE));
    }

    @Test
    void malformedIdsAndProviderFailuresStayDenied() throws Exception {
        assertFalse(SteamCustomAccessGate.isRegistered("bad"));
        assertFalse(SteamCustomAccessGate.evaluate("bad", 42L)
                .toCompletableFuture().get(1, TimeUnit.SECONDS));

        AutoCloseable registration = SteamCustomAccessGate.install(
                new SteamCustomAccessGate.Provider() {
                    @Override public boolean isRegistered(String modeId) {
                        throw new IllegalStateException("provider failed");
                    }

                    @Override public java.util.concurrent.CompletionStage<Boolean> evaluate(
                            String modeId, long remoteSteamId
                    ) {
                        throw new IllegalStateException("provider failed");
                    }
                });
        try {
            assertFalse(SteamCustomAccessGate.isRegistered(MODE));
            assertFalse(SteamCustomAccessGate.evaluate(MODE, 42L)
                    .toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            registration.close();
        }
    }
}
