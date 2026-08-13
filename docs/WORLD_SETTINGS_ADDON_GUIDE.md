# World Settings addon boundary

Core does not include a server-properties editor or settings screen. API 1.0
exposes only immutable allowlisted settings and bounded proposals for a future
separate addon. Impactful changes require host preview and confirmation.

No addon can disable Steam authentication, ingress guard, secret validation,
mandatory admission, bans, owner identity, capacity limits or public-listing
policy. Raw `server.properties`, arbitrary keys, filesystem paths, bind
addresses and authentication settings are not public contracts. Unsupported
or out-of-range changes return a typed error and are never written
reflectively.
