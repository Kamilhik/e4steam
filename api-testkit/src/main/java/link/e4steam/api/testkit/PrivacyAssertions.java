package link.e4steam.api.testkit;

/** Canary helpers for proving that public DTOs and diagnostics omit secrets. */
public final class PrivacyAssertions {
    private PrivacyAssertions() {
    }

    /** Fails if the inspected text contains any non-empty canary value. */
    public static void assertNoSecrets(String inspected, String... canaries) {
        if (inspected == null || canaries == null) throw new NullPointerException("privacy input");
        for (String canary : canaries) {
            if (canary != null && !canary.isEmpty() && inspected.contains(canary)) {
                throw new AssertionError("Secret canary leaked into inspected output");
            }
        }
    }
}
