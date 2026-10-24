package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamReliableDataPacerTest {
    @Test
    void boundsBurstAndRefillsPerBridge() {
        SteamReliableDataPacer<Object> pacer = new SteamReliableDataPacer<>(1_000, 100);
        Object first = new Object();
        Object second = new Object();
        long start = 10_000_000_000L;

        assertTrue(pacer.tryAcquire(first, 60, start));
        assertFalse(pacer.tryAcquire(first, 60, start));
        assertTrue(pacer.tryAcquire(first, 60, start + 20_000_000L));
        assertTrue(pacer.tryAcquire(second, 100, start));
    }

    @Test
    void removingBridgeStartsWithAFullBucket() {
        SteamReliableDataPacer<Object> pacer = new SteamReliableDataPacer<>(1_000, 100);
        Object bridge = new Object();

        assertTrue(pacer.tryAcquire(bridge, 100, 0));
        assertFalse(pacer.tryAcquire(bridge, 1, 0));
        pacer.remove(bridge);
        assertTrue(pacer.tryAcquire(bridge, 100, 0));
    }
}
