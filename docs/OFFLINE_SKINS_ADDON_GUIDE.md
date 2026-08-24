# Offline Skins addon boundary

Offline Skins is **not** included in core and e4steam performs no external skin
lookup on its own.

API 1.0 supplies provider request/result contracts keyed by safe identity. Skin
assets are bounded by encoded size, type, dimensions, decoded size, provenance,
hash and cache metadata before loader-specific rendering receives them. A
separate addon owns external lookup, redirects, consent and cache policy.

Minecraft identity is the default input. SteamID, persona and avatar are
personal data and require an explicit profile-read capability. Passwords, auth
tickets, invite tokens, cookies and raw Steam callbacks are never provided.
Provider failure must fall back safely without blocking login or the render
thread.
