# Addon lifecycle

The production lifecycle is implemented as:

```text
DISCOVERED -> VALIDATING -> INITIALIZING -> ACTIVE -> STOPPED
                       \-> DISABLED / FAILED
```

1. The installed mod loader or Java `ServiceLoader` supplies candidates;
   e4steam never scans or downloads arbitrary JAR files.
2. Core validates IDs, API ranges, duplicates, dependencies, cycles and the
   allowlisted subset of requested capabilities in deterministic order.
3. `initialize()` runs on a bounded lifecycle executor, outside native callback
   threads and core locks, with a finite timeout.
4. Registrations freeze before sessions negotiate addon channels.
5. Callback failure disables only that addon. Its parent `ResourceScope`
   closes tasks, subscriptions and registrations in reverse ownership order.
6. Runtime shutdown publishes a stopping event and closes addon, network,
   session and scheduler resources idempotently.

Loader adapters start the same lifecycle on Fabric, Quilt-compatible Fabric,
Forge and NeoForge. The testkit provides deterministic schedulers, event
recording and failure injection for addon CI.
