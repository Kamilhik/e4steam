# Future Modpack Sync addon boundary

Modpack Sync is a separate future addon; core does not download, install or
execute JAR files. The current API foundation contains no staging service.

A safe future flow requires explicit user confirmation, a bounded signed or
hash-pinned manifest, filename/path confinement, size/count limits, HTTPS in
the addon's own documented implementation, staging outside the live mods
directory, integrity verification, a preview and an explicit restart. A
manifest cannot silently enable code or bypass loader/version checks.

Steam passwords, tickets, tokens, API keys and arbitrary filesystem access are
not exposed by e4steam. The API is not a sandbox for an installed addon.
