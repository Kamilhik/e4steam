# e4steam documentation

This page is the starting point for the project documentation. Pick the task
you are trying to complete; you do not need to read every file.

## Players

| What you want to do | Read this |
| --- | --- |
| Install e4steam and play with a friend | [Getting started](GETTING_STARTED.md) or [руководство на русском](GETTING_STARTED_RU.md) |
| Find the correct JAR for a loader and Minecraft version | [Compatibility matrix](../COMPATIBILITY.md) |
| Fix `SteamAPI_Init failed` or a missing Spacewar session | [Steam troubleshooting](STEAM_TROUBLESHOOTING.md) · [на русском](STEAM_TROUBLESHOOTING_RU.md) |
| Understand `/e4steam doctor` and safely share a report | [Diagnostics](DIAGNOSTICS.md) · [на русском](DIAGNOSTICS_RU.md) |
| Run e4steam on macOS | [macOS guide](MACOS.md) · [на русском](MACOS_RU.md) |
| Try Steam overlay injection on Linux or macOS | [Unix overlay relaunch](UNIX_OVERLAY.md) · [на русском](UNIX_OVERLAY_RU.md) |
| Use Minecraft 1.7.x-1.16.x | [Retro builds](RETRO_PORTING.md) · [на русском](RETRO_PORTING_RU.md) |

## Dedicated-server administrators

Start with the [dedicated-server overview](DEDICATED_SERVER.md) or its
[Russian version](DEDICATED_SERVER_RU.md). It explains
what the `d-...steam` address is and how traffic reaches Minecraft.

Then use:

- [Deployment guide](DEDICATED_DEPLOYMENT.md) or
  [руководство по запуску](DEDICATED_DEPLOYMENT_RU.md) for installation,
  configuration, startup and console commands;
- [Security model](DEDICATED_SECURITY.md) or
  [модель безопасности](DEDICATED_SECURITY_RU.md) for loopback binding,
  Steam-only admission, identity and safe operation;
- [Compatibility matrix](../COMPATIBILITY.md) for the exact combinations that
  have recorded launch or join evidence.

## Addon developers

The Addon API documentation is intentionally more technical than the player
guides. Begin with the full [English guide](ADDON_API.md) or
[русское руководство](ADDON_API_RU.md), then open the contract you need:

| Topic | Guide |
| --- | --- |
| Discovery, initialization and cleanup | [Addon lifecycle](ADDON_LIFECYCLE.md) |
| Threads, scheduling and cancellation | [API threading](API_THREADING.md) |
| Negotiated channels and virtual UDP | [API networking](API_NETWORKING.md) |
| Capabilities and core admission rules | [Addon security](ADDON_SECURITY.md) |
| Personal data, diagnostics and redaction | [API privacy](API_PRIVACY.md) |
| API, mod and wire versioning | [API compatibility](API_COMPATIBILITY.md) |

API `1.0.0` is published on
[Maven Central](https://central.sonatype.com/artifact/io.github.kamilhik/e4steam-api/1.0.0).
The repository also contains a compile-checked `example-addon` and the
deterministic `api-testkit`.

## Contributors and maintainers

- [Contributing](../CONTRIBUTING.md) explains issue reports, development setup
  and pull-request expectations.
- [Testing](TESTING.md) separates build, JAR audit, client launch and real
  multiplayer evidence.
- [Release checklist](../RELEASING.md) is for maintainers preparing a release.
- [Security policy](../SECURITY.md) explains supported releases and private
  vulnerability reporting.
- [Dependency report](DEPENDENCY_LICENSE_REPORT.md) and
  [third-party notices](../THIRD_PARTY_NOTICES.md) cover redistributed code and
  native libraries.

## How status words are used

- **Supported** means the project intends to maintain that combination.
- **Manually verified** means a named scenario was run with a recorded build.
- **Automatically tested** means a test or artifact audit passed; it does not
  prove that two real Steam clients connected.
- **Experimental** means the code and artifact are available, but manual
  coverage is incomplete.
- **Unsupported** means no working artifact is promised.

The [compatibility matrix](../COMPATIBILITY.md) keeps these kinds of evidence
separate so a successful compile is never presented as a multiplayer test.
