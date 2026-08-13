# Diagnostics and privacy

`/e4steam doctor` produces a short chat summary and a bounded technical report
in the local Minecraft log. The report streams the mod hash instead of reading
the whole JAR, excludes raw Steam identity, redacts credential-bearing `.steam`
addresses, SteamID64 patterns, named secrets and the user home path, limits
exception depth/frames and caps the report at 64 KiB.

Addon API 1.0 also implements an allowlist-based `DiagnosticsService`.
Contributors require `diagnostics.contribute`, run off caller/native threads,
have a two-second timeout, are exception-isolated and are bounded by section,
field and total preview size. Core redaction still applies regardless of addon
behavior.

Default reports exclude SteamID, persona, avatar, lobby IDs, passwords,
tickets, tokens, cookies, GSLT, full join addresses, native handles, packet
dumps, arbitrary files and raw logs. `PrivacyOptions` can explicitly request
non-secret Steam/lobby identifiers where a host UI permits it; credentials are
structurally impossible to opt into.

Doctor and API previews never upload data. Review even a redacted local report
before sharing it because Minecraft/other mods may log their own information
outside e4steam's control.
