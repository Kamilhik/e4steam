# Future Offline Skins addon boundary

Offline Skins is not included in core and no skin provider is registered by
the `0.1.0` foundation.

A future provider must receive only safe Minecraft identity plus explicitly
granted profile data, validate image type before allocation, bound dimensions,
decoded bytes, animation frames, cache entries and lifetime, and return an
immutable asset. Rendering remains in loader/version adapters.

SteamID, persona name and avatar are personal data and require explicit
capability and documented retention. Passwords, auth tickets, invite tokens,
cookies and raw Steam callbacks are never provided.
