# Future Public Worlds addon boundary

Public Worlds is not included in e4steam core and does not appear without a
separately installed addon. The current API foundation does not yet expose
lobby search/publication services.

A future addon may propose public lobby metadata only after explicit host
configuration. Core still performs transport authentication, current-session
secret validation, mandatory access checks, capacity and generation checks.
The addon cannot override a rejection. SteamID/profile fields require a
separate capability and are personal data. Passwords, tickets, tokens and raw
join secrets are never available.

The addon must bound result counts, metadata sizes, update rates and retention.
Core sends no listing telemetry and provides no universal HTTP client.
