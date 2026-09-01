# Addon API threading and scheduling

Addon API callbacks never run directly on Steam/JNA native callback threads and
never run while core holds an internal lock. e4steam moves addon work into
named, bounded execution contexts so a slow addon cannot silently block the
Steam worker.

## Execution contexts

| Context | Intended work | Must not do |
| --- | --- | --- |
| `MINECRAFT_CLIENT` | Change client UI or client-only game state | Block, sleep, perform file/network I/O |
| `INTEGRATED_SERVER` | Change the local singleplayer server world | Block or touch client-only state |
| `DEDICATED_SERVER` | Change dedicated-server game state | Block or assume a client exists |
| `E4STEAM_CONTROL` | Small serialized decisions related to e4steam state | Long calculations, file I/O or waiting |
| `ADDON_WORKER` | Bounded addon-owned background work | Run forever or bypass API limits |
| `TIMER` | Delayed triggers and short timer callbacks | Treat it as a Minecraft main thread |

The context name describes where work is allowed to run; it does not make
unsafe Minecraft objects thread-safe. Move only the final game-state change to
the correct Minecraft context.

## Scheduling work

Use `SchedulerService.execute()` for immediate work and `schedule()` for a
delayed callback. Both calls need a finite timeout and return an
`ApiResult<TaskHandle>`.

```java
ApiResult<TaskHandle> result = context.api().scheduler().execute(
        ExecutionContext.ADDON_WORKER,
        () -> refreshLocalCache(),
        Duration.ofSeconds(2)
);

if (!result.isSuccess()) {
    ApiError error = result.error().get();
    // Handle QUEUE_FULL, INVALID_ARGUMENT or another typed failure.
    return;
}

context.resources().own(result.value().get());
```

Always transfer a successful handle to `context.resources()`, unless your addon
closes it earlier itself. When the addon stops, the parent scope cancels
remaining tasks and prevents a stale callback from entering a later runtime
generation.

For delayed work:

```java
ApiResult<TaskHandle> delayed = context.api().scheduler().schedule(
        ExecutionContext.TIMER,
        () -> requestRefresh(),
        Duration.ofSeconds(5),
        Duration.ofSeconds(1)
);
```

The delay says when a callback becomes eligible. The timeout is the callback's
execution budget; it is not a request to block the current thread.

## Task states

A `TaskHandle` reports one of these states:

- `QUEUED`: accepted into a bounded queue;
- `RUNNING`: callback started;
- `COMPLETED`: callback finished within its budget;
- `FAILED`: addon code threw an exception;
- `CANCELLED`: the handle or parent scope was closed;
- `TIMED_OUT`: execution exceeded its declared budget;
- `REJECTED`: a bounded executor refused the task before it ran.

`completion()` returns a `CompletionStage<ApiResult<TaskState>>`. Failure data
is sanitized and categorized; do not expect a raw internal exception or native
stack trace.

Calling `close()` more than once is safe. Never reuse a handle after it closes
or after its session/runtime generation changes.

## Event callbacks

Event subscriptions are delivered in deterministic registration order through
bounded API scheduling. High-frequency observation events may be coalesced.
Do not use an observation event as a lossless packet stream.

```java
ApiResult<Subscription> result = context.api().events().subscribe(
        SessionStateEvent.TYPE,
        event -> updateSnapshot(event.snapshot())
);

if (result.isSuccess()) {
    context.resources().own(result.value().get());
}
```

Decision policies are different from observation events: they have explicit
timeouts and fail closed. A timed-out addon cannot turn a rejected connection
into an accepted one.

## Moving between contexts

A safe pattern is:

1. receive a small immutable DTO in an API callback;
2. perform parsing or I/O on `ADDON_WORKER`;
3. schedule only the final Minecraft mutation on `MINECRAFT_CLIENT`,
   `INTEGRATED_SERVER` or `DEDICATED_SERVER`;
4. check session/generation state again before applying the result.

Do not call `.join()`, `.get()`, `Thread.sleep()` or a blocking HTTP/file
operation on a Minecraft or e4steam control context. Chaining
`CompletionStage` values is safer than waiting synchronously.

## Limits and failures

The public limits include at most 256 queued addon tasks and a maximum 10-second
lifecycle callback budget. Individual services may impose smaller limits.
Treat `QUEUE_FULL`, `RATE_LIMITED`, `TIMEOUT`, `CANCELLED` and `STALE_HANDLE` as
normal runtime outcomes, not impossible exceptions.

A callback exception is isolated to its addon operation. It does not run on or
terminate the Steam native callback thread. Repeated or lifecycle-critical
failures can still disable the addon, and its owned registrations are closed.

## Deterministic tests

`api-testkit` provides `DeterministicScheduler`. It uses virtual time and stable
ordering, so tests do not need `Thread.sleep()`:

```java
DeterministicScheduler scheduler = new DeterministicScheduler();
scheduler.schedule(
        ExecutionContext.ADDON_WORKER,
        callback,
        Duration.ofSeconds(5),
        Duration.ofSeconds(1)
);

scheduler.advance(Duration.ofSeconds(5));
scheduler.runUntilIdle();
```

Test cancellation, queue saturation, callback exceptions, stale generations
and shutdown while work is pending. A deterministic scheduler test still does
not replace a real Minecraft/Steam integration test.
