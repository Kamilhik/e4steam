# API threading

Named execution contexts are `MINECRAFT_CLIENT`, `INTEGRATED_SERVER`,
`DEDICATED_SERVER`, `E4STEAM_CONTROL`, `ADDON_WORKER` and `TIMER`.

- Addon callbacks do not execute on JNA/Steam native callback threads or while
  a core lock is held.
- Blocking work is forbidden on Minecraft client/server contexts.
- Queued tasks, delays, lifecycle callbacks and event delivery are bounded.
- Callback exceptions become sanitized addon failures and do not terminate the
  Steam worker.
- Each asynchronous operation is owned by an addon/session generation. Closing
  a task or parent scope is idempotent; stale callbacks are ignored or receive
  typed cancellation.
- High-frequency observational events may be coalesced; decision policies have
  explicit timeouts and fail closed.

Production execution is implemented by `CoreSchedulerService` and scoped
wrappers. `DeterministicScheduler` in the testkit supplies virtual-time tests.
