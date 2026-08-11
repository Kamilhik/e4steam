# Dedicated server status

Dedicated servers are unsupported. The current runtime intentionally rejects
non-client startup and supports integrated LAN worlds only. Compiling an API
enum does not constitute a GameServer backend.

A future implementation needs a separate headless `DEDICATED_GAME_SERVER`
backend, official Steam GameServer initialization, readiness/draining states,
per-player ticket validation before Minecraft profile creation, stable
Steam-derived UUIDs, private/whitelist/unlisted modes, console authority and a
direct vanilla TCP ingress guard. Public advertising remains absent without a
separate Public Worlds addon.

No server process may request a personal Steam username/password. Optional
GSLT must come from an approved secret source and never CLI, ordinary config,
addon API, metadata, logs or diagnostics.
