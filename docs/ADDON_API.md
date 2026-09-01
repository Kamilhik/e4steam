# e4steam Addon API 1.0

[Русская версия](ADDON_API_RU.md)

[![Maven Central](https://img.shields.io/maven-central/v/io.github.kamilhik/e4steam-api?label=Maven%20Central&style=flat-square)](https://central.sonatype.com/artifact/io.github.kamilhik/e4steam-api/1.0.0)

The Addon API lets a normal Fabric, Forge or NeoForge mod extend e4steam
without copying its Steam runtime or depending on Minecraft internals. API
`1.0.0` is included in e4steam `0.3.1`, remains compatible with e4steam
`0.3.0+`, and targets Java 8 bytecode.
The signed release artifact, sources, Javadocs and POM are published on
[Maven Central](https://repo1.maven.org/maven2/io/github/kamilhik/e4steam-api/1.0.0/).

> [!IMPORTANT]
> An addon is ordinary code running in the Minecraft JVM. The API limits what
> e4steam exposes, but it is not a security sandbox. Install only addons you
> trust.

## Guide map

This page is the complete starting tutorial. The focused contract guides go
deeper where correctness matters:

| Topic | Detailed guide |
| --- | --- |
| Discovery, validation, initialization and cleanup | [Addon lifecycle](ADDON_LIFECYCLE.md) |
| Execution contexts, timeouts and cancellation | [Threading and scheduling](API_THREADING.md) |
| Negotiated channels, encoding, backpressure and virtual UDP | [Networking](API_NETWORKING.md) |
| Capabilities, admission order and failure isolation | [Security](ADDON_SECURITY.md) |
| Identity, logs, diagnostics and storage | [Privacy](API_PRIVACY.md) |
| API/mod/wire/channel versions and Maven usage | [Compatibility](API_COMPATIBILITY.md) |

For exact signatures, generate Javadocs or open the source under
`api/src/main/java`. The compile-checked `example-addon` is the canonical code
sample; documentation examples intentionally show only the relevant part.

## Quick start

### 1. Add the API dependency

Version `1.0.0` is available from Maven Central. Most mod projects already
declare `mavenCentral()` in `repositories`, so no custom repository is needed.
Add one compile-time dependency because e4steam supplies the API at runtime:

~~~groovy
dependencies {
    compileOnly("io.github.kamilhik:e4steam-api:1.0.0")
}
~~~

Do not shade or bundle `link.e4steam.api` classes into the addon JAR.

### 2. Create an entry point

~~~java
package example.hello;

import link.e4steam.api.ApiVersion;
import link.e4steam.api.ApiVersionRange;
import link.e4steam.api.addon.AddonContext;
import link.e4steam.api.addon.AddonDescriptor;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.addon.E4steamAddonEntrypoint;

import java.util.Collections;

public final class HelloAddon implements E4steamAddonEntrypoint {
    @Override
    public AddonDescriptor descriptor() {
        return new AddonDescriptor(
                new AddonId("example:hello"),
                "Hello addon",
                ApiVersion.parse("1.0.0"),
                new ApiVersionRange(
                        ApiVersion.parse("1.0.0"),
                        ApiVersion.parse("2.0.0")
                ),
                Collections.emptyList(),
                Collections.emptySet()
        );
    }

    @Override
    public void initialize(AddonContext context) {
        // Register services, events, commands and UI contributions here.
    }
}
~~~

Addon IDs must be namespaced lower-case identifiers such as
`yourstudio:your_addon`. The API range has an inclusive minimum and an
exclusive maximum, so `[1.0.0, 2.0.0)` accepts every compatible 1.x API.

### 3. Let the loader discover the addon

Fabric uses the `e4steam` entrypoint in `fabric.mod.json`:

~~~json
{
  "entrypoints": {
    "e4steam": [
      "example.hello.HelloAddon"
    ]
  },
  "depends": {
    "e4steam": ">=0.3.0"
  }
}
~~~

Forge and NeoForge use Java service metadata. Create:

~~~text
src/main/resources/META-INF/services/link.e4steam.api.addon.E4steamAddonEntrypoint
~~~

Its content is the implementation class name:

~~~text
example.hello.HelloAddon
~~~

The addon must also remain a normal loader mod with its usual
`mods.toml` or `neoforge.mods.toml`. e4steam never scans arbitrary JAR files.

## Capabilities

An addon declares the e4steam features it wants in `AddonDescriptor`.
Requested capabilities are optional. Required capabilities are a subset that
must be granted or the addon is rejected before `initialize` runs.

~~~java
Set<CapabilityId> requested = new LinkedHashSet<>(Arrays.asList(
        Capabilities.SESSION_OBSERVE,
        Capabilities.UI_CONTRIBUTE,
        Capabilities.COMMANDS_REGISTER
));

return new AddonDescriptor(
        new AddonId("example:status"),
        "Status addon",
        ApiVersion.parse("1.0.0"),
        new ApiVersionRange(ApiVersion.parse("1.0.0"), ApiVersion.parse("2.0.0")),
        Collections.emptyList(),
        requested,
        requested
);
~~~

| Goal | Capability examples | Service |
| --- | --- | --- |
| Observe or control a session | `SESSION_OBSERVE`, `SESSION_CONTROL` | `api.sessions()` |
| Read safe identities | `IDENTITY_MINECRAFT_READ`, `IDENTITY_STEAM_PROFILE_READ` | `api.identities()` |
| Work with access and lobbies | `ACCESS_MODE_REGISTER`, `LOBBY_CREATE`, `LOBBY_SEARCH` | `api.access()`, `api.lobbies()` |
| Add network or virtual UDP traffic | `NETWORK_CHANNEL_REGISTER`, `UDP_PROVIDER_REGISTER` | `api.network()`, `api.udp()` |
| Add UI and commands | `UI_CONTRIBUTE`, `COMMANDS_REGISTER` | `api.ui()`, `api.commands()` |
| Store addon settings and data | `CONFIG_READ`, `CONFIG_WRITE`, `STORAGE_PRIVATE` | `api.config()`, `api.storage()` |
| Observe or administer a dedicated backend | `DEDICATED_OBSERVE`, `DEDICATED_ADMIN`, `DEDICATED_PUBLICATION_PROPOSE` | `api.dedicatedServers()` |
| Contribute diagnostics | `DIAGNOSTICS_CONTRIBUTE` | `api.diagnostics()` |

World settings, modpack staging and skins are contracts for separate addons.
The e4steam core does not ship those user-facing features.

## Dedicated-server API

`DedicatedServerService` is available from
`context.api().dedicatedServers()`. It works only when e4steam is running in
dedicated mode; on a normal client its methods return a typed unsupported or
unavailable result.

Request `DEDICATED_OBSERVE` to read state and wait for readiness:

~~~java
import link.e4steam.api.ApiResult;
import link.e4steam.api.dedicated.DedicatedServerService;

DedicatedServerService dedicated = context.api().dedicatedServers();
ApiResult<DedicatedServerService.DedicatedServerSnapshot> result =
        dedicated.snapshot();

if (result.isSuccess() && result.value().isPresent()) {
    DedicatedServerService.DedicatedServerSnapshot snapshot =
            result.value().get();
    boolean acceptingPlayers = snapshot.state()
            == DedicatedServerService.DedicatedServerState.ACCEPTING;
}
~~~

The snapshot contains the lifecycle state, access mode, player count, capacity
and ingress/publication flags. `config()` returns a redacted configuration, and
`readiness()` completes after the transport, ingress guard and Minecraft are
ready. `DedicatedStateEvent` provides replayable state changes.

`drain(reasonCode)` requires `DEDICATED_ADMIN` and stops e4steam sharing; it
does not terminate the Minecraft process. Publication proposals require
`DEDICATED_PUBLICATION_PROPOSE`; core can still reject them when no approved
provider is active or the server configuration disallows publication. The API
never returns Steam tickets, GSLT, raw descriptor contents, native handles or
protocol packets.

e4steam core 0.3.1 has no publication provider and returns
`public-worlds-addon-required`; the service exists so a separate trusted addon
can integrate without exposing the GameServer internals.

## Own every resource

Subscriptions, registrations, channel handles and scheduled tasks must be
attached to `context.resources()`. e4steam then closes them in reverse order
when the addon or runtime stops.

~~~java
ApiResult<Subscription> result = context.api().events().subscribe(
        RuntimeReadyEvent.TYPE,
        event -> {
            // Keep this callback short and non-blocking.
        }
);

if (!result.isSuccess() || !result.value().isPresent()) {
    throw new IllegalStateException("Runtime subscription was rejected");
}
context.resources().own(result.value().get());
~~~

Do not create a second Steam runtime, callback loop, lobby manager or native
transport. Use the services supplied in `AddonContext`.

## Lifecycle

1. The installed mod loader discovers the entry point.
2. e4steam validates IDs, versions, dependencies and capabilities.
3. Addons are dependency-sorted.
4. `initialize(context)` registers resources.
5. Registration freezes before network-channel negotiation.
6. All owned resources are closed automatically on shutdown.

Initialization must not block the Minecraft thread or a Steam callback thread.
Use `api.scheduler()` and the named execution contexts for asynchronous work.
Return UI work to the Minecraft client thread through the loader-specific
adapter in your addon.

## Networking rules

- Addon channel IDs must be namespaced and versioned.
- Register channels during initialization, before registration freezes.
- A channel opens only after e4steam core authentication and compatibility
  negotiation succeed on both peers.
- Declare payload, queue and delivery bounds; never create an unbounded queue.
- Treat every received payload as untrusted input.
- Never put passwords, Steam tickets, join secrets, cookies or native handles
  into addon messages, storage or diagnostics.

See [API networking](API_NETWORKING.md), [threading](API_THREADING.md) and
[privacy](API_PRIVACY.md) for the complete rules.

## Services at a glance

| Area | Method |
| --- | --- |
| Runtime, addons, capabilities, events, scheduler | `runtime()`, `addons()`, `capabilities()`, `events()`, `scheduler()` |
| Identity and sessions | `identities()`, `sessions()` |
| Access and lobbies | `access()`, `lobbies()` |
| Network and UDP | `network()`, `udp()` |
| UI and commands | `ui()`, `commands()` |
| Config and private storage | `config()`, `storage()` |
| Dedicated server | `dedicatedServers()` |
| Optional provider contracts | `worldSettings()`, `modpacks()`, `skins()` |
| Diagnostics, localization and logging | `diagnostics()`, `localization()`, `logger()` |

## Testing

From the e4steam repository:

~~~text
gradlew.bat apiChecks
~~~

This verifies Java 8 bytecode, public API compatibility, forbidden
dependencies, Javadocs, the deterministic testkit and the example addon.
`api-testkit/build/libs/e4steam-api-testkit-1.0.0.jar` can be used in addon
tests for deterministic fakes without starting Minecraft or Steam.

The repository's `example-addon` demonstrates events, a negotiated channel,
UI, commands, config, storage and scheduler ownership. It is a compile-tested
neutral example, not a directly installable loader mod because it deliberately
contains no Fabric, Forge or NeoForge metadata.

After `gradlew.bat :api:javadoc`, open
`api/build/docs/javadoc/index.html` for the complete class and method reference.

## Example from a released addon: e4steam Friends

[e4steam Friends](https://github.com/K2-Studio-Development/e4steam-Friends) is
the first full e4steam addon. It provides a Minecraft-style Steam friends
screen, presence, search, invitations, joining and join requests for Fabric
and NeoForge on Minecraft 26.2.

Use that project as a reference for loader packaging, client lifecycle and UI
integration. For public API calls, follow this repository's `example-addon`.
e4steam Friends has an isolated compatibility bridge for social data that API
1.0 does not expose yet. Do not copy that bridge or depend on
`link.e4steam.internal` classes in a new addon.

## Common mistakes

- Bundling API classes inside the addon JAR.
- Forgetting the Fabric `e4steam` entrypoint or Forge/NeoForge service file.
- Requesting a capability but calling the service as if it were guaranteed.
- Forgetting `context.resources().own(...)`.
- Registering a network channel after initialization.
- Calling Steamworks directly or shipping another copy of its natives.
- Updating Minecraft UI from an addon worker thread.
- Logging raw identifiers, tokens or payloads.

API, mod and wire versions are independent:

- Addon API: `1.0.0`;
- e4steam mod: `0.3.1`;
- core wire protocol: `4`;
- each addon network channel has its own version range.

The public Addon API 1.0 surface is stable. Only types deliberately separated
under `link.e4steam.api.experimental` are outside the binary-compatibility
guarantee; this does not make installed addons or the addon system experimental.
