# Addon lifecycle

e4steam discovers addons through the installed mod loader or Java
`ServiceLoader` metadata. Core does not search arbitrary folders, download JARs
or execute an addon received from another player.

## State machine

```text
DISCOVERED -> VALIDATING -> INITIALIZING -> ACTIVE -> STOPPED
                       \-> DISABLED
                       \-> FAILED
```

| State | Meaning |
| --- | --- |
| `DISCOVERED` | Loader supplied a candidate and descriptor |
| `VALIDATING` | IDs, versions, dependencies and capabilities are checked |
| `INITIALIZING` | Entry point runs with its scoped `AddonContext` |
| `ACTIVE` | Registrations are available for the current runtime generation |
| `DISABLED` | Policy or compatibility prevented activation |
| `FAILED` | Addon code failed during bounded initialization/callback handling |
| `STOPPED` | Runtime closed and owned resources were released |

## 1. Discovery

The loader adapter supplies an `E4steamAddonEntrypoint`. Its `descriptor()` must
be safe to read before initialization. Keep descriptor construction free of
Minecraft world access, Steam calls, file I/O and background threads.

The descriptor contains:

- stable namespaced `AddonId`;
- display name and addon version;
- supported Addon API range;
- required and optional addon dependencies;
- requested capabilities;
- the subset of requested capabilities that are required.

Duplicate IDs are rejected. The same logical addon must use the same ID across
Fabric, Quilt, Forge and NeoForge adapters.

## 2. Validation

Core validates candidates in deterministic order before any addon entry point
runs. It checks:

- identifier and collection bounds;
- Addon API version range;
- duplicate IDs;
- missing or incompatible required dependencies;
- dependency cycles;
- requested capabilities against the allowlist and runtime policy;
- availability of required capabilities.

An optional dependency or capability may be missing; the addon must keep a
fallback. A missing required item disables the addon before partial
registration.

## 3. Initialization

`initialize(AddonContext context)` runs on a bounded lifecycle executor, never
on a Steam native callback thread and never while core holds an internal lock.
The maximum public lifecycle callback budget is 10 seconds.

`AddonContext` provides:

- `descriptor()`: the validated immutable metadata;
- `api()`: a capability-filtered API view for this addon;
- `resources()`: the parent resource scope for registrations and tasks.

Initialization should register channels, commands, UI actions, config schemas,
events and other static contributions. It should not wait for a Minecraft world
or active Steam session. Observe readiness through snapshots/events instead.

## 4. Owning resources

Every successful registration or scheduled task returns a handle. Transfer it
to the parent scope immediately:

```java
ApiResult<Subscription> result = context.api().events().subscribe(
        RuntimeReadyEvent.TYPE,
        event -> refresh(event.snapshot())
);

if (!result.isSuccess()) {
    throw new IllegalStateException("event registration rejected");
}

context.resources().own(result.value().get());
```

The scope closes children in reverse ownership order. Closing is idempotent.
This prevents subscriptions, tasks and channel handles from surviving after an
addon or runtime generation stops.

If the addon creates its own thread, file watcher or external client, wrap it
in a `Registration` and transfer that wrapper to the same scope. e4steam cannot
clean up an unregistered resource it does not know exists.

## 5. Registration freeze and activation

Static protocol registrations freeze before a session negotiates addon
channels. Registering a channel after `NetworkService.registrationsFrozen()` is
true is rejected. This keeps both peers' manifests deterministic.

An `ACTIVE` addon can still have an optional service or channel unavailable in a
particular runtime mode. For example, UI is not available on a headless server.
Feature code must check typed results instead of assuming every service is ready.

## 6. Session generations

Sessions, peer IDs, network handles and async work are generation-bound. When a
world closes, Steam reconnects or a dedicated server restarts, old handles must
not act on the new generation.

Treat `STALE_HANDLE`, `STALE_SESSION`, `CANCELLED` and `UNAVAILABLE` as normal
cleanup outcomes. Obtain a fresh snapshot/handle instead of retrying the old
object in a loop.

## 7. Failure isolation

If initialization throws, times out or returns invalid registrations, the addon
enters `FAILED` and its parent scope closes. A callback exception becomes a
sanitized `ADDON_FAILURE`; it does not stop the Steam worker or expose a raw
native stack to other addons.

Failure isolation cannot undo arbitrary global changes made directly through
Java or Minecraft internals. Addons should prefer scoped API services and keep
initialization reversible.

## 8. Shutdown

Runtime shutdown publishes a stopping event and closes resources in a bounded,
idempotent order. Addon, network, session and scheduler resources are released
without waiting forever for addon code.

Do not start new work from a stopping event. Flush only small already-bounded
state and let the parent scope cancel the rest. Never block shutdown on a remote
HTTP service.

## Loader adapters

Fabric and Quilt-compatible Fabric, Forge and NeoForge adapters all feed the
same core lifecycle. Keep loader-specific code limited to discovery, metadata
and Minecraft integration. Business logic should depend on `e4steam-api`, not
loader classes.

On a dedicated server, no client UI class may be loaded. Mark purely client
addons correctly in loader metadata, or provide a physical-side check before
touching Minecraft client types.

## Lifecycle tests

`api-testkit` provides fake services, deterministic scheduling, event recording
and failure injection without Minecraft or Steam. Test at least:

- duplicate ID and incompatible API range;
- missing required versus optional dependency;
- dependency cycle;
- denied required capability;
- initialization exception and timeout;
- registration freeze;
- reverse-order resource cleanup;
- close called more than once;
- session/world close with pending tasks;
- runtime restart and stale handle rejection.

Then test real loader discovery on each published loader. A testkit lifecycle
pass does not prove that Fabric, Forge or NeoForge metadata is correct.
