# Public Worlds addon boundary

Public Worlds is **not** included in e4steam core. Without a separate addon
there is no public button, access mode, browser, search, publication, command,
setting or network channel.

API 1.0 supplies bounded primitives a future addon may use: custom access-mode
registration, optional post-core admission policy, typed lobby metadata and
search pages, UI/command contributions and a publication proposal capability.
Public metadata is an untrusted advertisement and never authorization.

A future addon must require explicit host configuration, namespace and bound
metadata, rate-limit search/update work and revalidate every result during the
authenticated handshake. Core always retains Steam authentication, current
generation, protocol, replay, invite/session binding, ban, owner, capacity and
rate gates. An addon can reject but cannot override a core rejection.

Steam profile data requires a separate capability. Passwords, tickets, tokens,
GSLT and credential-bearing join addresses are unavailable. Core performs no
listing telemetry and provides no universal external HTTP backend.
