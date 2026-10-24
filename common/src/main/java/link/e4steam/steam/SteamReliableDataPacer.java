package link.e4steam.steam;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Per-bridge token bucket for loaders that can enqueue a large Minecraft
 * login/chunk burst before Steam exposes useful native queue telemetry.
 */
final class SteamReliableDataPacer<B> {
    private static final class Bucket {
        private double availableBytes;
        private long updatedAtNanos;

        private Bucket(double availableBytes, long updatedAtNanos) {
            this.availableBytes = availableBytes;
            this.updatedAtNanos = updatedAtNanos;
        }
    }

    private final double bytesPerNanosecond;
    private final int burstBytes;
    private final Map<B, Bucket> buckets = new IdentityHashMap<>();

    SteamReliableDataPacer(long bytesPerSecond, int burstBytes) {
        if (bytesPerSecond <= 0 || burstBytes <= 0) {
            throw new IllegalArgumentException("Pacer limits must be positive");
        }
        this.bytesPerNanosecond = bytesPerSecond / 1_000_000_000.0d;
        this.burstBytes = burstBytes;
    }

    boolean tryAcquire(B bridge, int bytes, long nowNanos) {
        if (bridge == null || bytes <= 0 || bytes > burstBytes) {
            return false;
        }
        Bucket bucket = buckets.get(bridge);
        if (bucket == null) {
            bucket = new Bucket(burstBytes, nowNanos);
            buckets.put(bridge, bucket);
        } else if (nowNanos > bucket.updatedAtNanos) {
            long elapsed = nowNanos - bucket.updatedAtNanos;
            bucket.availableBytes = Math.min(
                    burstBytes,
                    bucket.availableBytes + elapsed * bytesPerNanosecond
            );
            bucket.updatedAtNanos = nowNanos;
        }
        if (bucket.availableBytes < bytes) {
            return false;
        }
        bucket.availableBytes -= bytes;
        return true;
    }

    void remove(B bridge) {
        buckets.remove(bridge);
    }

    void clear() {
        buckets.clear();
    }
}
