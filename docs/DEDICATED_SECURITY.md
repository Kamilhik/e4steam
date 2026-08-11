# Dedicated security requirements

This document is a design gate, not a supported-feature claim.

- Readiness is false until native backend, Steam logon, transport, Minecraft
  listener and ingress guard are all active.
- Direct remote or loopback vanilla TCP without an internal authenticated Steam
  context is rejected before gameplay handlers.
- Tickets are bounded, single-generation, replay-protected and closed on
  disconnect; raw bytes never enter public DTOs or diagnostics.
- Stable Steam-derived UUIDs back bans, allowlists and operators. No connected
  player owns dedicated server authority.
- Mandatory core rejection cannot be reversed by an addon policy.
- GSLT, tickets, secrets and passwords are never printed or serialized.

These controls and two-client tests do not exist yet, so dedicated mode remains
disabled and unsupported.
