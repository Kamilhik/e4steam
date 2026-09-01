# Running a dedicated e4steam server

[Русская версия](DEDICATED_DEPLOYMENT_RU.md)

This guide starts with an existing, working modded Minecraft server. First make
sure the server starts without e4steam. Then install e4steam and enable its
Steam-only ingress.

Windows x64 is the primary supported platform. Linux and macOS use the same
JARs but remain experimental while their manual matrices are incomplete.

## Before you begin

You need:

- a 64-bit operating system and the Java version required by that Minecraft
  release;
- a working Fabric/Quilt, Forge or NeoForge dedicated server;
- the normal e4steam JAR matching the loader and Minecraft version;
- Fabric API for a Fabric or Quilt server when the selected release requires
  it;
- a writable server directory and outbound access to Steam/Valve relays.

The server does not need a personal Steam account, desktop Steam client or
GSLT. Do not launch the server through Steam.

Back up the world, `server.properties` and `config` directory before changing a
production server.

## 1. Install the correct JAR

1. Stop the Minecraft server cleanly.
2. Remove older e4steam JARs from `mods`. Keep exactly one release JAR.
3. Copy the JAR matching the server's loader and Minecraft range into `mods`.
4. Add Fabric API for Fabric/Quilt when required.
5. Start the server once only if you need the loader to create its normal
   directories, then stop it again.

There is no separate client/server e4steam download and no separate Windows,
Linux or macOS JAR.

## 2. Restrict the Minecraft listener

Edit `server.properties`:

```properties
server-ip=127.0.0.1
enable-rcon=false
enable-query=false
```

These settings are mandatory:

- `server-ip=127.0.0.1` keeps Minecraft behind e4steam's authenticated local
  bridge;
- RCON and vanilla query are disabled because they are separate network entry
  points and do not pass through Steam admission.

Do not use `0.0.0.0`, a LAN address or a public address. e4steam deliberately
fails startup instead of quietly exposing a second unprotected path. IPv6
loopback `::1` is accepted by the runtime, but use `127.0.0.1` unless the whole
server setup is already known to work with IPv6 loopback.

## 3. Create the e4steam config

Create `config/e4steam-dedicated.toml` as UTF-8 text:

```toml
schema-version = 1
enabled = true
access-mode = "PRIVATE"
max-peers = 8
query-port = 65535
server-name = "e4steam Minecraft server"
whitelist = ["76561198000000001"]
auth-mode = "ANONYMOUS"
publication = false
ingress-guard = "STEAM_ONLY"
diagnostics-level = "BASIC"
relay-policy = "OFFICIAL_AUTOMATIC"
```

Replace the example SteamID64 with the players you want to permit.

### Config reference

| Field | Accepted value | What it does |
| --- | --- | --- |
| `schema-version` | `1` | Selects the strict configuration format |
| `enabled` | `true` or `false` | Enables the dedicated Steam backend |
| `access-mode` | `PRIVATE`, `WHITELIST`, `UNLISTED` | Chooses whether an allowlist is required |
| `max-peers` | `1` to `64` | Maximum simultaneous Steam peers; `8` is the normal default |
| `query-port` | `0` to `65535` | Steam GameServer query port setting; default is `65535` |
| `server-name` | non-empty text, at most 64 characters | Safe display name used by the backend |
| `whitelist` | array of SteamID64 strings | Initial players permitted in private/whitelist mode |
| `auth-mode` | `ANONYMOUS` | Fixed security baseline; GSLT mode is not accepted |
| `publication` | `false` | Core does not publish a public server listing |
| `ingress-guard` | `STEAM_ONLY` | Requires a current authenticated Steam bridge before Minecraft login |
| `diagnostics-level` | `OFF` or `BASIC` | Controls bounded e4steam diagnostics |
| `relay-policy` | `OFFICIAL_AUTOMATIC` | Lets Steam choose direct P2P or official Valve relays |

`CUSTOM` access mode is reserved for an installed provider and cannot be used
by core alone.

The parser intentionally supports a small TOML subset. Unknown fields,
duplicate keys, unsupported escapes, symlinks, files larger than 32 KiB and
security-baseline changes stop startup. This prevents a typo from silently
turning into a less protected configuration.

## 4. Optional environment or JVM overrides

Ordinary non-secret fields can be overridden with
`E4STEAM_DEDICATED_*` environment variables or
`-De4steam.dedicated.*` JVM properties. JVM properties take precedence over
the file; environment variables provide values when no property is set.

Examples:

```text
E4STEAM_DEDICATED_ENABLED=true
E4STEAM_DEDICATED_ACCESS=WHITELIST
E4STEAM_DEDICATED_MAX_PEERS=8
E4STEAM_DEDICATED_QUERY_PORT=65535
E4STEAM_DEDICATED_NAME=e4steam Minecraft server
E4STEAM_DEDICATED_WHITELIST=76561198000000001,76561198000000002
```

Do not place passwords, auth tickets, cookies, tokens or private keys in the
file, environment or JVM arguments. Dedicated mode has no GSLT setting.

## 5. Start the server

Start Minecraft with the server's normal script or service. Do not start a
personal Steam client on the same server account.

The useful milestones are:

```text
e4steam dedicated state: TRANSPORT_READY
e4steam dedicated address: d-...steam
```

Retro builds may prefix the first line with `e4steam retro`. The descriptor is
printed only after the backend is accepting connections.

If no address appears:

1. search upward for the first e4steam error;
2. verify `server-ip`, RCON and query values;
3. verify the JAR/loader/Minecraft match;
4. check outbound Steam connectivity and the selected native architecture;
5. keep the full startup log for a report.

## 6. Connect a player

1. The player installs the matching e4steam release and starts their personal
   Steam client.
2. The administrator sends the current `d-...steam` address privately.
3. The player opens **Multiplayer → Direct Connection**, pastes the descriptor
   and connects.
4. Confirm that the player reaches the world, chunks load and reconnect works.

Minecraft version, loader and e4steam must match the server. A normal IP:port
connection is expected to fail because the listener is loopback-only and
requires a Steam admission record.

## Console administration

Modern server consoles expose permission-level-4 commands:

```text
e4steam-dedicated status
e4steam-dedicated descriptor
e4steam-dedicated allow <SteamID64 or e4steam-derived UUID>
e4steam-dedicated unallow <SteamID64 or e4steam-derived UUID>
e4steam-dedicated ban <SteamID64 or e4steam-derived UUID>
e4steam-dedicated unban <SteamID64 or e4steam-derived UUID>
e4steam-dedicated stop
```

| Command | Result |
| --- | --- |
| `status` | Prints lifecycle, access mode, player count, capacity and ingress state |
| `descriptor` | Prints the address only while the backend is accepting |
| `allow` / `unallow` | Changes the persistent runtime allowlist |
| `ban` / `unban` | Changes the persistent runtime ban list |
| `stop` | Drains e4steam sharing without stopping Minecraft |

Access changes are saved to the bounded, owner-local
`config/e4steam-dedicated-access.txt`. Retro servers do not provide these
commands; edit their initial whitelist while the server is stopped.

## Stop, update and recover

Use Minecraft's normal `stop` command to save the world and terminate the
server. `e4steam-dedicated stop` only stops new Steam connections.

For an update:

1. stop Minecraft normally;
2. back up the world and config;
3. replace the old e4steam JAR with one matching the same intended version on
   clients;
4. read the changelog for protocol, identity or config migrations;
5. start the server and wait for a new descriptor;
6. test one join and one reconnect before reopening access.

If startup fails, restore the previous JAR and its matching config from the
backup. Do not keep two e4steam versions in `mods`.

## Hosted services and containers

Keep these invariants even when a panel or container generates the launch
command:

- Minecraft binds to loopback inside the same network namespace as e4steam;
- the config and native cache are writable only by the service account;
- outbound Steam/Valve traffic is allowed;
- no public port forwards directly to the Minecraft listener;
- logs and backups are readable only by server administrators.

e4steam 0.3.1 does not ship an official container image or uptime SLA. Test the
exact image, JVM and loader before moving a public world.

## Verification checklist

- [ ] Server reaches `ACCEPTING` and prints one current descriptor.
- [ ] An allowed player joins and receives chunks.
- [ ] A second join after disconnect works.
- [ ] A non-allowed or banned player is rejected in the selected mode.
- [ ] Direct LAN/public TCP does not reach Minecraft login.
- [ ] Capacity is enforced.
- [ ] `e4steam-dedicated stop` closes new e4steam joins without killing Minecraft.
- [ ] Minecraft's normal `stop` saves the world and shuts the backend down.

For the security reasoning behind these checks, read
[Dedicated-server security](DEDICATED_SECURITY.md).
