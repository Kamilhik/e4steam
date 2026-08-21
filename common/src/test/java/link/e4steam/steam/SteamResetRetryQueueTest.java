package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamResetRetryQueueTest {
    private static SteamResetRetryQueue<String> queue(int capacity, int attempts) {
        return new SteamResetRetryQueue<>(capacity, attempts, 1_000, 10, 100, ignored -> 0);
    }

    @Test
    void temporaryFailureThenSuccessHasTerminalSentState() {
        SteamResetRetryQueue<String> queue = queue(4, 4);
        SteamResetRetryQueue.Entry<String> entry = queue.offerAfterTemporaryFailure(
                1, 2, new byte[]{7}, "bridge", 11, 100
        ).entry();

        assertNull(queue.poll(11, 109));
        assertSame(entry, queue.poll(11, 110));
        assertEquals(
                SteamResetRetryQueue.State.SENT,
                queue.complete(entry, SteamResetRetryQueue.SendOutcome.SUCCESS, 110)
        );
        assertTrue(queue.isEmpty());
    }

    @Test
    void repeatedTemporaryFailuresUseExponentialBoundedSchedule() {
        SteamResetRetryQueue<String> queue = queue(4, 5);
        SteamResetRetryQueue.Entry<String> entry = queue.offerAfterTemporaryFailure(
                1, 2, new byte[]{7}, "bridge", 11, 100
        ).entry();

        assertSame(entry, queue.poll(11, 110));
        assertEquals(
                SteamResetRetryQueue.State.RETRY_SCHEDULED,
                queue.complete(entry, SteamResetRetryQueue.SendOutcome.TEMPORARY_FAILURE, 110)
        );
        assertEquals(130, entry.nextAttemptMillis());
        assertSame(entry, queue.poll(11, 130));
        queue.complete(entry, SteamResetRetryQueue.SendOutcome.TEMPORARY_FAILURE, 130);
        assertEquals(170, entry.nextAttemptMillis());
    }

    @Test
    void permanentFailureIsNeverRetried() {
        SteamResetRetryQueue<String> queue = queue(4, 4);
        SteamResetRetryQueue.Entry<String> entry = queue.offerAfterTemporaryFailure(
                1, 2, new byte[]{7}, "bridge", 11, 100
        ).entry();

        assertSame(entry, queue.poll(11, 110));
        assertEquals(
                SteamResetRetryQueue.State.PERMANENT_FAILURE,
                queue.complete(entry, SteamResetRetryQueue.SendOutcome.PERMANENT_FAILURE, 110)
        );
        assertNull(queue.poll(11, 1_000));
    }

    @Test
    void capacityAndDuplicateResetAreBounded() {
        SteamResetRetryQueue<String> queue = queue(1, 4);
        String bridge = new String("bridge");
        SteamResetRetryQueue.Offer<String> first = queue.offerAfterTemporaryFailure(
                1, 2, new byte[]{1}, bridge, 11, 100
        );
        SteamResetRetryQueue.Offer<String> duplicate = queue.offerAfterTemporaryFailure(
                1, 2, new byte[]{2}, bridge, 11, 100
        );
        SteamResetRetryQueue.Offer<String> overflow = queue.offerAfterTemporaryFailure(
                1, 3, new byte[]{3}, bridge, 11, 100
        );

        assertEquals(SteamResetRetryQueue.OfferStatus.ACCEPTED, first.status());
        assertEquals(SteamResetRetryQueue.OfferStatus.DUPLICATE, duplicate.status());
        assertSame(first.entry(), duplicate.entry());
        assertEquals(SteamResetRetryQueue.OfferStatus.FULL, overflow.status());
        assertEquals(1, queue.size());
    }

    @Test
    void staleWorkerGenerationCannotSendIntoNewSession() {
        SteamResetRetryQueue<String> queue = queue(4, 4);
        SteamResetRetryQueue.Entry<String> entry = queue.offerAfterTemporaryFailure(
                1, 2, new byte[]{7}, "bridge", 11, 100
        ).entry();

        assertSame(entry, queue.poll(12, 100));
        assertEquals(SteamResetRetryQueue.State.CANCELLED_STALE_GENERATION, entry.state());
        assertTrue(queue.isEmpty());
    }

    @Test
    void disconnectAndShutdownRemovePendingEntries() {
        SteamResetRetryQueue<String> queue = queue(4, 4);
        String firstBridge = new String("first");
        String secondBridge = new String("second");
        queue.offerAfterTemporaryFailure(1, 1, new byte[]{1}, firstBridge, 11, 100);
        queue.offerAfterTemporaryFailure(2, 2, new byte[]{2}, secondBridge, 11, 100);

        List<SteamResetRetryQueue.Entry<String>> disconnected = queue.purge(firstBridge);
        assertEquals(1, disconnected.size());
        assertEquals(
                SteamResetRetryQueue.State.CANCELLED_STALE_CONNECTION,
                disconnected.get(0).state()
        );
        List<SteamResetRetryQueue.Entry<String>> shutdown = queue.cancelAll();
        assertEquals(1, shutdown.size());
        assertEquals(SteamResetRetryQueue.State.CANCELLED_SHUTDOWN, shutdown.get(0).state());
        assertTrue(queue.isEmpty());
    }

    @Test
    void retryBudgetExhaustionIsTerminal() {
        SteamResetRetryQueue<String> queue = queue(4, 2);
        SteamResetRetryQueue.Entry<String> entry = queue.offerAfterTemporaryFailure(
                1, 2, new byte[]{7}, "bridge", 11, 100
        ).entry();

        assertSame(entry, queue.poll(11, 110));
        assertEquals(
                SteamResetRetryQueue.State.EXHAUSTED,
                queue.complete(entry, SteamResetRetryQueue.SendOutcome.TEMPORARY_FAILURE, 110)
        );
        assertTrue(queue.isEmpty());
    }

    @Test
    void payloadIsCopiedAndNeverIncludedInDiagnostics() {
        SteamResetRetryQueue<String> queue = queue(4, 4);
        byte[] secret = "private-token".getBytes(StandardCharsets.UTF_8);
        SteamResetRetryQueue.Entry<String> entry = queue.offerAfterTemporaryFailure(
                1, 2, secret, "bridge", 11, 100
        ).entry();
        secret[0] = 'X';

        ByteBuffer target = ByteBuffer.allocate(32);
        entry.putPayload(target);
        byte[] actual = new byte[target.position()];
        target.flip();
        target.get(actual);
        assertArrayEquals("private-token".getBytes(StandardCharsets.UTF_8), actual);
        assertFalse(entry.toString().contains("private-token"));
    }

    @Test
    void concurrentDuplicateAdmissionCreatesOneOwner() throws Exception {
        SteamResetRetryQueue<Object> queue = new SteamResetRetryQueue<>(
                4, 4, 1_000, 10, 100, ignored -> 0
        );
        Object bridge = new Object();
        int workers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();
        for (int index = 0; index < workers; index++) {
            pool.submit(() -> {
                start.await();
                SteamResetRetryQueue.Offer<Object> offer = queue.offerAfterTemporaryFailure(
                        1, 2, new byte[]{7}, bridge, 11, 100
                );
                if (offer.status() == SteamResetRetryQueue.OfferStatus.ACCEPTED) {
                    accepted.incrementAndGet();
                } else if (offer.status() == SteamResetRetryQueue.OfferStatus.DUPLICATE) {
                    duplicate.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, accepted.get());
        assertEquals(workers - 1, duplicate.get());
        assertEquals(1, queue.size());
    }
}
