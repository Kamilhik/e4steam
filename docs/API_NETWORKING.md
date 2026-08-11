# Addon networking status

The current `0.1.0` foundation does not expose an addon network channel or raw
Steam transport. This is intentional: a partial negotiation/security layer is
not enabled by default.

A later service must provide namespaced channels, independent channel versions,
required/optional negotiation, authentication-before-handler, bounded frames,
rates and queues, decompression limits, per-addon fairness, generation binding
and sanitized errors. Addon traffic may never starve Minecraft/control traffic.

Raw JNA callbacks, Steam handles, core packet types and mutable native buffers
will not be public API. Until the versioned service and its fuzz/abuse tests are
merged, addons must not depend on internal transport packages.
