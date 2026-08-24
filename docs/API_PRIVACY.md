# API privacy

e4steam has no addon telemetry or analytics endpoint. Doctor and diagnostic
services operate locally, use allowlists and never upload a report themselves.

Always forbidden in public DTOs, events, errors, logs and diagnostics:

- passwords, auth tickets, GSLT, tokens, cookies and private/API keys;
- complete credential-bearing join addresses and invite secrets;
- native handles, raw callbacks/packets and arbitrary user files;
- unredacted home paths or sensitive provider failure text.

SteamID64, persona name, avatar, presence and friend relationship are not
credentials, but are personal data. Safe Minecraft identity is separately
gated by `identity.minecraft.read`; a minimal immutable Steam profile requires
`identity.steam.profile.read`/`steam.profile.read`. These fields are omitted
from default diagnostics, are not sent externally and should be retained only
as long as the addon feature requires.

Opaque peer IDs are the default cross-addon identity. Public DTOs defensively
copy collections/bytes and sensitive models have redacted `toString()` output.
Privacy contract and canary tests search API-facing output for forbidden
secret material.

The API cannot prevent a malicious JVM mod from opening its own network
connection or reading files. Trust in installed mods remains mandatory.
