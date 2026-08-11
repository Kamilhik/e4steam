# API privacy

e4steam core performs no addon-usage telemetry and has no analytics endpoint.
Doctor/export flows are allowlist-based and do not upload reports themselves.

Always forbidden in public DTOs, events, errors, logs and diagnostics:
passwords, tickets, tokens, cookies, keys, GSLT, complete credential-bearing
join addresses, native handles, raw packet/callback objects and arbitrary user
files. Public DTO `toString()` methods intentionally omit payload values and
detailed failure text where it could contain a secret.

SteamID64, persona name, avatar, presence and friend relationship are not
credentials, but they are personal data. A future identity/profile service
must require a documented capability, return immutable minimal DTOs, avoid
default diagnostics, avoid external upload and retain values only as long as
the feature needs them.

The API cannot technically prevent a malicious JVM mod from using its own HTTP
client or reading files. Trust in installed mods remains mandatory.
