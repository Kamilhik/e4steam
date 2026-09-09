# Addon API compatibility

e4steam has several independent version numbers. An addon must not treat them
as interchangeable.

| Version | e4steam 0.3.2 | Who controls it |
| --- | --- | --- |
| Mod release | `0.3.2` | e4steam release |
| Java Addon API | `1.1.0` | Public Java contracts |
| Core wire protocol | `4` | e4steam host/guest transport |
| Addon channel protocol | Chosen by the addon | One registered channel |

Two e4steam releases can expose the same Addon API. An addon channel can also
change versions without changing the core wire protocol.

API 1.1.0 preserves every public and protected binary symbol from API 1.0.0.
An addon compiled against 1.0.0 therefore remains valid on a compatible 1.1
runtime. An addon that calls `publicDirectory()` must compile against 1.1.0 and
require e4steam 0.3.2 or newer.

## Compile-only dependency

API 1.1.0 is published on Maven Central. Addons compiled with API 1.0.0 remain
compatible:

```groovy
dependencies {
    compileOnly("io.github.kamilhik:e4steam-api:1.1.0")
}
```

No custom repository is required. Use `compileOnly`: the e4steam runtime
provides the API classes. Bundling a second API copy into the addon can create
class identity and loader conflicts.

The same API JAR is also available from the
development kit or `api/build/libs`:

```groovy
dependencies {
    compileOnly(files("libs/e4steam-api-1.1.0.jar"))
}
```

Do not place either API JAR in the game's `mods` directory. The 0.3.2 runtime
already contains the implementation and the public classes.

The API artifact targets Java 8 and contains no Minecraft, Fabric, Quilt,
Forge, NeoForge, JNA, Steamworks or e4steam internal implementation classes.
This is what allows one API-facing addon core to be shared across loaders.
The embedded runtime is currently packaged in e4steam's Minecraft 1.17+
artifacts. Retro 1.7–1.16 builds keep their older loader boundary and do not
discover Addon API entrypoints.

## Declaring the supported API range

An addon descriptor uses `ApiVersionRange`. The minimum is inclusive and the
maximum is exclusive:

```java
new ApiVersionRange(
        ApiVersion.parse("1.0.0"),
        ApiVersion.parse("2.0.0")
)
```

This means “API 1.0.0 or newer, but not API 2.0.0”. e4steam checks the range
before calling the addon entry point. An incompatible addon is rejected with a
typed sanitized error rather than being allowed to fail halfway through
initialization.

For an addon that needs Public Directory API 1.1, use `1.1.0` as the minimum.
Do not use the mod version as this range. `0.3.2` is not the Addon API version.

## Semantic-versioning promise

Stable API types outside `link.e4steam.api.experimental` follow semantic
versioning:

- patch: fixes that keep source and binary contracts compatible;
- minor: compatible additions, normally new types, constants or services;
- major: changes that can break an already compiled addon.

Adding an abstract method to an existing interface can break binary
compatibility and normally requires a major API version. A new optional service
behind a new `ServiceKey` is usually safer. Default methods may be used only
when their behavior is meaningful for older implementations.

Types marked `@ExperimentalApi` do not receive the same stability guarantee.
Use them only with an explicit fallback and a narrower compatibility claim.

## Capabilities and optional services

API compatibility does not guarantee that every capability is available in
every runtime mode. A client UI service can be unavailable on a headless
server. A future or optional service may be absent from `ServiceRegistry`.

The addon should:

1. declare requested capabilities in `AddonDescriptor`;
2. mark only truly necessary capabilities as required;
3. check an optional capability/service before using it;
4. degrade cleanly when the current loader, platform or mode cannot provide it.

A missing required capability disables the addon with a typed error. A missing
optional capability should disable only the related feature.

## Channel compatibility

Each `NetworkService.ChannelDescriptor` declares its own minimum and maximum
integer protocol version. This is independent from Java API 1.1.0.

For a backward-compatible payload addition, keep the old fields readable and
negotiate the highest common version. For an incompatible schema, introduce a
new channel version or a new channel ID. Required channels can reject addon
activation; optional channels leave the base Minecraft connection available.

Core wire protocol 4 transports manifests and negotiated addon traffic. Addons
do not read or write raw core frames and should not branch on internal packet
layout.

## Loader and Minecraft compatibility

Addon API code is loader-independent, but discovery still needs a normal
loader entry. Fabric and Quilt use the `e4steam` entrypoint in
`fabric.mod.json`; Forge and NeoForge use
`META-INF/services/link.e4steam.api.addon.E4steamAddonEntrypoint`. The addon's
published files must state which Minecraft versions and loaders contain that
metadata and any UI/game integrations.

Do not claim a Minecraft range merely because the API artifact is Java 8. Test
the loader adapter, entry point, required services and actual feature on each
claimed baseline.

## Compatibility checks in this repository

`api/api-surface.sha256` records the exact API 1.1 public reflection surface.
`apiBinaryCompatibilityCheck` rejects accidental changes to that surface, and
`apiBackwardCompatibilityCheck` compares it with the published 1.0.0 artifact.
JAR audits reject forbidden Minecraft, loader, JNA, Steamworks and internal
types in the API artifact.

Run:

```powershell
.\gradlew.bat --no-daemon apiChecks
```

or on Linux/macOS:

```bash
./gradlew --no-daemon apiChecks
```

These checks catch many binary and packaging mistakes. They do not prove that a
loader discovers the addon or that two real clients negotiate its channel.

## When changing an addon

For every release:

- keep the addon ID stable;
- update the addon version separately from API and channel versions;
- widen the API range only after testing against the new API;
- add migration code before changing stored schema versions;
- preserve old channel decoding while claiming compatibility;
- document removed capabilities or loader targets;
- test an upgrade from the previous released addon JAR.

If a breaking migration is unavoidable, publish a clear compatibility table and
reject unsupported combinations before joining rather than allowing corrupted
state or an endless connection timeout.
