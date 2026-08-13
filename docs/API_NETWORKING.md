# Addon networking

Addon networking is implemented for the unreleased 0.3.0 branch through
`NetworkService` and an internal authenticated `AddonNetworkCoordinator`.
Addons never receive raw Steam/JNA handles or core protocol frames.

## Channel lifecycle

1. An addon registers a namespaced channel before registrations freeze.
2. Its descriptor declares independent versions, required/optional status,
   direction, delivery semantics and maximum message size.
3. Peers exchange bounded manifests only after core authentication.
4. Required incompatibility rejects activation; optional incompatibility
   disables that channel only.
5. A handler can run only for the current authenticated session generation and
   a successfully negotiated channel.

Protocol `4` carries addon manifest, agreement, fragment and message frames.
Large messages use bounded fragmentation/reassembly. Decoding validates
lengths before allocation and rejects malformed, replayed, early or
stale-generation frames.

Per-peer, addon and channel budgets bound frame size, aggregate reassembly,
rates, queued bytes and callback work. Queue scheduling reserves priority for
core/Minecraft traffic so addon traffic cannot starve the game transport.
Virtual UDP uses the same authentication, generation and quota boundary.

Unit/contract tests cover required and optional negotiation, malformed and
oversized input, replay/stale generations, fragmentation, queue pressure,
fairness and handler isolation. A real two-client addon-channel Steam smoke
test is still required before 0.3.0 release.
