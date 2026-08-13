# Dedicated deployment (experimental 0.3.0)

Do not use this development branch for an important public server. The backend
is implemented and automatically tested, but real Steam GameServer deployment
has not completed the two-client matrix.

## Server prerequisites

1. Install the normal e4steam JAR matching the dedicated server loader/version.
2. Bind Minecraft in `server.properties`:

   ```properties
   server-ip=127.0.0.1
   enable-rcon=false
   enable-query=false
   ```

3. Create `config/e4steam-dedicated.toml`:

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

The file is an intentionally strict bounded TOML subset. Unknown/duplicate
fields, symlinks, unsafe values, public publication, disabled ingress or a
non-anonymous login mode fail startup. `E4STEAM_DEDICATED_*` environment values
and `-De4steam.dedicated.*` properties can override ordinary non-secret fields;
do not pass credentials through either mechanism. This backend has no GSLT
input at all.

## Operation

- Start the Minecraft server normally; do not start a personal Steam client or
  launch it through Steam.
- Wait for `state=ACCEPTING`, then use the permission-level-4 console commands:
  `e4steam-dedicated status`, `descriptor`, `allow`, `unallow`, `ban`, `unban`
  and `stop`.
- `allow`/`ban` accept SteamID64 or an e4steam-derived UUID and persist to the
  bounded owner-local `config/e4steam-dedicated-access.txt` store.
- Give the printed `d-...steam` descriptor only to intended players. It contains
  no auth ticket, but should still be treated as non-public server metadata.
- Graceful stop enters draining, closes auth/bridges/Steam backend and lets the
  Minecraft server own normal world saving.

Container/service deployments must preserve loopback isolation, writeable
owner-only config/cache paths and outbound Steam connectivity. No official
container image or production health SLA is provided in 0.3.0.
