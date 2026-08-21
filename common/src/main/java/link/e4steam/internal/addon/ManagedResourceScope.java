package link.e4steam.internal.addon;

import link.e4steam.api.Registration;
import link.e4steam.api.ResourceScope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Thread-safe parent ownership with idempotent reverse-order shutdown. */
public final class ManagedResourceScope implements ResourceScope {
    private final Object lock = new Object();
    private final List<Registration> children = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    @Override
    public <T extends Registration> T own(T child) {
        if (child == null) throw new NullPointerException("child");
        synchronized (lock) {
            if (!closed.get()) {
                children.add(child);
                return child;
            }
        }
        child.close();
        throw new IllegalStateException("Resource scope is closed");
    }

    @Override
    public int openResourceCount() {
        synchronized (lock) { return children.size(); }
    }

    @Override public boolean isClosed() { return closed.get(); }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        List<Registration> closing;
        synchronized (lock) {
            closing = new ArrayList<>(children);
            children.clear();
        }
        RuntimeException first = null;
        for (int index = closing.size() - 1; index >= 0; index--) {
            try {
                closing.get(index).close();
            } catch (RuntimeException failure) {
                if (first == null) first = failure;
                else first.addSuppressed(failure);
            }
        }
        if (first != null) throw first;
    }
}
