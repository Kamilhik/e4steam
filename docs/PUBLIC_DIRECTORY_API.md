# Public Directory API 1.1

[Русская версия](PUBLIC_DIRECTORY_API_RU.md)

`PublicDirectoryService` is the stable boundary between e4steam and an addon
that publishes or browses public player worlds and dedicated servers. It is
available in Addon API 1.1.0 and requires e4steam 0.3.2 or newer.

The directory addon owns the catalog UI and registry protocol. e4steam owns
Steam authentication, connection secrets, the current session generation and
the actual join. The API never returns a raw Steam ticket, invite token, IP
address, native Steamworks object or core packet.

## Dependency and version range

Until API 1.1.0 is published separately to Maven Central, use the API JAR from
the development kit or this repository:

```groovy
dependencies {
    compileOnly(files("libs/e4steam-api-1.1.0.jar"))
}
```

Do not install this JAR in `mods` and do not include it in the addon artifact.
The e4steam runtime supplies the API classes.

An addon using this service should declare:

```java
new ApiVersionRange(
        ApiVersion.parse("1.1.0"),
        ApiVersion.parse("2.0.0")
)
```

Use an open-ended e4steam loader dependency such as `>=0.3.2` on
Fabric/Quilt or `[0.3.2,)` on Forge/NeoForge. Never require exactly 0.3.2.

## Capabilities

Request only the operations the addon needs:

| Capability | Allows |
| --- | --- |
| `DIRECTORY_PUBLICATION` | Obtain an opaque target for the active host generation |
| `DIRECTORY_ATTESTATION` | Ask core for a registry-bound publisher receipt |
| `DIRECTORY_JOIN` | Join and cancel an opaque target through core |

Add each mandatory capability to both `requestedCapabilities` and
`requiredCapabilities`. A denied required capability disables the addon before
`initialize`; an optional denial should disable only the related feature.

## Availability

```java
PublicDirectoryService directory = context.api().publicDirectory();
ApiResult<PublicDirectoryService.DirectoryAvailability> result =
        directory.availability();
```

The safe availability states are:

| State | Meaning |
| --- | --- |
| `AVAILABLE` | A compatible client or dedicated runtime is active |
| `NO_ACTIVE_SESSION` | Core is present, but there is nothing to publish or join yet |
| `STEAM_UNAVAILABLE` | Steam startup failed or the current runtime was lost |
| `UNSUPPORTED` | The current environment cannot provide this bridge |

Availability is a snapshot, not a permanent promise. Every operation must
still handle a typed failure or a session change.

## Publishing an active target

```java
PublicDirectoryService.DirectoryOrigin origin =
        new PublicDirectoryService.DirectoryOrigin("https://registry.example");
PublicDirectoryService.PublicationTargetRequest request =
        new PublicDirectoryService.PublicationTargetRequest(
                PublicDirectoryService.TargetKind.INTEGRATED_WORLD,
                origin
        );

directory.publicationTarget(request).thenAccept(result -> {
    if (!result.isSuccess() || !result.value().isPresent()) return;
    PublicDirectoryService.PublicationTarget target = result.value().get();
    // Send target.targetRef() and target.generation() to the trusted registry.
});
```

`TargetKind` is either `INTEGRATED_WORLD` or `DEDICATED_SERVER`. The returned
reference is opaque and generation-bound. When the world closes, the server
drains or a new session starts, remove the old listing. Do not try to decode or
reuse the reference as identity.

An integrated target is available only after the user selected a registered
public access mode and the Steam lobby became ready. A dedicated target is
available only while the protected dedicated backend is accepting players.

## Publisher attestation

A registry creates a short-lived challenge containing an origin, challenge ID,
nonce, action and expiry. The addon passes that challenge to core:

```java
directory.attest(challenge).thenAccept(result -> {
    if (!result.isSuccess() || !result.value().isPresent()) return;
    PublicDirectoryService.AttestationReceipt receipt = result.value().get();
    // Return the bounded receipt to the same registry origin.
});
```

Valid actions are `PUBLISH_INTEGRATED`, `PUBLISH_DEDICATED`, `UPDATE` and
`DELETE`. Core binds proof to the challenge and returns only a receipt. The raw
proof material never crosses the API.

`DirectoryOrigin` accepts HTTPS origins without credentials, path, query or
fragment. Plain HTTP is rejected, except for literal loopback hosts used by an
explicit local development setup.

The current 0.3.2 development runtime deliberately fails closed for production
HTTPS attestation until a trusted registry verifier is configured. An opt-in
loopback HMAC path exists only for local integration tests. It must never be
exposed to a LAN or the internet and is not proof of a Steam identity.

## Joining a directory target

The registry returns an opaque target and a fresh one-use join handle:

```java
PublicDirectoryService.JoinRequest request =
        new PublicDirectoryService.JoinRequest(
                new PublicDirectoryService.OpaqueTargetRef(targetFromRegistry),
                new PublicDirectoryService.JoinHandleRef(handleFromRegistry)
        );

directory.join(request).thenAccept(result -> {
    if (!result.isSuccess() || !result.value().isPresent()) return;
    PublicDirectoryService.JoinOperation operation = result.value().get();
    currentOperation = operation.id();
});
```

Keep only the local `JoinOperationId`. A join handle cannot be replayed. Poll
`joinSnapshot(id)` at a bounded UI rate, and call `cancel(id)` when the user
closes the screen. Cancellation is idempotent and late Steam callbacks cannot
turn a cancelled or failed operation into success.

| Join state | Meaning |
| --- | --- |
| `IDLE` / `RESOLVING` | Core is validating the request |
| `JOINING_STEAM_TARGET` | Steam is joining the lobby or resolving the target |
| `AUTHENTICATING` | The authenticated e4steam handshake is running |
| `CONNECTING_MINECRAFT` | Minecraft is opening the local bridge |
| `ACTIVE` | The matching session is active |
| `CANCELLED` | The user or addon cancelled the attempt |
| `STALE` | The target or generation no longer exists |
| `FULL` | Host capacity was reached |
| `INCOMPATIBLE` | Minecraft, protocol or required addon compatibility failed |
| `FAILED` | A bounded non-specific failure occurred |

Treat `detailCode()` as a localization-safe category, not exception text.

## Public access modes

A directory addon can register an `AccessService.AccessModeProvider`. If
opening that mode publishes the world, implement
`AccessService.ConfirmableAccessModeProvider` and provide localization keys for
the confirmation title and message. Publication starts only after explicit
user confirmation and successful world startup.

The addon's admission decision runs after mandatory core checks. It cannot
override a bad endpoint token, stale generation, failed Steam authentication,
protocol mismatch, ban, replay detection or capacity limit. Missing providers
and provider exceptions fail closed.

## Loader discovery

- Fabric and Quilt: declare the `e4steam` entrypoint in `fabric.mod.json`.
- Forge and NeoForge: provide
  `META-INF/services/link.e4steam.api.addon.E4steamAddonEntrypoint`.

Keep Minecraft GUI code in loader/version-specific source sets. Shared API code
must not reference client classes, which keeps dedicated-server startup safe.

## Verification checklist

Before publishing a directory addon:

1. Compile against API 1.1.0 with `compileOnly`.
2. Confirm the final JAR contains no `link/e4steam/api/**`, Steam natives,
   absolute paths or credentials.
3. Test missing Steam, missing capability, missing addon configuration and an
   incompatible API range without a crash.
4. Test one-use handles, expiry, cancellation, stale targets, full hosts and
   repeated UI opening.
5. Test both integrated and dedicated publication lifecycle cleanup.
6. Keep registry requests bounded by size, timeout, rate and redirect policy.
7. Complete a two-account host/guest test before making a compatibility claim.

Automated local-registry tests validate the contract, but production use still
requires a trusted attestation verifier and real two-user testing.
