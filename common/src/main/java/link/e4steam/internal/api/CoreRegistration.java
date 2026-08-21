package link.e4steam.internal.api;

import link.e4steam.api.Registration;

import java.util.concurrent.atomic.AtomicBoolean;

class CoreRegistration implements Registration {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Runnable closeAction;
    CoreRegistration(Runnable closeAction) { this.closeAction = closeAction; }
    @Override public void close() { if (closed.compareAndSet(false, true) && closeAction != null) closeAction.run(); }
    @Override public boolean isClosed() { return closed.get(); }
}
