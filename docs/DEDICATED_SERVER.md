# Dedicated server

Status for the 0.3.0 release: supported on Windows x64. Real authenticated
joins are recorded for representative modern and retro loaders; the complete
two-client and cross-platform matrix is still tracked separately.

## Architecture

- `DEDICATED_GAME_SERVER` is a separate headless runtime backend. It never
  initializes the user Steam client, friends list or overlay and never asks for
  a personal Steam username/password.
- `SteamGameServerRuntimeBackend` initializes the GameServer API for App ID
  480, logs on anonymously, disables master-server advertising and carries
  packets through Steam Networking Messages.
- The Minecraft listener binds only to loopback. Every accepted Steam peer gets
  a short-lived internal loopback bridge registered to the current generation.
  Direct remote or unrelated loopback logins fail before gameplay handlers.
- Each client submits a bounded Steam auth ticket. The server validates it via
  the GameServer auth session before deriving the stable Minecraft UUID/name.
  Ticket bytes are zeroed, never persisted and never enter diagnostics/API.
- Readiness is announced only after GameServer transport, loopback listener,
  Minecraft world/tick loop and ingress guard are all ready.

Lifecycle:

```text
CONFIG_VALIDATED -> NATIVES_READY -> STEAM_INITIALIZING -> STEAM_LOGGING_ON
-> TRANSPORT_READY -> MINECRAFT_READY/ACCEPTING -> DRAINING -> STOPPED
                                      \-> FAILED
```

Private/whitelist/unlisted admission, stable-identity bans and the server-owned
authority model are implemented. Public advertising always returns
`public-worlds-addon-required`; core has no public browser or publication flow.

Clients join the credential-free, generation-bound descriptor printed
automatically as `e4steam dedicated address: d-...steam` when readiness is
reached. The `e4steam-dedicated descriptor` command can print it again. A
descriptor is routing metadata, not proof of authorization: GameServer auth
and all mandatory gates still run.

## Loader scope

The neutral server bootstrap and headless entrypoints are wired for modern
Fabric/Quilt-compatible Fabric, Forge and NeoForge artifacts. The released
retro Forge `1.7.x`–`1.16.x` and Fabric `1.14.x`–`1.16.x` artifacts also have a
Java 8 headless bootstrap, the same GameServer authentication backend and
server-side login/listener hooks. Their transport deliberately omits addon
channel negotiation; the base Minecraft TCP stream remains authenticated and
bounded. All retro combinations are built and artifact-audited; Forge 1.12.2
also has a real Windows x64 authenticated join. Exact statuses are in
[`COMPATIBILITY.md`](../COMPATIBILITY.md).

See [`DEDICATED_DEPLOYMENT.md`](DEDICATED_DEPLOYMENT.md) for configuration and
[`DEDICATED_SECURITY.md`](DEDICATED_SECURITY.md) for the trust boundary.
