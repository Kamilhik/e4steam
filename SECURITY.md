# Security policy

For normal users: install e4steam and addons only from trusted project pages,
keep every participant on compatible versions and never share a live
`s-...steam` or `d-...steam` address publicly. If you think you found a security
problem, use private vulnerability reporting instead of a public issue.

## Supported versions

| Version | Security support |
| --- | --- |
| Latest stable 0.3.x | Yes |
| 0.2.x and older releases | No |
| Alpha and beta builds | No |

Security fixes target the latest stable 0.3.x release, including its listed
retro JARs and Windows x64 dedicated-server path. Linux and macOS builds remain
experimental, but the same security rules apply to them. 32-bit operating
systems are unsupported.

## Security model

- A Steam transport session is not permission to enter a world. The current
  address secret, access mode, live world and guest limit are separate gates.
- Client-supplied Minecraft names are untrusted. e4steam derives a stable UUID
  and safe profile name from the authenticated Steam identity.
- Reconnect and RESET handling is bounded by one Steam worker generation. Old
  terminal packets cannot be reused after a new generation starts.
- Native libraries load only from an owner-controlled cache after type, link,
  owner, size and SHA-256 checks. Java cannot sandbox another mod installed by
  the same user; such code stays inside the local trust boundary.
- Addon channels open only after core authentication and version negotiation.
  Per-channel quotas and fair queues keep addon traffic from starving Minecraft
  or core control frames.
- A dedicated server accepts Minecraft only through a current authenticated
  Steam bridge. Loopback binding, GameServer ticket validation and single-use
  ingress records block direct TCP and stale sessions.
- Diagnostics omit Steam identity by default and redact join descriptors,
  named secrets and user paths. Output size and exception depth are bounded.

## Trusting addons

Addon API capabilities limit what e4steam itself exposes; they do not turn an
addon into sandboxed code. An installed mod can still use normal Java APIs.
Install addons only from sources you trust.

Core does not give addons Steam passwords, auth tickets, invite tokens, GSLT,
API keys, native handles or raw protocol callbacks. Steam profiles are personal
data and require an explicit profile-read capability.

## Reporting a vulnerability

Use GitHub private vulnerability reporting. Never post a live join address,
Steam session details, account identifiers, private keys or an unreviewed log
in a public issue.

Include the e4steam version, Minecraft version, loader, Java version, operating
system and whether the failure happened on a host, guest or dedicated server.
Add the smallest reproduction you can provide. `/e4steam doctor` omits the
invite token, but you should still read the generated report before sharing it.

A useful private report includes:

- what an attacker must control or know;
- whether the issue works before Steam authentication or only after admission;
- affected client, integrated-world host or dedicated-server path;
- exact e4steam, Minecraft, loader, Java and OS versions;
- minimal steps and the expected versus actual security boundary;
- sanitized logs or a small proof of concept.

Do not test against another person's server or account without permission. Do
not include a real password, ticket, token, private key or active join address
even in a private report; replace it with a clearly marked dummy value.

## Invite addresses

An `s-...steam` address and its accepted long fallback contain a random
128-bit bearer token. Friends mode also requires a direct Steam friendship;
invite-only mode additionally requires current membership in the private
lobby. `/e4steam stop` or reopening the world invalidates the current token.
