# Retro porting and compatibility

The unreleased 0.3.0 branch contains isolated Java 8 toolchains and exact JARs.
All entries are currently **build-only**: compilation/JAR audits are evidence
of packaging, not proof that Minecraft launches or Steam multiplayer works.

## Exact artifact matrix

| Minecraft | Loader | Exact artifact | Status |
| --- | --- | --- | --- |
| 1.6.4 | Forge 9.11.1.1345 | `e4steam-forge-mc1.6.4-v0.3.0.jar` | Build-only |
| 1.7.10 | Forge 10.13.4.1614 | `e4steam-forge-mc1.7.10-v0.3.0.jar` | Build-only |
| 1.8.9 | Forge 11.15.1.2318 | `e4steam-forge-mc1.8.9-v0.3.0.jar` | Build-only |
| 1.9.4 | Forge 12.17.0.2317 | `e4steam-forge-mc1.9.4-v0.3.0.jar` | Build-only |
| 1.10.2 | Forge 12.18.3.2511 | `e4steam-forge-mc1.10.2-v0.3.0.jar` | Build-only |
| 1.11.2 | Forge 13.20.1.2588 | `e4steam-forge-mc1.11.2-v0.3.0.jar` | Build-only |
| 1.12.2 | Forge 14.23.5.2847 | `e4steam-forge-mc1.12.2-v0.3.0.jar` | Build-only |
| 1.13.2 | Forge 25.0.223 | `e4steam-forge-mc1.13.2-v0.3.0.jar` | Build-only |
| 1.14.4 | Forge 28.2.26 | `e4steam-forge-mc1.14.4-v0.3.0.jar` | Build-only |
| 1.15.2 | Forge 31.2.57 | `e4steam-forge-mc1.15.2-v0.3.0.jar` | Build-only |
| 1.16.5 | Forge 36.2.42 | `e4steam-forge-mc1.16.5-v0.3.0.jar` | Build-only |
| 1.14.4 | Fabric Loader 0.16.14 | `e4steam-fabric-mc1.14.4-v0.3.0.jar` | Build-only |
| 1.15.2 | Fabric Loader 0.16.14 | `e4steam-fabric-mc1.15.2-v0.3.0.jar` | Build-only |
| 1.16.5 | Fabric Loader 0.16.14 | `e4steam-fabric-mc1.16.5-v0.3.0.jar` | Build-only |

The three Fabric versions reflect the owner's later explicit scope expansion.
There is no Legacy Fabric, Ornithe, Rift or retro Quilt artifact and no generic
multi-version retro JAR.

## Architecture

`retro/core` reuses the current e4steam Steam Networking Messages transport,
App ID 480 lifecycle, retry/native-cache fixes and compact address model while
remaining Java 8-compatible. Exact loader modules provide only version-specific
entrypoints and LAN listener hooks. Client bootstrap is loaded reflectively
after physical-side checks; Forge server entrypoint constant pools are audited
to reject eager Minecraft client or Steam client runtime references.

Every retro artifact contains the same audited set of exactly nine x86-64/
arm64 client and GameServer natives used by the universal modern artifacts.
Legacy 32-bit and encrypted-app-ticket binaries are deliberately excluded.

The adapted listener hooks create a loopback-only secondary endpoint and pass
its port to e4steam. World close stops the Steam relay. No Cloudflare,
Quiclime, cloudflared, broker, endpoint or telemetry implementation was copied.
The existing six modern artifacts are built by the independent root/legacy
projects and are not replaced by retro modules.

## Reference and attribution

Architecture, build/mapping knowledge and the four Minecraft listener mixins
were adapted from [`xhyrom/e4mc-retro`](https://github.com/xhyrom/e4mc-retro),
licensed Apache-2.0. Runtime/backend code was independently replaced with
e4steam's Steam transport. Audited upstream revisions:

| Upstream branch | Commit SHA | Used for |
| --- | --- | --- |
| `1.6.x` | `906261a35fd18cd8362bd2232c1cf54d7c37f180` | 1.6.4 lifecycle/build/mixin seam |
| `1.7.x` | `00d98a2236c8575c3f521bba249d2175b61456a7` | 1.7.10 Forge setup |
| `1.8.x` | `0d9894c09e41973dabc5273bf006e4d58107a224` | 1.8.9 Forge setup |
| `1.9-1.12.x` | `5802050583a1ea24dceec5b1f1092ab4cb4a8070` | 1.9.4–1.12.2 listener/build seams |
| `1.13.x` | `938c95d909ae5f8ea965cf85b5cf1c86d7fc1297` | 1.13.2 Forge setup |
| `1.14.x` | `aa46e72f60a9b306d5ee6e3b5b1940d84e7b2fdd` | 1.14.4 Forge/Fabric setup |
| `1.15.x` | `6c421dcce2d3889b2965fa69ded9dbd46ec416da` | 1.15.2 Forge/Fabric setup |
| `1.16.x` | `6d32a539731c4cf22bb1923083f48dce5f9adb22` | 1.16.5 Forge/Fabric setup |
| `forge/1.12.2` | `39e9fea7c86969bea2507a2f749321f6cd48875b` | Cross-check of 1.12.2 mappings/build |

Adapted files carry source comments and the repository/JARs retain Apache 2.0,
original e4mc MIT notices and third-party notices.

## Required manual promotion gate

For every exact artifact record loader/JDK/OS and verify: client reaches main
menu, creates a world, opens LAN, creates Spacewar presence, host descriptor or
invite appears, a second Steam account joins, chunks/movement work, disconnect
and reconnect work, world close tears down Steam, and physical dedicated-server
launch does not load client classes. Until recorded, status remains build-only.
