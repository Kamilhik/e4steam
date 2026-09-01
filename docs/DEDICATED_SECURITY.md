# Dedicated-server security

[Русская версия](DEDICATED_SECURITY_RU.md)

This document explains the rules that protect a dedicated e4steam server. It
is written for administrators and addon developers. The shorter setup steps are
in [Running a dedicated server](DEDICATED_DEPLOYMENT.md).

## Main rule

Minecraft must accept a player only after e4steam has authenticated the current
Steam peer and created a short-lived admission record for the same server
generation.

If configuration, Steam, the loopback listener or the ingress guard is not
ready, the server fails closed. It does not silently fall back to a public TCP
listener or offline Minecraft login.

## Network boundary

- `server-ip` must be loopback (`127.0.0.1` or `::1`).
- RCON and vanilla query must be disabled.
- The e4steam backend receives Steam traffic and forwards an authenticated
  connection through a local bridge.
- A direct remote TCP client has no admission record and is rejected.
- An unrelated local process is also rejected; loopback by itself is not
  authorization.

This design prevents the normal Minecraft port from becoming a second path
around Steam checks.

## Admission order

For every new connection, core checks:

1. current server runtime and generation;
2. valid Steam transport session and authenticated Steam identity;
3. e4steam protocol version and framing;
4. replay/duplicate state and request age;
5. capacity and rate limits;
6. ban and owner-protection rules;
7. allowlist or selected access mode;
8. required addon negotiation, on modern servers only;
9. Minecraft login through the authenticated bridge.

An addon may reject an otherwise valid connection. It cannot turn a failed
core check into acceptance. An addon timeout or exception fails closed.

## Authentication tickets and replay protection

Steam auth tickets are internal, limited to 4096 bytes and tied to the current
runtime generation. Duplicate, stale and replayed tickets are rejected. Ticket
buffers are erased after use.

The public Addon API, commands and normal diagnostics never expose tickets,
GSLT, passwords, cookies, API keys, join secrets, native handles or raw
handshake packets.

## Player identity

The client-supplied Minecraft name is not trusted for ownership or permission.
e4steam derives a stable Minecraft UUID and safe profile name from the
authenticated Steam identity. A Steam persona-name change therefore does not
change the derived UUID, allowlist identity, ban identity or saved playerdata.

The displayed name is still personal data. Logs and addon APIs should use the
minimum identity needed for the feature.

## Configuration and local files

The dedicated config parser has fixed bounds and accepts only known fields.
Symlinks, unsafe file types, duplicate/unknown keys and changes to fixed
security values stop startup.

The runtime access file, pending authentication, queues and diagnostics are
also bounded. Keep the server directory and backups owned by the service
account. Do not share write access with an untrusted panel user or mod.

## Public listing

Core does not publish the server to Steam's public Server Browser. The
`d-...steam` descriptor is sent directly to intended players.

Addon API can carry a bounded publication proposal, but publication requires:

- an installed trusted provider;
- the `DEDICATED_PUBLICATION_PROPOSE` capability;
- explicit server configuration permission;
- the normal Steam authentication and ingress rules.

Core 0.3.1 has no publication provider. A proposal cannot open a hidden direct
TCP listener or weaken the admission sequence.

## Retro servers

Retro Forge and Fabric servers use the same anonymous GameServer,
generation-bound authentication, capacity, ban, allowlist and loopback model.
Their Java 8 path does not expose Addon API services or negotiate addon
channels. The smaller feature set does not remove any mandatory core check.

## Operational checklist

- Keep Minecraft bound to loopback.
- Permit outbound Steam/Valve traffic, not inbound direct Minecraft traffic.
- Use one server OS account with owner-only config/cache permissions.
- Send descriptors privately and replace the session after accidental public
  exposure.
- Review addons like any other server mod; Java addons are not sandboxed.
- Read logs before sharing them and remove unrelated information from other
  mods.
- Update host and clients together when the e4steam wire protocol changes.
- Back up the world before changing identity or loader versions.

## What automated tests prove

Tests cover strict configuration, loopback ingress, ticket ownership,
lifecycle, stable identity and client/server class separation. Artifact audits
check the packaged entrypoints and native files.

These checks do not prove a real Steam connection on every operating system.
Manual joins, reconnects, capacity tests, direct-TCP rejection and multi-client
results are recorded separately in [COMPATIBILITY.md](../COMPATIBILITY.md).

Report a suspected vulnerability through GitHub private vulnerability
reporting, not a public issue. Follow [SECURITY.md](../SECURITY.md) and do not
attach a live descriptor or unreviewed log.
