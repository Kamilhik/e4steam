# Modern compatibility candidates

Applies to the free 0.3.7 and Premium 0.1.1 development candidates for Minecraft 1.17–26.3. These are test builds, not a published release.

## Player saves

Steam remains the transport identity. Minecraft player saves use the normal Minecraft identity: the verified Mojang UUID for an online account on an online-mode server, or the vanilla `OfflinePlayer:<name>` UUID in offline mode. A verified online account joining an offline-mode server keeps that server’s normal offline UUID too. A client-supplied online UUID is not trusted without Minecraft session verification.

Adding or removing e4steam should therefore keep the same player data when the Minecraft account and server authentication mode stay the same. Changing account or server authentication mode can change the vanilla UUID independently of e4steam. Removing e4steam also removes Steam transport: use normal LAN/IP access afterwards. A dedicated server previously switched to Steam-only ingress needs its original `server.properties` restored from `config/e4steam-dedicated/` before public IP access is enabled again.

On a successful authenticated login, old Steam-derived player data can be copied to the normal UUID. Inventory, location and other NBT state are preserved; vanilla statistics and advancements are copied too. Original files and any overwritten destination remain in the world's `e4steam-player-backups` directory. Migration is recorded and is not repeated over newer gameplay progress. Data formats belonging to other mods need separate compatibility checks.

An existing offline nickname cannot safely prove ownership of an old save. Without completed Mojang verification of that nickname, the world owner must approve its Steam binding in `e4steam-offline-identities.properties` inside the world directory, as `offline-UUID=SteamID64`. The rejection message identifies the exact binding required. New unused offline identities are bound on first join. Another Steam account cannot subsequently claim that name.

## Login ordering

Modern integrated-world clients wait for the matching OPEN acknowledgement before starting their Minecraft reader. The authentication-mode control frame is queued first, preventing early game packets from overtaking identity negotiation. A missing acknowledgement times out after 30 seconds. Old runtime paths retain their compatibility behavior.

## Simple Voice Chat

For shared singleplayer worlds, the host reads the actual bound Voice Chat UDP port instead of assuming that the configured port is used. Guest packets go through a local UDP proxy. If that port is occupied, the modern compatibility hook provides a free local port to Voice Chat. An early ICMP “port unreachable” does not permanently stop the proxy. Direct IP connections retain their normal Voice Chat endpoint.

Dedicated servers now announce an optional voice endpoint after Steam admission. Each authenticated player receives a separate loopback UDP proxy; audio datagrams use the existing unreliable Steam channel in both directions. No public Voice Chat UDP port is required for this route. The original dedicated OPEN acknowledgement stays unchanged, so clients without the optional endpoint support can still join Minecraft.

## Dedicated Premium addresses

1. Install the matching Premium JAR on the dedicated server and start it once.
2. Stop the server and set `name=my-server` in `config/e4steam-premium/dedicated-address.properties`.
3. Place your existing signed Premium key in `config/e4steam-premium/license.key`.
4. Restart. Clients with this updated free or Premium core can enter `my-server.steam`.

No Steam user login, QR scan or desktop Steam client is required on the server. The anonymous Steam GameServer backend still needs its native libraries and network access.

Keep `config/e4steam-premium/dedicated-name-identity.key` private and preserve it across server upgrades. It contains the service identity, not an activation key. Clients verify a fresh signed challenge and remember the public fingerprint after the first successful connection. A changed anonymous Steam process ID can then resolve to the same service after restart.

Steam master-server results may contain cached metadata older than 90 seconds. Named-service records allow a bounded 15-minute cache age; availability and possession of the service key are checked separately using a fresh nonce proof before Minecraft bytes are released.

Names use Steam discovery without a central registry. Two unrelated services advertising the same name are rejected as ambiguous on first use. A previously saved world address and a dedicated-server address are separate identities; reusing the same name requires deliberately clearing the obsolete client binding.

## World handoff and statistics

The Premium sender can hand a saved world to a connected friend. A free recipient can accept it and return that same world to its original owner. Import verification precedes retirement of the sender's active copy. Retired world backups expire after three days; player-identity migration backups are retained separately. The owner's personal address is restored locally on return rather than exported to the temporary host.

Modern candidates contain no PostHog sender, ingestion key, statistics setting or telemetry mixin. The paused retro branch is outside this change.

## Handoff identity and throughput fixes

The receiver captures the UUID it actually used in the remote world before disconnecting. World-local authenticated Steam-to-Minecraft bindings are saved in `e4steam-player-identities.properties`. Import selects that player record instead of an older launcher-account or legacy Steam record. When the temporary host returns the world, their current singleplayer data is also copied back to the UUID used as a guest. An overwritten launcher record is retained in the role-migration backup directory.

Transfer sends advance on successful transport completion instead of waiting for the next maintenance timer after each packet. Cumulative acknowledgements are coalesced, the bounded transfer window is 64 × 24 KiB, and the native reliable queue limit for world transfer is 1 MiB. Backpressure retries and archive verification remain enabled. A regression test transfers an incompressible multi-window archive with a deliberately slow maintenance timer; this tests scheduling, not Internet throughput.

## Native bulk-rate profile

Large outgoing world-transfer frames temporarily enable Steam's unlimited upper send-rate setting (SendRateMax=0), with an initial minimum-rate hint of 2 MiB/s. Healthy busy routes can increase that hint beyond 8 MiB/s; packet-loss and latency feedback reduces it on a poor route. Steam congestion control, the route, both connections and disk work still determine actual throughput; full Internet line speed is not guaranteed. This profile is local to the game's Steam SDK process; the previous minimum and maximum are read first and restored on completion, cancellation, peer closure or transport shutdown. If the settings cannot be read or applied, the transfer continues with normal settings.

Bulk profile v2 requires sustained congestion before reducing its minimum-rate hint, uses 25% reductions with a six-second cooldown, and permits recovery when RTT is healthy even if Steam's rolling quality estimate still includes earlier loss. A transient spike or historical loss alone must not repeatedly halve the rate. Following congestion, increases are gradual rather than doubling immediately. The upper rate remains unlimited.

Local logs now separately time network acknowledgement and archive installation, and record Steam's native send-rate estimate, quality estimates, measured outgoing traffic and queue state during a transfer. These are troubleshooting logs, with no external statistics sender.

The manual native benchmark uses two isolated JVMs and dummy data. Its controlled 256 KiB/s baseline is explicitly configured for the test; results do not establish the cause of a particular remote route's speed limit or predict Internet throughput.

## Validation limits

Unit checks cover UDP socket recovery, address proofs and replay rejection, duplicate-name selection, vanilla UUID compatibility, backup retention and migration idempotence. The matrix audit checks all twelve JARs, Java targets, edition separation, required hooks and absence of statistics code.

Voice audio, transfer/return between two computers, and live Steam directory discovery still require gameplay verification. A successful build alone is not evidence that those end-to-end scenarios passed.
