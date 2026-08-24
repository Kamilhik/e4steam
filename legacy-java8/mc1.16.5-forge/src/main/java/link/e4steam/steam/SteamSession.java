package link.e4steam.steam;

import link.e4steam.E4steamClient;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Java 8 lifecycle used by the pre-1.17 Forge ports. */
public final class SteamSession {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Object lock = new Object();
    private final int localPort;
    private final SteamAccessMode accessMode;
    private final byte[] token = new byte[SteamAddress.TOKEN_LENGTH];
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile State state = State.STARTING;
    private volatile SteamAddress address;
    private SteamRuntime.Activity activity;

    public SteamSession(int localPort, SteamAccessMode accessMode) {
        this.localPort = localPort;
        this.accessMode = accessMode;
        RANDOM.nextBytes(token);
    }

    public int localPort() { return localPort; }
    public SteamAccessMode accessMode() { return accessMode; }
    public SteamAddress address() { return address; }
    public State state() { return state; }

    public void startAsync() {
        if (!started.compareAndSet(false, true)) return;
        Thread thread = new Thread(new Runnable() {
            @Override public void run() { start(); }
        }, "e4steam-legacy-session-start");
        thread.setDaemon(true);
        thread.start();
    }

    private void start() {
        try {
            SteamRuntime runtime = SteamRuntime.get();
            SteamRuntime.Activity acquired = runtime.acquireActivity();
            synchronized (lock) {
                if (state != State.STARTING) {
                    acquired.close();
                    return;
                }
                activity = acquired;
            }
            runtime.awaitReady();
            runtime.startHosting(this, localPort, 0, token, accessMode);
            SteamAddress provisional = new SteamAddress(Long.parseUnsignedLong(runtime.steamId()), token);
            runtime.createHostLobby(this, accessMode, provisional).get(75, TimeUnit.SECONDS);
            address = provisional;
            state = State.STARTED;
            E4steamClient.sessionReady(this);
        } catch (Throwable throwable) {
            runtimeFailed(throwable);
        }
    }

    public CompletableFuture<Void> openInviteOverlayAsync() {
        try {
            return SteamRuntime.get().openHostInviteOverlay(this);
        } catch (IOException exception) {
            CompletableFuture<Void> failed = new CompletableFuture<Void>();
            failed.completeExceptionally(exception);
            return failed;
        }
    }

    public void stop() {
        SteamRuntime.Activity current;
        synchronized (lock) {
            if (state == State.STOPPED || state == State.STOPPING) return;
            state = State.STOPPING;
            SteamRuntime.get().stopHosting(this);
            current = activity;
            activity = null;
            state = State.STOPPED;
        }
        if (current != null) current.close();
    }

    void runtimeFailed(Throwable throwable) {
        state = State.UNHEALTHY;
        E4steamClient.sessionFailed(throwable);
        stop();
    }

    public enum State { STARTING, STARTED, STOPPING, STOPPED, UNHEALTHY }
}
