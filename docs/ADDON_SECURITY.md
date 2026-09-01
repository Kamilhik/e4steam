# Addon security

Addon API capabilities restrict what e4steam exposes. They do not isolate an
addon from Java, Minecraft or other code in the same process. A malicious mod
can still read files or open its own network connection, so users must install
addons only from sources they trust.

## Core security cannot be bypassed

Incoming peers pass these gates in order:

1. current Steam runtime and session generation;
2. transport validation and authenticated Steam identity;
3. core protocol version and session binding;
4. replay, rate, capacity, ban and ownership checks;
5. required addon-channel negotiation;
6. optional addon access policy.

An addon policy sees only a small `AdmissionContext` after the mandatory gates.
It can deny an authenticated connection, but `allow()` cannot revive a peer
that core already rejected. A timeout or exception fails closed.

## Capabilities

An addon descriptor lists requested capabilities and which of them are
required. Core validates them before `initialize()`.

```java
Set<CapabilityId> requested = new LinkedHashSet<>();
requested.add(Capabilities.NETWORK_CHANNEL_REGISTER);
requested.add(Capabilities.UI_CONTRIBUTE);

Set<CapabilityId> required = Collections.singleton(
        Capabilities.NETWORK_CHANNEL_REGISTER
);
```

Request the smallest set possible. Mark a capability required only when the
addon cannot provide any safe useful behavior without it. A headless server,
unsupported loader or local policy may deny an optional capability.

At a use point, check or require the capability:

```java
ApiResult<CapabilityId> permission = context.api().capabilities().require(
        Capabilities.DEDICATED_ADMIN,
        "myaddon.dedicated.drain"
);

if (!permission.isSuccess()) {
    return;
}
```

Do not catch a denial and bypass the API through reflection or internal classes.
Internal packages are not a compatibility or security contract.

## Capability groups

| Area | Typical capabilities | Main risk controlled |
| --- | --- | --- |
| Session | `session.observe`, `session.control` | Lifecycle and connection actions |
| Dedicated server | `dedicated.observe`, `dedicated.admin` | Headless status and administration |
| Identity | `identity.minecraft.read`, `identity.steam.profile.read` | Personal data exposure |
| Lobby | `lobby.create`, `lobby.search`, `lobby.metadata.write` | Bounded Steam lobby operations |
| Access | `access.mode.register`, `access.policy.evaluate` | Custom post-authentication decisions |
| Network | `network.channel.register`, `udp.provider.register` | Authenticated addon traffic and quotas |
| UI/commands | `ui.contribute`, `commands.register` | User-visible actions |
| Config/storage | `config.read`, `config.write`, `storage.private` | Namespaced local state |
| Optional providers | world, modpack and skin capabilities | Explicit external feature contracts |
| Diagnostics | `diagnostics.contribute` | Bounded redacted reports |

The presence of an API contract does not mean the core mod ships that feature.
Public world listing, modpack installation, external skins and custom world
settings require a separate trusted addon/provider.

## Custom access modes

An addon with `access.mode.register` can register an
`AccessService.AccessModeProvider` before registration freeze. Its policy
receives only:

- current `SessionId`;
- opaque authenticated `PeerId`;
- owning `AddonId`;
- `coreAuthenticated`, which is always true.

It does not receive a Steam ticket, invite token, raw SteamID, IP address or
native handle.

The policy returns:

- `allow()`: continue only if all earlier core gates passed;
- `deny(reasonCode)`: reject with a bounded non-secret reason code;
- `challenge(id, expiry)`: begin a bounded opaque challenge lasting no more
  than 30 seconds.

Challenges must not use a password, token or credential as their ID. Policy
callbacks have a finite timeout; unavailable external services should deny or
use a documented safe fallback, never hang login.

## Network handlers

Addon handlers run only after channel negotiation in the current authenticated
generation. Payloads remain untrusted. Validate lengths and schema, respect
backpressure, and do not deserialize arbitrary Java objects.

Core enforces per-peer, per-addon and per-channel budgets. It reserves queue
capacity for Minecraft and control traffic. An addon must still stop retrying
when it receives `QUEUE_FULL`, `RATE_LIMITED`, `STALE_SESSION` or `CLOSED`.

## Secrets and personal data

Core never exposes:

- Steam or Microsoft passwords;
- Steam auth tickets or GameServer login tokens;
- e4steam join secrets;
- cookies, OAuth tokens or API keys;
- native handles and raw callback data;
- core handshake or packet injection hooks.

Steam profile information is personal data and requires explicit profile-read
capability. Default diagnostics omit it. See [`API_PRIVACY.md`](API_PRIVACY.md)
for logging, storage and export rules.

## Resource and input limits

Descriptors, dependency lists, capability sets, metadata, frames, message
sizes, queues, fragments, callbacks, config values, storage blobs, diagnostic
sections and images all have fixed bounds. Constructors may throw
`IllegalArgumentException` for invalid static declarations; runtime operations
return typed `ApiResult` failures.

Never respond to a limit by silently increasing memory use outside the API. If
a feature needs more data, paginate, compress safely, split the user action or
redesign the schema.

## Dedicated-server addons

Headless code must not load client UI types. Dedicated administration needs
`dedicated.admin`, and public listing metadata needs
`dedicated.publication.propose`. A publication proposal cannot enable listing
when core config or administrator policy denies it, and it cannot contain raw
IP addresses or credentials.

Custom access policy cannot replace the dedicated ingress guard. Direct vanilla
TCP remains blocked; an addon cannot accept a peer without a current Steam-
authenticated ingress record.

## Failure behavior

Addon callback exceptions are sanitized and isolated. A lifecycle-critical
failure disables the addon and closes its `ResourceScope`; it does not expose a
raw native exception to peers or stop the Steam worker.

Do not rely on exception text as a protocol. Use `ApiErrorCode`, safe operation
names and retryability. Security rejections and invalid arguments are not
fixed by immediate retry.

## Security test checklist

Test at least:

- denied required and optional capabilities;
- duplicate or malformed identifiers;
- access-policy timeout, exception and explicit denial;
- proof that `allow()` cannot override a core rejection;
- handler invocation before authentication/negotiation (must not happen);
- oversized, malformed, replayed and stale-generation payloads;
- queue and rate exhaustion without starving Minecraft traffic;
- diagnostic secret canaries and path redaction;
- resource cleanup after addon failure and runtime restart;
- headless launch without client classes.

The testkit helps prove API behavior, but it cannot sandbox or certify an
arbitrary third-party JAR. Review source and distribution practices before
recommending an addon.
