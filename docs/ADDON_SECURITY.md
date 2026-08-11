# Addon security

Capabilities provide least privilege and clear diagnostics, not JVM isolation.
A malicious installed mod can call Java APIs directly; only trusted addons
should be installed.

Core must never expose Steam/Microsoft passwords, Steam auth tickets, invite
tokens, cookies, OAuth/API keys, GSLT, native handles, raw handshake secrets,
packet dumps or mutable native buffers. SteamID, persona name and avatar are
personal data and may be returned only by a specific profile-read capability.

Mandatory core admission checks always run before optional addon policy:
transport authentication, generation/liveness, invite secret, access mode,
social/allowlist policy and capacity. Events are observational and cannot
cancel a security rejection. An addon policy may reject an already valid peer;
it can never turn a core rejection into acceptance.

Public Worlds, Modpack Sync, Offline Skins and world-settings UI are separate
future addons. Core contains only neutral contracts and does not activate these
features without an installed addon and explicit user action.
