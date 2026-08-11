# e4steam Addon API

Status: API `0.1.0` foundation for the unreleased e4steam `0.3.0` branch.

The `:api` module is a loader-independent Java 8 artifact under
`link.e4steam.api`. It has no Minecraft, Fabric, Quilt, Forge, NeoForge, JNA,
Steamworks4j or internal protocol dependency. `:api-testkit` supplies fakes and
the neutral `:example-addon` module is compiled against the public surface.

## Implemented foundation

| Area | Main types | Maturity |
| --- | --- | --- |
| Versions/errors | `ApiVersion`, `ApiVersionRange`, `ApiResult`, `ApiError` | Stable baseline |
| Addon metadata | `AddonId`, `AddonDescriptor`, `AddonState`, `AddonContext` | Stable contracts |
| Capabilities | `CapabilityId`, `Capabilities`, `CapabilityService` | Stable contracts |
| Runtime status | `RuntimeService`, `RuntimeSnapshot` | Stable contracts |
| Events | `EventService`, `EventType`, immutable runtime events | Stable foundation |
| Scheduling | `SchedulerService`, named contexts, `TaskHandle` | Stable contracts |
| Ownership | `Registration`, `Subscription`, `ResourceScope` | Stable contracts |
| Future services | `ServiceKey`, `ServiceRegistry` | Stable extension point |
| Experimental | `link.e4steam.api.experimental` | No binary promise |

Loader discovery and production runtime adapters are not wired by this first
foundation PR. Installing the example does not add Public Worlds, Modpack
Sync, Offline Skins, dedicated servers or any other end-user feature.

## Build and test

```text
./gradlew apiChecks
```

This compiles the API to Java 8 bytecode, runs contract/privacy tests, builds
Javadocs, compiles the testkit and example, rejects forbidden dependencies and
checks the canonical public surface hash.

## Entry point

Implement `E4steamAddon.initialize(AddonContext)`. Register every returned
`Registration`, `Subscription` or `TaskHandle` in `context.resources()` so it
is closed on disable/failure/shutdown. Read only services/capabilities visible
through the scoped `E4steamApi` instance.

The API is not a sandbox. An addon is ordinary code in the same JVM and must be
installed only from a trusted source.
