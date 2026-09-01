# Dedicated servers

[Русская версия](DEDICATED_SERVER_RU.md)

This page explains how dedicated mode works. Use the
[deployment guide](DEDICATED_DEPLOYMENT.md) when you are ready to install it.

## Short version

- Install the normal e4steam JAR that matches the server's Minecraft version
  and loader. There is no separate server JAR.
- Bind Minecraft to `127.0.0.1` and enable e4steam in
  `config/e4steam-dedicated.toml`.
- Start the server normally. Do not run a personal Steam client on the server.
- Wait until the console prints `e4steam dedicated address: d-...steam`.
- Send that address to an allowed player. The player pastes it into Minecraft's
  normal server-address field while their Steam client is running.

The server uses anonymous Steam GameServer login for App ID 480. It does not
need a personal Steam account, Steam friends list, desktop overlay or GSLT.

## Platform status

Windows x64 is the primary supported dedicated-server platform. Recorded
authenticated joins include NeoForge 1.21.1, Fabric 26.2 and Forge 1.12.2.
Linux and macOS contain the same server code and native libraries but remain
experimental until their manual matrices are complete.

The exact evidence for every loader family is kept in
[COMPATIBILITY.md](../COMPATIBILITY.md). A successful build is not the same as
a successful player join.

## What happens when a player connects

```text
Player enters d-...steam
        |
        v
Steam Networking Messages / Valve relay
        |
        v
e4steam checks the server generation and Steam authentication
        |
        v
capacity, ban and allowlist checks
        |
        v
short-lived authenticated loopback bridge
        |
        v
Minecraft server on 127.0.0.1
```

Minecraft is not exposed directly to the LAN or internet. A connection reaches
the Minecraft login handler only after e4steam has created an admission record
for the authenticated Steam peer. An unrelated TCP client, including another
local process, has no matching record and is rejected.

## What the `d-...steam` address contains

The descriptor tells an e4steam client which anonymous Steam GameServer and
runtime generation to contact. It is not a Steam authentication ticket and it
does not bypass the allowlist, ban, capacity or protocol checks.

The address still identifies a live server session. Share it only with the
intended players. Restarting the e4steam backend creates a new generation, so
an old descriptor stops being useful.

The server is not published in Steam's public Server Browser. Core e4steam does
not contain a public-server browser or publication provider.

## Startup states

The server moves through these states:

```text
CONFIG_VALIDATED
  -> NATIVES_READY
  -> STEAM_INITIALIZING
  -> STEAM_LOGGING_ON
  -> TRANSPORT_READY
  -> MINECRAFT_READY
  -> ACCEPTING
  -> DRAINING
  -> STOPPED
```

Any startup error changes the state to `FAILED`. The `d-...steam` address is
printed only in `ACCEPTING`, after both Steam transport and Minecraft ingress
are ready.

| State | Meaning |
| --- | --- |
| `CONFIG_VALIDATED` | The strict config and `server.properties` passed validation |
| `NATIVES_READY` | The correct Steam libraries were selected and loaded |
| `STEAM_LOGGING_ON` | The anonymous GameServer login is in progress |
| `TRANSPORT_READY` | Steam can receive e4steam traffic |
| `MINECRAFT_READY` | The loopback Minecraft listener and login hooks are ready |
| `ACCEPTING` | The descriptor is valid and permitted players may join |
| `DRAINING` | New e4steam joins are stopped while active bridges close |
| `FAILED` | Startup or runtime stopped at the recorded error category |

## Access modes

- `PRIVATE` and `WHITELIST` require the player to be present in the configured
  or runtime allowlist.
- `UNLISTED` lets any compatible, authenticated Steam user who has the current
  descriptor attempt to join. Capacity and bans still apply.
- `CUSTOM` is reserved for an installed provider and is rejected by core when
  no provider supplies it.

The server does not use the host's personal Steam friends list because there is
no personal Steam account in dedicated mode.

## Loader coverage

Modern Fabric/Quilt, Forge and NeoForge release JARs contain headless
entrypoints. Retro Forge `1.7.x-1.16.x` and Fabric `1.14.x-1.16.x` JARs also
contain a Java 8 dedicated bootstrap.

Retro servers carry the normal Minecraft stream through the same authenticated
Steam boundary. They do not expose Addon API services, addon channel
negotiation or the modern console command tree. Configure their allowlist
before startup.

## Addon API

Modern dedicated servers expose `DedicatedServerService` through
`context.api().dedicatedServers()`.

With `DEDICATED_OBSERVE`, an addon can read:

- lifecycle and readiness;
- redacted configuration;
- access mode, player count and capacity;
- whether Steam-only ingress and publication are active.

`DEDICATED_ADMIN` allows `drain(reasonCode)`. Drain stops e4steam from accepting
new Steam players; it does not save the world or terminate Minecraft.

`DEDICATED_PUBLICATION_PROPOSE` lets an addon submit a bounded publication
proposal. Core still rejects it unless an approved provider is installed and
the server configuration permits publication. e4steam core 0.3.1 has no such
provider and returns `public-worlds-addon-required`.

The API never returns auth tickets, GSLT, private descriptor fields, native
handles or raw Steam packets. See the full [Addon API guide](ADDON_API.md).

## Next steps

1. Follow [Running a dedicated e4steam server](DEDICATED_DEPLOYMENT.md).
2. Read [Dedicated-server security](DEDICATED_SECURITY.md) before exposing the
   server on a hosted machine.
3. Check [Compatibility](../COMPATIBILITY.md) for the exact loader, Java and OS
   results already recorded.
