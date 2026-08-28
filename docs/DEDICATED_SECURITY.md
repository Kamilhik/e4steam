# Dedicated security

The 0.3.0 implementation is fail-closed around these invariants:

- `server-ip` must resolve to loopback; RCON and vanilla query must be disabled.
- Readiness is false until Steam transport, Minecraft readiness and the ingress
  guard are active.
- Every Minecraft loopback login maps to a current authenticated Steam peer;
  arbitrary remote or loopback TCP has no admission context and is rejected.
- Auth tickets are at most 4096 bytes, generation-bound, timeout-bounded,
  replay/duplicate-protected and zeroed immediately after use. One transport
  owner ends each backend auth session exactly once on denial, timeout,
  disconnect or shutdown; abandoned bounded-queue tasks erase credentials.
- Core protocol, generation, rate, capacity, ban, whitelist and owner checks
  precede addon negotiation/policy. Addons cannot reverse a denial.
- UUIDs derive from authenticated Steam identity, not persona/display name.
  No connected player owns dedicated server authority.
- Public listing is disabled. A separate Public Worlds addon can only propose
  publication and still cannot bypass core gates.
- Config, access stores, queues, pending auth and diagnostics are bounded.
  Symlinks/unsafe files and unknown security fields fail startup.

Retro dedicated artifacts use the same Steam GameServer auth, generation,
capacity, whitelist and loopback-ingress baseline, but do not expose the Addon
API or negotiate addon channels on that Java 8 path. Absence of addon handling
cannot bypass the mandatory Steam and Minecraft admission gates.

Passwords, GSLT, tickets, tokens, descriptor internals and native handles are
not logged or exposed to addons. The current App ID 480 backend intentionally
supports anonymous GameServer login only; GSLT configuration is rejected
instead of being accepted insecurely.

Automated admission, ingress, lifecycle, config, identity and headless
class-leakage tests exist. A real server plus two clients, reconnect, direct
TCP bypass and failure-under-load smoke suite remains required before a
supported release claim.
