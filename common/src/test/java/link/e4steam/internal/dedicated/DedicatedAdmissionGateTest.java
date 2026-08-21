package link.e4steam.internal.dedicated;

import link.e4steam.api.ApiConstants;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedicatedAdmissionGateTest {
    @Test
    void staleGenerationAndInvalidAuthNeverReachAddonPolicy() {
        AtomicBoolean addonCalled = new AtomicBoolean();
        DedicatedAdmissionGate gate = gate(false, false, true, 0, 8, addonCalled);

        DedicatedAdmissionGate.Result stale = gate.evaluate(
                request(11L, 1L, (byte) 1), 2L).toCompletableFuture().join();
        assertFalse(stale.allowed());
        assertEquals("stale-generation", stale.reason());

        DedicatedAdmissionGate.Result rejected = gate.evaluate(
                request(11L, 2L, (byte) 2), 2L).toCompletableFuture().join();
        assertFalse(rejected.allowed());
        assertEquals("steam-auth-failed", rejected.reason());
        assertFalse(addonCalled.get());
    }

    @Test
    void replaysAreRejectedBeforeSecondAuthentication() {
        AtomicBoolean addonCalled = new AtomicBoolean();
        DedicatedAdmissionGate gate = gate(true, false, true, 0, 8, addonCalled);
        byte[] nonce = nonce((byte) 7);
        DedicatedAdmissionGate.Request first = new DedicatedAdmissionGate.Request(
                22L, 4L, ApiConstants.WIRE_PROTOCOL_VERSION, nonce, new byte[]{1, 2});
        assertTrue(gate.evaluate(first, 4L).toCompletableFuture().join().allowed());
        DedicatedAdmissionGate.Request second = new DedicatedAdmissionGate.Request(
                22L, 4L, ApiConstants.WIRE_PROTOCOL_VERSION, nonce, new byte[]{1, 2});
        DedicatedAdmissionGate.Result replay =
                gate.evaluate(second, 4L).toCompletableFuture().join();
        assertFalse(replay.allowed());
        assertEquals("replayed-hello", replay.reason());
    }

    @Test
    void bansWhitelistCapacityAndAddonPolicyRemainMandatory() {
        assertEquals("banned", gate(true, true, true, 0, 8, new AtomicBoolean())
                .evaluate(request(31L, 3L, (byte) 1), 3L).toCompletableFuture().join().reason());
        assertEquals("not-whitelisted", gate(true, false, false, 0, 8, new AtomicBoolean())
                .evaluate(request(32L, 3L, (byte) 2), 3L).toCompletableFuture().join().reason());
        assertEquals("capacity-reached", gate(true, false, true, 8, 8, new AtomicBoolean())
                .evaluate(request(33L, 3L, (byte) 3), 3L).toCompletableFuture().join().reason());
    }

    @Test
    void addonPolicyFailureIsReturnedForCallerOwnedAuthCleanup() {
        DedicatedAdmissionGate.Authenticator authenticator = new DedicatedAdmissionGate.Authenticator() {
            @Override public java.util.concurrent.CompletionStage<Boolean> authenticate(
                    long steamId, byte[] ticket, long generation) {
                return CompletableFuture.completedFuture(true);
            }
        };
        DedicatedAdmissionGate.CorePolicy policy = new DedicatedAdmissionGate.CorePolicy() {
            @Override public boolean banned(long steamId) { return false; }
            @Override public boolean whitelistRequired() { return false; }
            @Override public boolean whitelisted(long steamId) { return false; }
            @Override public int players() { return 0; }
            @Override public int capacity() { return 8; }
            @Override public java.util.concurrent.CompletionStage<Boolean> addonPolicy(long steamId) {
                CompletableFuture<Boolean> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException("test failure"));
                return failed;
            }
        };
        DedicatedAdmissionGate.Result result = new DedicatedAdmissionGate(authenticator, policy)
                .evaluate(request(41L, 5L, (byte) 4), 5L)
                .toCompletableFuture().join();
        assertFalse(result.allowed());
        assertEquals("addon-policy-denied", result.reason());
    }

    @Test
    void attemptRateIsBoundedBeforeAuthentication() {
        AtomicInteger authenticated = new AtomicInteger();
        DedicatedAdmissionGate gate = new DedicatedAdmissionGate(
                new DedicatedAdmissionGate.Authenticator() {
                    @Override public java.util.concurrent.CompletionStage<Boolean> authenticate(
                            long steamId, byte[] ticket, long generation) {
                        authenticated.incrementAndGet();
                        return CompletableFuture.completedFuture(false);
                    }
                },
                new DedicatedAdmissionGate.CorePolicy() {
                    @Override public boolean banned(long steamId) { return false; }
                    @Override public boolean whitelistRequired() { return false; }
                    @Override public boolean whitelisted(long steamId) { return false; }
                    @Override public int players() { return 0; }
                    @Override public int capacity() { return 8; }
                    @Override public java.util.concurrent.CompletionStage<Boolean> addonPolicy(long steamId) {
                        return CompletableFuture.completedFuture(true);
                    }
                });
        for (int attempt = 0; attempt < 8; attempt++) {
            assertEquals("steam-auth-failed", gate.evaluate(
                    request(51L, 6L, (byte) (10 + attempt)), 6L)
                    .toCompletableFuture().join().reason());
        }
        DedicatedAdmissionGate.Result limited = gate.evaluate(
                request(51L, 6L, (byte) 30), 6L).toCompletableFuture().join();
        assertEquals("rate-limited", limited.reason());
        assertEquals(8, authenticated.get());
    }

    private static DedicatedAdmissionGate gate(
            boolean auth,
            boolean banned,
            boolean whitelisted,
            int players,
            int capacity,
            AtomicBoolean addonCalled
    ) {
        return new DedicatedAdmissionGate(
                new DedicatedAdmissionGate.Authenticator() {
                    @Override public java.util.concurrent.CompletionStage<Boolean> authenticate(
                            long steamId, byte[] ticket, long generation) {
                        return CompletableFuture.completedFuture(auth);
                    }
                },
                new DedicatedAdmissionGate.CorePolicy() {
                    @Override public boolean banned(long steamId) { return banned; }
                    @Override public boolean whitelistRequired() { return true; }
                    @Override public boolean whitelisted(long steamId) { return whitelisted; }
                    @Override public int players() { return players; }
                    @Override public int capacity() { return capacity; }
                    @Override public java.util.concurrent.CompletionStage<Boolean> addonPolicy(long steamId) {
                        addonCalled.set(true);
                        return CompletableFuture.completedFuture(true);
                    }
                }
        );
    }

    private static DedicatedAdmissionGate.Request request(long id, long generation, byte marker) {
        return new DedicatedAdmissionGate.Request(
                id,
                generation,
                ApiConstants.WIRE_PROTOCOL_VERSION,
                nonce(marker),
                new byte[]{9, 8, 7}
        );
    }

    private static byte[] nonce(byte marker) {
        byte[] result = new byte[16];
        java.util.Arrays.fill(result, marker);
        return result;
    }

    @Test
    void requestCloseIsIdempotentAndRedacted() {
        DedicatedAdmissionGate.Request request = request(61L, 7L, (byte) 61);
        assertTrue(request.toString().contains("credentials=redacted"));
        request.close();
        request.close();
        DedicatedAdmissionGate.Result result = new DedicatedAdmissionGate(
                (steamId, ticket, generation) -> CompletableFuture.completedFuture(true),
                new DedicatedAdmissionGate.CorePolicy() {
                    @Override public boolean banned(long steamId) { return false; }
                    @Override public boolean whitelistRequired() { return false; }
                    @Override public boolean whitelisted(long steamId) { return true; }
                    @Override public int players() { return 0; }
                    @Override public int capacity() { return 8; }
                    @Override public java.util.concurrent.CompletionStage<Boolean> addonPolicy(long steamId) {
                        return CompletableFuture.completedFuture(true);
                    }
                })
                .evaluate(request, 7L).toCompletableFuture().join();
        assertFalse(result.allowed());
        assertEquals("invalid-request", result.reason());
    }
}
