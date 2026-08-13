# Security policy

## Supported versions

| Version | Supported |
| --- | --- |
| Latest 0.2.x stable | ✅ Yes |
| 0.3.0 development branch | Testing only; not a published supported release |
| Older 0.x and alpha builds | ❌ No |

Security fixes are provided for the latest stable 0.2.x release. The 0.3.0
branch contains experimental macOS/dedicated implementations and build-only
retro artifacts; none is a published supported release. 32-bit operating
systems are unsupported.

## Threat model for 0.3.0 development

- A Steam transport session is not world authorization. The current invite
  secret, social policy, live world and guest limit remain mandatory gates.
- An offline Minecraft name supplied by a Steam guest is untrusted. The stable
  guest UUID and safe name are derived from the already authenticated SteamID.
- RESET retries are bounded and tied to one Steam worker generation; stale
  terminal packets cannot cross a reconnect into a new generation.
- Native libraries are loaded only from an allowlisted owner-controlled cache
  after no-follow type, owner, size and SHA-256 validation. Same-account code
  remains inside the trust boundary; Java cannot sandbox another installed mod.
- Addon channels are generation-bound and negotiated only after core
  authentication. Required incompatibility fails before game/addon traffic;
  quotas and fair queues protect core/Minecraft capacity.
- Dedicated mode requires loopback-only Minecraft bind, authenticated
  GameServer tickets and current single-use ingress registration. Direct TCP,
  stale generations and replayed authentication fail before gameplay code.
- Doctor and addon diagnostics exclude Steam identity by default and redact
  credential-bearing addresses, named secrets and user paths with finite
  output limits.

## Installed addon trust

The addon API is a least-privilege contract and diagnostic boundary, not a JVM
sandbox. A malicious installed mod can use ordinary Java APIs outside e4steam.
Install addons only from trusted sources. Core never provides addons with a
Steam password, auth ticket, invite token, cookie, API key, native handle or
raw packet/native callback object. SteamID, persona name and avatar are
personal data and require an explicit profile-read capability.

## Reporting a vulnerability

Use GitHub private vulnerability reporting. Do not publish working invite
addresses, Steam session details, account identifiers, or logs containing
private data in a public issue.

Include the e4steam version, loader, Minecraft version, operating system,
whether the failure occurred as host or guest, and the smallest reproduction
you can provide. The `/e4steam doctor` output intentionally omits the invite
token; review diagnostics before sharing them.

## Invite handling

The compact `s-...steam` address and accepted long fallback contain a random
128-bit bearer token. Both access modes also require the remote Steam account
to be a direct friend of the host. Invite-only mode additionally requires
current membership in the host's private lobby. Use `/e4steam stop` or reopen
the world to invalidate the current token.
