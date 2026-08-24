package link.e4steam.api;

/** Idempotently closeable API-owned resource. */
public interface Registration extends AutoCloseable {
    /** Closes the registration; repeated calls have no effect. */
    @Override
    void close();

    /** Returns whether the registration has been closed. */
    boolean isClosed();
}
