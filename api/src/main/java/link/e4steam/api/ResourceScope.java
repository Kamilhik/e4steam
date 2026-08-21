package link.e4steam.api;

/** Parent resource that closes addon registrations in reverse ownership order. */
public interface ResourceScope extends Registration {
    /**
     * Transfers a child registration to this scope.
     *
     * @param child non-null child resource
     * @param <T> registration type
     * @return the same child for fluent registration
     */
    <T extends Registration> T own(T child);

    /** Returns the number of currently open child registrations. */
    int openResourceCount();
}
