package link.e4steam.api.testkit;

import link.e4steam.api.Registration;
import link.e4steam.api.ResourceScope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Deterministic parent scope and leak detector for addon contract tests. */
public final class TestResourceScope implements ResourceScope {
    private final Object lock = new Object();
    private final List<Registration> children = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    @Override
    public <T extends Registration> T own(T child) {
        if (child == null) throw new NullPointerException("child");
        synchronized (lock) {
            if (closed.get()) {
                child.close();
                throw new IllegalStateException("Resource scope is closed");
            }
            children.add(child);
        }
        return child;
    }

    @Override
    public int openResourceCount() {
        synchronized (lock) {
            int open = 0;
            for (Registration child : children) {
                if (!child.isClosed()) open++;
            }
            return open;
        }
    }

    /** Fails the test if an owned registration remains open. */
    public void assertNoLeaks() {
        int open = openResourceCount();
        if (open != 0) {
            throw new AssertionError("Open addon resources: " + open);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        List<Registration> closing;
        synchronized (lock) {
            closing = new ArrayList<>(children);
        }
        for (int index = closing.size() - 1; index >= 0; index--) {
            closing.get(index).close();
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }
}
