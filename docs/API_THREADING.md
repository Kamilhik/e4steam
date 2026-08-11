# API threading

Named contexts are `MINECRAFT_CLIENT`, `INTEGRATED_SERVER`,
`DEDICATED_SERVER`, `E4STEAM_CONTROL`, `ADDON_WORKER` and `TIMER`.

- No addon callback may run on a JNA/native callback thread.
- No callback may run while a core lock is held.
- Blocking work is forbidden on Minecraft main/server threads.
- Queues, task counts, delays and callback time budgets are bounded.
- Callback exceptions are converted to sanitized addon failures and must not
  stop the Steam worker.
- Closing a task or parent scope is idempotent; late callbacks are ignored or
  completed as typed cancellation.

`SchedulerService` defines the public contract and `DeterministicScheduler`
provides virtual-time tests. Production Minecraft/loader executors are a
follow-up and are not active in this foundation PR.
