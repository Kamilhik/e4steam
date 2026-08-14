# Release checklist

e4steam 0.2.4 is the current stable release. The 0.3.0 branch is unreleased and
must not be tagged or published while its Draft PR and manual matrices remain
open. App ID 480 is permanent for this project.

## Required order

1. Finish the English and Russian `CHANGELOG.md` section before creating a tag.
2. Record exact automated and manual evidence in `COMPATIBILITY.md`; never turn
   a compile/main-menu result into a multiplayer or platform support claim.
3. Run the modern, Java 8 API and retro branch verification commands below.
4. Inspect every intended runtime JAR, its metadata, classfile level, notices,
   natives and SHA-256. Do not publish dev/sources/unstubbed/root artifacts.
5. Wait for Windows, Linux, macOS Intel, macOS arm64 and retro CI jobs to pass.
6. Complete the applicable two-instance Steam matrices using the candidate
   hashes. macOS, dedicated and every retro artifact remain non-releaseable if
   their required smoke evidence is absent.
7. Commit the final docs/results, then create annotated `v<version>` from that
   verified commit. Publishing/merging still requires explicit owner approval.

## Modern runtime artifacts

| Artifact | Loader/version scope | Java |
| --- | --- | --- |
| `e4steam-fabric-quilt-mc1.17-1.18.2-v<version>.jar` | Fabric/Quilt 1.17–1.18.2 | 16 |
| `e4steam-forge-mc1.17.1-1.18.1-v<version>.jar` | Forge 1.17.1–1.18.1 | 16 |
| `e4steam-fabric-quilt-mc1.19-1.21.11-v<version>.jar` | Fabric/Quilt 1.19–1.21.11 | 17+ |
| `e4steam-fabric-quilt-mc26.1-26.2-v<version>.jar` | Fabric/Quilt 26.1–26.2 | target metadata |
| `e4steam-forge-mc1.18.2-1.20.2-v<version>.jar` | Forge 1.18.2–1.20.2 | 17 |
| `e4steam-neoforge-mc1.20.2-26.2-v<version>.jar` | NeoForge 1.20.2–26.2 | 17+ |

The same JAR contains pinned Windows x64, Linux x64 and universal macOS
x86_64/arm64 Steamworks client/GameServer libraries. Do not create OS-specific
duplicates. Fabric/Quilt still requires the matching Fabric API.

The six JARs contain headless entrypoints, but dedicated server publication is
allowed only after its loader/OS GameServer matrix passes. Listing metadata
must distinguish client support from experimental dedicated support rather
than making a blanket server claim.

## Retro candidates

`./gradlew -p retro auditRetroArtifacts` builds 14 Java 8 candidates: exact
Forge 1.6.4, Forge branch JARs for 1.7.x through 1.16.x, and Fabric branch JARs
for 1.14.x through 1.16.x. Every filename must keep its loader and public
Minecraft branch. There is no single all-retro, Legacy Fabric, Ornithe, Rift or
retro Quilt file.

The root `releaseJars` task invokes that retro audit and copies all 14 branch
files into `release/<version>` beside the six modern candidates. The resulting
directory must contain exactly 20 runtime JARs; stale or unexpected JARs fail
the assembly check. The direct retro command below remains useful as a focused
verification command.

Retro candidates are build-only until every branch artifact's baseline completes launch,
LAN host, Steam join, movement/chunk, disconnect/reconnect, teardown and
physical-server classloading checks. Do not upload only because the build is
green.

## Verification commands

```powershell
.\gradlew.bat --no-daemon clean apiChecks test headlessEntrypointAudit releaseJars
.\gradlew.bat --no-daemon -p retro clean auditRetroArtifacts
git diff --check
```

Also run the bundled Minecraft-mod JAR inspector, review the dependency/license
inventory in `docs/DEPENDENCY_LICENSE_REPORT.md` and scan intended artifacts
for credentials/private certificates.
Record output and any assumptions/skips. CI does not publish on pull requests.

## Manual candidate checks

- modern integrated: client launch, world open, friends/invite and address join,
  TCP, configured UDP, addon required/optional negotiation, disconnect/reconnect;
- macOS: repeat on native Intel and native arm64 JVMs (Rosetta is a separate
  fallback result, not arm64 proof);
- dedicated: anonymous GameServer startup, readiness, descriptor, two clients,
  stable identity, bans/whitelist, direct TCP rejection, replay/stale session,
  reconnect, capacity and graceful drain on each claimed OS/loader;
- retro: the per-branch-artifact baseline checks described above.

Record Minecraft, loader, Java, OS/arch, Steam accounts/roles without exposing
their IDs, artifact SHA-256, date and outcome. Never publish auth tickets,
tokens, GSLT, join secrets or unredacted logs.

## Listing and legal disclosures

State that e4steam is an unofficial e4mc derivative, both integrated-world
players require the mod and signed-in Steam, traffic uses Steam P2P/Valve
relays, App ID 480 is a shared test namespace, and Steamworks redistributables
are bundled under their own terms. Identify Kamilchik as author/maintainer and
preserve `LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md` and e4mc-retro attribution.

Public Worlds, automatic modpack installation, Offline Skins and World Settings
UI must never be advertised as core features merely because API contracts exist.
