# Addon security

Capabilities provide least privilege and auditable diagnostics, not JVM
isolation. A malicious installed mod can call ordinary Java APIs, so users must
install addons only from trusted sources.

## Mandatory admission order

1. current Steam runtime and session generation;
2. transport validation and authenticated Steam identity;
3. core wire/version and session binding;
4. replay, rate, capacity, ban and owner checks;
5. required addon-channel negotiation;
6. optional addon policy.

Addon policy receives a sanitized minimal context after core gates. It may add
a denial but cannot turn a core denial into acceptance. Timeout or exception
fails closed. Network handlers are not called before authentication and channel
negotiation.

Core never exposes passwords, Steam/Microsoft auth tickets, GSLT, invite or
join secrets, cookies, OAuth/API keys, native handles, raw handshake data,
packet dumps or mutable native buffers. SteamID/persona/avatar are personal
data and require explicit profile-read capability; they are absent from normal
diagnostics.

All descriptors, metadata, frames, queues, callbacks, config/storage values,
manifests, images and diagnostics have central bounds. Core remains free of a
public browser, automatic mod installation, external skin lookup and settings
manager unless separate trusted addons are installed.
