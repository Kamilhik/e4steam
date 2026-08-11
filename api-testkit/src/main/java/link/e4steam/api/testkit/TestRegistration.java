package link.e4steam.api.testkit;

import link.e4steam.api.Registration;

import java.util.concurrent.atomic.AtomicBoolean;

/** Idempotent registration with an observable close action for addon tests. */
public class TestRegistration implements Registration {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Runnable closeAction;

    /** Creates a registration that runs the action at most once. */
    public TestRegistration(Runnable closeAction) {
        if (closeAction == null) throw new NullPointerException("closeAction");
        this.closeAction = closeAction;
    }

    /** Creates a no-op registration. */
    public TestRegistration() {
        this(new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeAction.run();
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }
}
