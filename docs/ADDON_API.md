# e4steam Addon API

Status: API `1.0.0` implemented on the unreleased e4steam `0.3.0` branch.
The public artifact is loader-independent Java 8 code under
`link.e4steam.api`; it has no Minecraft, loader, JNA, Steamworks4j or e4steam
implementation dependency. `:api-testkit` provides deterministic fakes and
`:example-addon` is compiled and tested against only this public surface.

## Services and maturity

| Area | Public entry point | 0.3.0 status |
| --- | --- | --- |
| Runtime, lifecycle, events, scheduler, resources | `E4steamApi` | Implemented |
| Identity and sessions | `IDENTITIES`, `SESSIONS` | Implemented safe projections |
| Access and lobbies | `ACCESS`, `LOBBIES` | Implemented bounded contracts/adapters |
| Addon channels and virtual UDP | `NETWORK`, `UDP` | Implemented; negotiated after core auth |
| UI and commands | `UI`, `COMMANDS` | Implemented neutral contribution registry |
| Config and private storage | `CONFIG`, `STORAGE` | Implemented with validation, quotas and path confinement |
| Dedicated server | `DEDICATED` | Implemented contract; runtime remains experimental |
| World settings | `WORLD_SETTINGS` | Proposal contract only; no settings UI in core |
| Modpack and skins | `MODPACKS`, `SKINS` | Provider/staging contracts only; no downloader/provider in core |
| Diagnostics, localization and logging | `DIAGNOSTICS`, `LOCALIZATION`, `LOGGER` | Implemented bounded/redacted contracts |

Loader discovery is wired through Fabric metadata and normal Java service
metadata used by Forge/NeoForge. e4steam does not scan, download or execute
arbitrary addon JARs. Descriptors are dependency-sorted and checked for API
ranges, cycles, duplicate IDs and requested capabilities before initialization.

## Entry point

Implement `E4steamAddonEntrypoint` (or the compatible `E4steamAddon` contract),
declare an `AddonDescriptor`, and register every `Registration`,
`Subscription` and `TaskHandle` in `context.resources()`. Use typed keys in
`ApiServiceKeys`; each addon receives a scoped view containing only granted
capabilities. Registration is frozen before active channel negotiation.

API, mod and wire versions are independent:

- Java API: `1.0.0`;
- e4steam development version: `0.3.0`;
- core wire protocol: `4`;
- each addon network channel declares its own compatible range.

## Build and compatibility checks

```text
./gradlew apiChecks
```

This compiles Java 8 bytecode, runs API/privacy/testkit/example tests, creates
Javadocs, rejects forbidden dependencies and validates
`api/api-surface.sha256`. A future incompatible Java API change requires an
intentional major-version decision. Experimental types under
`link.e4steam.api.experimental` have no binary guarantee.

The API is a least-privilege integration boundary, not a JVM sandbox. Install
only trusted addons. Passwords, authentication tickets, GSLT, cookies, invite
secrets, native handles and raw protocol/native callbacks are never API data.
