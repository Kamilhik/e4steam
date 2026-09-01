# e4steam compatibility

Use this file to answer two different questions:

1. **Is there a release JAR for my Minecraft version and loader?** Read the
   modern or retro artifact table.
2. **Has that exact combination been run with real Steam clients?** Read the
   manual-evidence columns and dates.

A build can be valid even when nobody has completed a manual multiplayer test
on that exact patch and operating system. For this reason, build success,
client launch, integrated-world multiplayer, native loading and dedicated
GameServer operation are recorded separately. One green result never implies
all the others.

If you only want to install the mod, begin with
[Getting started](docs/GETTING_STARTED.md) or
[«Начало работы»](docs/GETTING_STARTED_RU.md).

## Quick answer

- Windows x64 is the primary platform for clients, opened worlds and dedicated
  servers.
- Linux x64 and macOS artifacts are available, but their full manual matrices
  are not complete and remain experimental.
- 32-bit systems are not supported.
- Modern releases cover Fabric/Quilt, Forge and NeoForge with separate JARs.
- Retro releases cover Forge `1.7.x-1.16.x` and regular Fabric
  `1.14.x-1.16.x`.
- Fabric and Quilt need Fabric API. Forge `1.7.x` also needs an external
  UniMixins `0.1.20` or newer JAR.

## What the symbols mean

Legend: ✅ manually verified · 🧪 automatically tested or audited · 🧱 branch
JAR built and audited · ⏳ not manually verified yet · — unsupported.

In plain language:

| Symbol | Meaning |
| --- | --- |
| ✅ | A maintainer completed the stated real-game scenario. Read the row for the exact scope. |
| 🧪 | An automated test or package audit passed. Minecraft multiplayer may still be untested. |
| 🧱 | A release JAR was built and inspected for that branch. This is not a launch or join result. |
| ⏳ | The artifact or code exists, but the stated manual scenario has not been recorded yet. |
| — | e4steam does not provide that combination. |

## 0.3.1 automated platform status

| Area | Windows x64 | Linux x64 | macOS Intel | macOS arm64 |
| --- | --- | --- | --- | --- |
| Java 8 API/testkit/example | 🧪 | 🧪 | 🧪 | 🧪 |
| Modern core/unit/headless graph | 🧪 | 🧪 | 🧪 | 🧪 |
| Six modern runtime JAR audit | 🧪 | 🧪 | 🧪 | 🧪 |
| Native names/hash/header selection | 🧪 | 🧪 | 🧪 + Mach-O audit | 🧪 + Mach-O audit |
| Integrated two-client Steam regression | ⏳ | ⏳ | ⏳ | ⏳ |
| Dedicated GameServer/two clients | ⏳ | ⏳ | ⏳ | ⏳ |

Dedicated servers are supported on Windows x64 after representative modern and
retro authenticated joins. Linux and macOS remain experimental under the
existing release policy. No 32-bit target is supported.

## Modern client launch evidence

The broad 99-instance launch pass was recorded on 2026-08-01 with e4steam
0.2.0. It proves only that those loader profiles reached the main menu on
Windows x64. It is retained as historical regression evidence, not presented
as a 0.3.1 multiplayer pass.

| Loader | Minecraft versions launched | Historical result |
| --- | --- | --- |
| Fabric | 1.17–1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 | 33/33 ✅ on 0.2.0 |
| Quilt | 1.17–1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 | 33/33 ✅ on 0.2.0 |
| Forge | 1.17.1–1.20.2 | 12/12 ✅ on 0.2.0 |
| NeoForge | 1.20.2–1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 | 21/21 ✅ on 0.2.0 |

Targeted 0.3.1 client smoke checks on 2026-08-29: the corrected
`e4steam-forge-mc1.17.1-1.18.1` JAR reached the main menu on Forge 37.1.1 /
Minecraft 1.17.1 / Java 16 and Forge 39.1.0 / Minecraft 1.18.1 / Java 17,
both on Windows x64. This verifies client startup, not Steam multiplayer.

## Integrated host/guest evidence from 0.2.x

The maintainer manually reconfirmed the 0.2.x integrated-world flow on
2026-08-02. These results remain regression targets, not a 0.3.1 pass.

| Artifact boundary | Loader | Host/guest | Invite | TCP | UDP voice |
| --- | --- | --- | --- | --- | --- | --- |
| 1.17 | Fabric / Quilt | ✅ | ✅ | ✅ | ✅ |
| 1.17.1 | Forge | ✅ | ✅ | ✅ | ✅ |
| 1.18.2 | Fabric / Quilt / Forge | ✅ | ✅ | ✅ | ✅ |
| 1.20.2 | Fabric / Quilt / Forge / NeoForge | ✅ | ✅ | ✅ | ✅ |
| 1.21.11 | Fabric / Quilt / NeoForge | ✅ | ✅ | ✅ | ✅ |
| 26.2 | Fabric / Quilt / NeoForge | ✅ | ✅ | ✅ | ✅ |

Offline launcher profiles remain supported only for an already authenticated
Steam bridge. Ordinary LAN/TCP keeps normal Minecraft authentication behavior.
In 0.3.0 the guest UUID and safe name derive from authenticated Steam identity,
so persona-name changes do not alter ownership/bans.

## 0.3.1 retro branch matrix

Every entry below is a supported 0.3.1 release artifact with an isolated Java 8
build and branch JAR audit. The version in parentheses is the representative
build/test baseline. Client/LAN and two-client Steam evidence are recorded
separately and do not change the artifact's release status.

| Minecraft | Forge | Fabric | Windows client + LAN host | Steam host/guest | Linux/macOS |
| --- | --- | --- | --- | --- | --- |
| 1.7.x (1.7.10) | 🧱 | — | ✅ 2026-08-20 | ⏳ | ⏳ |
| 1.8.x (1.8.9) | 🧱 | — | ⏳ | ⏳ | ⏳ |
| 1.9.x (1.9.4) | 🧱 | — | ⏳ | ⏳ | ⏳ |
| 1.10.x (1.10.2) | 🧱 | — | ⏳ | ⏳ | ⏳ |
| 1.11.x (1.11.2) | 🧱 | — | ⏳ | ⏳ | ⏳ |
| 1.12.x (1.12.2) | 🧱 | — | ⏳ | ⏳ | ⏳ |
| 1.13.x (1.13.2) | 🧱 | — | ⏳ | ⏳ | ⏳ |
| 1.14.x (1.14.4) | 🧱 | 🧱 | ⏳ | ⏳ | ⏳ |
| 1.15.x (1.15.2) | 🧱 | 🧱 | ⏳ | ⏳ | ⏳ |
| 1.16.x (1.16.5) | 🧱 | 🧱 | ⏳ | ⏳ | ⏳ |

Regular Fabric/Quilt must not be used as the loader name for Minecraft
1.7.10-1.13.2. Separate Legacy Fabric or Ornithe ports are possible for those
versions, and a separate Rift port is possible for 1.13.2, but none of those
artifacts is built or verified in the current matrix. Retro Quilt remains
unsupported.
Retro dedicated GameServer paths are implemented, built and server-side
artifact-audited. Forge 1.12.2 has a real Windows x64 authenticated join; the
other branch baselines remain not yet manually verified.

## 0.3.1 dedicated matrix

| Loader family | Headless entry/class graph | GameServer startup | Authenticated client join |
| --- | --- | --- | --- |
| Fabric/Quilt 1.17+ | 🧪 | ✅ Fabric 26.2, Windows x64, 2026-08-28 | ✅ one authenticated client |
| Forge 1.17.1+ | 🧪 | ⏳ | ⏳ |
| NeoForge 1.20.2+ | 🧪 | ✅ NeoForge 1.21.1, Windows x64, 2026-08-28 | ✅ one authenticated client |
| Retro branch artifacts | 🧪 physical-side, listener/login mixin and JAR audit | ✅ Forge 1.12.2, Windows x64, 2026-08-28 | ✅ one authenticated client |

The recorded checks prove startup and one authenticated client join on three
representative loader generations. They do not prove the two-client or
cross-platform matrix, nor every patch inside each declared branch range.

The following hashes identify the candidates used for or produced around the
recorded Windows checks. They are evidence identifiers, not hashes for every
later rebuild of the 0.3.1 worktree:

| Minecraft / loader | Java | Release JAR SHA-256 |
| --- | --- | --- |
| Forge 1.17.1–1.18.1 (client launch) | 16 / 17 | `375D5C434D21B085BAE7E81EE1F63942DD34A5A07CC9ACD37106EBE722CE2D66` |
| Forge 1.12.2 | 8 | `64BC1F65105141D0E0A0400C4B6F1F3589CCCA24ADE3CF47CC06E447BF5D8D3C` |
| NeoForge 1.21.1 | 21 | `101545A0ACCF47115CA55CF2C6F398E118F95204D9421103E85E587971662445` |
| Fabric 26.2 | 25 | `5B732F9937550C7F606C264FF33E33FE55D32D514E581A381E31EEBF61770C71` |

Before marking another combination as verified, record the exact artifact SHA-256,
Minecraft, loader, Java, OS/arch, host/join/invite/reconnect, direct-TCP rejection and
shutdown results. A green compile or main menu is not that evidence.
