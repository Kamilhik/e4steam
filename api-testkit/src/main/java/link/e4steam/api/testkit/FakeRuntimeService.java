package link.e4steam.api.testkit;

import link.e4steam.api.runtime.RuntimeService;
import link.e4steam.api.runtime.RuntimeSnapshot;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Mutable test-only runtime snapshot with deterministic readiness completion. */
public final class FakeRuntimeService implements RuntimeService {
    private volatile RuntimeSnapshot snapshot;
    private final CompletableFuture<RuntimeSnapshot> readiness = new CompletableFuture<>();

    /** Creates a fake runtime with an initial safe snapshot. */
    public FakeRuntimeService(RuntimeSnapshot initialSnapshot) {
        if (initialSnapshot == null) throw new NullPointerException("initialSnapshot");
        this.snapshot = initialSnapshot;
    }

    /** Replaces the current snapshot and optionally completes readiness. */
    public void update(RuntimeSnapshot nextSnapshot, boolean ready) {
        if (nextSnapshot == null) throw new NullPointerException("nextSnapshot");
        snapshot = nextSnapshot;
        if (ready) readiness.complete(nextSnapshot);
    }

    @Override
    public RuntimeSnapshot snapshot() { return snapshot; }

    @Override
    public CompletionStage<RuntimeSnapshot> readiness() { return readiness; }
}
