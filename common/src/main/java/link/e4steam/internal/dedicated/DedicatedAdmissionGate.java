package link.e4steam.internal.dedicated;

import link.e4steam.api.ApiConstants;
import link.e4steam.steam.SteamMinecraftIdentity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** Mandatory fail-closed gates executed before addon policy and Minecraft login. */
public final class DedicatedAdmissionGate {
    private static final int MIN_NONCE_BYTES = 16;
    private static final int MAX_NONCE_BYTES = 64;
    private static final int MAX_TICKET_BYTES = 4_096;
    private static final int MAX_REPLAY_ENTRIES = 2_048;
    private static final long REPLAY_TTL_MILLIS = 60_000L;
    private static final int MAX_ATTEMPTS_PER_WINDOW = 8;
    private static final long RATE_WINDOW_MILLIS = 60_000L;

    private final Authenticator authenticator;
    private final CorePolicy policy;
    private final Map<String, Long> replay = new LinkedHashMap<String, Long>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_REPLAY_ENTRIES;
        }
    };
    private final ConcurrentHashMap<Long, RateWindow> attempts = new ConcurrentHashMap<>();

    public DedicatedAdmissionGate(Authenticator authenticator, CorePolicy policy) {
        this.authenticator = java.util.Objects.requireNonNull(authenticator, "authenticator");
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
    }

    public CompletionStage<Result> evaluate(Request request, long activeGeneration) {
        if (request == null) return completed(Result.deny("invalid-request"));
        if (!request.hasCredentials()) {
            request.destroy();
            return completed(Result.deny("invalid-request"));
        }
        if (request.generation != activeGeneration || activeGeneration <= 0L) {
            request.destroy();
            return completed(Result.deny("stale-generation"));
        }
        if (request.wireVersion != ApiConstants.WIRE_PROTOCOL_VERSION) {
            request.destroy();
            return completed(Result.deny("incompatible-wire"));
        }
        if (!allowAttempt(request.remoteSteamId, System.currentTimeMillis())) {
            request.destroy();
            return completed(Result.deny("rate-limited"));
        }
        if (!claimNonce(request.nonce, System.currentTimeMillis())) {
            request.destroy();
            return completed(Result.deny("replayed-hello"));
        }
        Arrays.fill(request.nonce, (byte) 0);
        byte[] ticket = request.takeTicket();
        CompletionStage<Boolean> validation;
        try {
            validation = authenticator.authenticate(
                    request.remoteSteamId,
                    ticket,
                    activeGeneration
            );
        } catch (VirtualMachineError | ThreadDeath fatal) {
            Arrays.fill(ticket, (byte) 0);
            throw fatal;
        } catch (Throwable failure) {
            Arrays.fill(ticket, (byte) 0);
            return completed(Result.deny("steam-auth-failed"));
        }
        Arrays.fill(ticket, (byte) 0);
        if (validation == null) return completed(Result.deny("steam-auth-failed"));
        CompletableFuture<Result> result = new CompletableFuture<>();
        validation.whenComplete((authenticated, failure) -> {
            if (failure != null || !Boolean.TRUE.equals(authenticated)) {
                result.complete(Result.deny("steam-auth-failed"));
                return;
            }
            Result mandatory = mandatoryAfterAuthentication(request.remoteSteamId);
            if (!mandatory.allowed()) {
                result.complete(mandatory);
                return;
            }
            CompletionStage<Boolean> addon;
            try {
                addon = policy.addonPolicy(request.remoteSteamId);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable addonFailure) {
                result.complete(Result.deny("addon-policy-failed"));
                return;
            }
            if (addon == null) {
                result.complete(Result.deny("addon-policy-failed"));
                return;
            }
            addon.whenComplete((allowed, addonFailure) -> {
                if (addonFailure != null || !Boolean.TRUE.equals(allowed)) {
                    result.complete(Result.deny("addon-policy-denied"));
                    return;
                }
                result.complete(Result.allow(
                        request.remoteSteamId,
                        activeGeneration,
                        SteamMinecraftIdentity.uuid(request.remoteSteamId),
                        SteamMinecraftIdentity.safeName(request.remoteSteamId)
                ));
            });
        });
        return result.thenApply(value -> value);
    }

    private Result mandatoryAfterAuthentication(long steamId) {
        try {
            if (policy.banned(steamId)) return Result.deny("banned");
            if (policy.whitelistRequired() && !policy.whitelisted(steamId)) {
                return Result.deny("not-whitelisted");
            }
            int capacity = policy.capacity();
            int players = policy.players();
            if (capacity < 1 || players < 0 || players >= capacity) {
                return Result.deny("capacity-reached");
            }
            return Result.allowCore();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            return Result.deny("core-policy-failed");
        }
    }

    private boolean claimNonce(byte[] nonce, long now) {
        if (nonce == null || nonce.length < MIN_NONCE_BYTES || nonce.length > MAX_NONCE_BYTES) {
            return false;
        }
        String key = digest(nonce);
        synchronized (replay) {
            java.util.Iterator<Map.Entry<String, Long>> iterator = replay.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue() <= now) iterator.remove();
            }
            if (replay.containsKey(key)) return false;
            replay.put(key, now + REPLAY_TTL_MILLIS);
            return true;
        }
    }

    private boolean allowAttempt(long steamId, long now) {
        if (steamId == 0L) return false;
        if (attempts.size() >= 1_024 && !attempts.containsKey(steamId)) {
            for (Map.Entry<Long, RateWindow> entry : attempts.entrySet()) {
                if (now - entry.getValue().started >= RATE_WINDOW_MILLIS) {
                    attempts.remove(entry.getKey(), entry.getValue());
                }
            }
            if (attempts.size() >= 1_024) return false;
        }
        RateWindow updated = attempts.compute(steamId, (id, current) -> {
            if (current == null || now - current.started >= RATE_WINDOW_MILLIS) {
                return new RateWindow(now, 1);
            }
            return new RateWindow(current.started, current.count + 1);
        });
        return updated.count <= MAX_ATTEMPTS_PER_WINDOW;
    }

    private static String digest(byte[] value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static CompletionStage<Result> completed(Result result) {
        return CompletableFuture.completedFuture(result);
    }

    public interface Authenticator {
        /**
         * Starts validation. The caller owns the resulting backend auth session
         * and must end it after every denial, timeout, disconnect or shutdown.
         */
        CompletionStage<Boolean> authenticate(long steamId, byte[] ticket, long generation);
    }

    public interface CorePolicy {
        boolean banned(long steamId);
        boolean whitelistRequired();
        boolean whitelisted(long steamId);
        int players();
        int capacity();
        CompletionStage<Boolean> addonPolicy(long steamId);
    }

    public static final class Request implements AutoCloseable {
        private final long remoteSteamId;
        private final long generation;
        private final int wireVersion;
        private final byte[] nonce;
        private byte[] ticket;

        public Request(long remoteSteamId, long generation, int wireVersion, byte[] nonce, byte[] ticket) {
            if (remoteSteamId == 0L) throw new IllegalArgumentException("steamId");
            if (nonce == null || nonce.length < MIN_NONCE_BYTES || nonce.length > MAX_NONCE_BYTES) {
                throw new IllegalArgumentException("nonce");
            }
            if (ticket == null || ticket.length == 0 || ticket.length > MAX_TICKET_BYTES) {
                throw new IllegalArgumentException("ticket");
            }
            this.remoteSteamId = remoteSteamId;
            this.generation = generation;
            this.wireVersion = wireVersion;
            this.nonce = nonce.clone();
            this.ticket = ticket.clone();
        }

        private synchronized byte[] takeTicket() {
            byte[] current = ticket;
            if (current == null) return new byte[0];
            ticket = null;
            return current;
        }

        private synchronized boolean hasCredentials() {
            return ticket != null && ticket.length > 0;
        }

        @Override public synchronized void close() {
            if (ticket != null) {
                Arrays.fill(ticket, (byte) 0);
                ticket = null;
            }
            Arrays.fill(nonce, (byte) 0);
        }

        @Override public String toString() {
            return "DedicatedAdmissionRequest{generation=" + generation
                    + ", wire=" + wireVersion + ", credentials=redacted}";
        }

        private void destroy() { close(); }
    }

    public static final class Result {
        private final boolean allowed;
        private final String reason;
        private final long internalSteamId;
        private final long generation;
        private final UUID minecraftUuid;
        private final String minecraftName;

        private Result(boolean allowed, String reason, long internalSteamId, long generation,
                       UUID minecraftUuid, String minecraftName) {
            this.allowed = allowed;
            this.reason = reason;
            this.internalSteamId = internalSteamId;
            this.generation = generation;
            this.minecraftUuid = minecraftUuid;
            this.minecraftName = minecraftName;
        }

        static Result allowCore() { return new Result(true, "", 0L, 0L, null, null); }
        static Result allow(long id, long generation, UUID uuid, String name) {
            return new Result(true, "", id, generation, uuid, name);
        }
        static Result deny(String reason) {
            return new Result(false, reason, 0L, 0L, null, null);
        }
        public boolean allowed() { return allowed; }
        public String reason() { return reason; }
        public long internalSteamId() { return internalSteamId; }
        public long generation() { return generation; }
        public UUID minecraftUuid() { return minecraftUuid; }
        public String minecraftName() { return minecraftName; }
        @Override public String toString() {
            return "DedicatedAdmissionResult{" + (allowed ? "allowed" : "denied:" + reason) + '}';
        }
    }

    private static final class RateWindow {
        private final long started;
        private final int count;
        private RateWindow(long started, int count) {
            this.started = started;
            this.count = count;
        }
    }
}
