# Release checklist

e4steam 0.3.1 is a full release. Windows x64 clients, integrated worlds and
dedicated servers are supported. The listed retro artifacts are normal release
files, not experimental builds. Linux x64 and macOS remain experimental until
their manual matrices are complete. App ID 480 is permanent for this project.

This document is for maintainers. Building JARs is only the middle of the
release process; publication starts after documentation, artifact audits and
manual evidence refer to the same candidate hashes.

## Release gates

| Gate | Required result |
| --- | --- |
| Version and changelog | English and Russian 0.3.1 entries agree |
| Automated checks | Modern, API, headless and retro checks pass |
| Artifact set | Exactly 19 runtime JARs, no stale or development files |
| Manual evidence | Claimed client/server scenarios recorded against candidate SHA-256 |
| Platform wording | Incomplete Linux/macOS coverage remains marked experimental |
| Legal files | License, notice and third-party records match packaged code |
| Approval | Project owner explicitly authorizes publication |

If a gate fails, fix it or narrow the claim. Do not rename an untested candidate
to “release” to bypass missing evidence.

## Release order

1. Finish both the English and Russian entries in `CHANGELOG.md`.
2. Record automated and manual evidence in `COMPATIBILITY.md`. A successful
   compile or main-menu launch is not a multiplayer test.
3. Run the modern, Addon API and retro checks below.
4. Inspect every candidate JAR: metadata, classfile level, notices, native
   libraries and SHA-256. Exclude development, sources, unstubbed and root
   artifacts.
5. Wait for the Windows, Linux, macOS Intel, macOS arm64 and retro CI jobs.
6. Run the applicable two-instance Steam checks against the exact candidate
   hashes. Keep Linux and macOS combinations marked experimental until their
   results are recorded.
7. Commit the final documentation and results, then create the annotated
   `v<version>` tag from that commit. Publishing still requires the project
   owner's explicit approval.

## Modern runtime JARs

| Artifact | Loader and Minecraft range | Java |
| --- | --- | --- |
| `e4steam-fabric-quilt-mc1.17-1.18.2-v<version>.jar` | Fabric/Quilt 1.17-1.18.2 | 16 |
| `e4steam-forge-mc1.17.1-1.18.1-v<version>.jar` | Forge 1.17.1-1.18.1 | 16/17 |
| `e4steam-fabric-quilt-mc1.19-1.21.11-v<version>.jar` | Fabric/Quilt 1.19-1.21.11 | 17+ |
| `e4steam-fabric-quilt-mc26.1-26.2-v<version>.jar` | Fabric/Quilt 26.1-26.2 | release metadata |
| `e4steam-forge-mc1.18.2-1.20.2-v<version>.jar` | Forge 1.18.2-1.20.2 | 17 |
| `e4steam-neoforge-mc1.20.2-26.2-v<version>.jar` | NeoForge 1.20.2-26.2 | 17+ |

Each JAR contains the pinned 64-bit Steamworks client and GameServer libraries
for Windows, Linux and universal macOS x86_64/arm64. Do not publish separate OS
copies. Fabric and Quilt still need the matching Fabric API.

The modern JARs contain both client and headless entrypoints. Publication
metadata may claim dedicated-server support on Windows x64. Linux and macOS
server claims must follow the manual evidence in `COMPATIBILITY.md`.

## Retro runtime JARs

`./gradlew -p retro auditRetroArtifacts` builds 13 Java 8 candidates: ten Forge
branch JARs for `1.7.x` through `1.16.x`, plus three Fabric branch JARs for
`1.14.x` through `1.16.x`. Every filename must include the loader and public
Minecraft branch. There is no all-retro JAR and no retro Quilt build.

Legacy Fabric and Ornithe for `1.7.10`-`1.13.2`, and Rift for `1.13.2`, are
separate possible ports. They must not be presented as aliases for regular
Fabric or Quilt.

The root `releaseJars` task runs the retro audit and copies all 13 files next
to the six modern JARs in `release/<version>`. The final directory must contain
exactly 19 runtime JARs. Stale or unexpected files fail assembly.

Forge `1.7.x` requires an external UniMixins `0.1.20` or newer JAR. e4steam
must not embed UniMixins component mods because that creates duplicate mod IDs
in existing packs.

## Automated checks

```powershell
.\gradlew.bat --no-daemon clean apiChecks test headlessEntrypointAudit releaseJars
.\gradlew.bat --no-daemon -p retro clean auditRetroArtifacts
git diff --check
```

Also inspect the candidate JARs with the repository's Minecraft-mod inspector,
review `docs/DEPENDENCY_LICENSE_REPORT.md` and scan release files for secrets or
private certificates. Record failed, skipped and assumed checks as clearly as
successful ones. Pull-request CI never publishes artifacts.

If a check fails, keep the first meaningful error and the task name. Re-running
until a flaky result turns green is not evidence unless the flake is understood
and documented.

## Manual candidate checks

- modern integrated world: launch, open world, friends/invite and address join,
  TCP, configured UDP, addon compatibility, disconnect and reconnect;
- macOS: repeat on native Intel and native arm64 JVMs; a Rosetta result is a
  separate fallback result, not proof of native arm64 support;
- dedicated server: anonymous GameServer startup, automatic descriptor, two
  clients, stable identities, access rules, direct TCP rejection, stale/replay
  handling, reconnect, capacity and graceful drain;
- retro: run the baseline for every branch artifact claimed in the release.

Record Minecraft, loader, Java, OS/architecture, participant roles, candidate
SHA-256, date and result. Do not publish Steam IDs, auth tickets, GSLT, join
secrets or raw logs.

The candidate hash matters: rebuilding after a manual test creates a different
artifact and invalidates that test until the new hash is checked again.

## Listing and legal notes

Public descriptions must say that e4steam is an unofficial e4mc derivative,
uses Steam P2P or Valve relays, and shares Valve's test App ID 480. Integrated-
world players need matching e4steam builds and signed-in Steam clients. The
dedicated server itself uses anonymous GameServer login.

Keep `LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md` and e4mc-retro attribution
with every source and binary release. Valve Steamworks redistributables retain
their own terms and are not relicensed under Apache 2.0.

API contracts for Public Worlds, automatic modpack installation, Offline Skins
and World Settings are not proof that those features ship in core. Advertise
them only when a separate released addon actually provides them.
