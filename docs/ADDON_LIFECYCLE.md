# Addon lifecycle

The public states are `DISCOVERED`, `VALIDATING`, `INITIALIZING`, `ACTIVE`,
`DISABLED`, `FAILED` and `STOPPED`.

The intended production order is:

1. A normal Minecraft mod loader discovers metadata; e4steam never scans or
   downloads arbitrary JAR files.
2. Core validates identifiers, API ranges, duplicate ids, dependencies and
   cycles in deterministic order.
3. Core grants an allowlisted subset of requested capabilities.
4. `initialize()` runs with a finite time/error budget outside internal locks
   and native callback threads.
5. Registration contracts freeze before sessions negotiate protocols.
6. Failure is isolated to the addon; its parent `ResourceScope` closes children
   in reverse order.
7. Shutdown closes session, addon and runtime scopes deterministically.

The `0.1.0` public contracts, resource scope and testkit are implemented. The
loader discovery/production lifecycle engine is a follow-up and is not active
in the current core. No addon callback is therefore silently enabled by this
foundation alone.
