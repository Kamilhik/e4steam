# Retro porting and compatibility

[Русская версия](RETRO_PORTING_RU.md)

The 0.3.1 release contains isolated Java 8 toolchains and one supported JAR per
retro Minecraft branch. Each branch artifact is compiled against the stable
baseline listed below. The compatibility matrix records exact manual evidence
separately from release support.

## For players

Retro builds are the e4steam files for Minecraft 1.7.x-1.16.x. Pick the JAR
whose filename contains both your loader and your Minecraft branch. Do not use
a modern `mc1.17+` JAR and do not install several e4steam JARs together.

Basic installation:

1. install the supported Forge or Fabric loader for the chosen Minecraft
   version;
2. place exactly one matching e4steam JAR in the instance's `mods` folder;
3. for Fabric 1.14.x-1.16.x, also install a compatible Fabric API;
4. for Forge 1.7.x, also install UniMixins 0.1.20 or newer;
5. start Steam, sign in, then launch Minecraft normally.

The `.x` in a filename means one minor Minecraft branch, not every old
Minecraft release. For example, `mc1.12.x` targets the 1.12 branch and is built
against 1.12.2; it is not a universal 1.7-1.16 file.

The first version to test is the build baseline in the table below. Other
patches in the same `.x` branch are supported release targets, but their exact
manual results are recorded separately in `COMPATIBILITY.md`.

## Branch artifact matrix

| Public branch | Build baseline | Loader | Branch artifact | Status |
| --- | --- | --- | --- | --- |
| 1.7.x | 1.7.10 | Forge 10.13.4.1614 | `e4steam-forge-mc1.7.x-v0.3.1.jar` | Supported release |
| 1.8.x | 1.8.9 | Forge 11.15.1.2318 | `e4steam-forge-mc1.8.x-v0.3.1.jar` | Supported release |
| 1.9.x | 1.9.4 | Forge 12.17.0.2317 | `e4steam-forge-mc1.9.x-v0.3.1.jar` | Supported release |
| 1.10.x | 1.10.2 | Forge 12.18.3.2511 | `e4steam-forge-mc1.10.x-v0.3.1.jar` | Supported release |
| 1.11.x | 1.11.2 | Forge 13.20.1.2588 | `e4steam-forge-mc1.11.x-v0.3.1.jar` | Supported release |
| 1.12.x | 1.12.2 | Forge 14.23.5.2864 | `e4steam-forge-mc1.12.x-v0.3.1.jar` | Supported release |
| 1.13.x | 1.13.2 | Forge 25.0.223 | `e4steam-forge-mc1.13.x-v0.3.1.jar` | Supported release |
| 1.14.x | 1.14.4 | Forge 28.2.30 | `e4steam-forge-mc1.14.x-v0.3.1.jar` | Supported release |
| 1.15.x | 1.15.2 | Forge 31.2.62 | `e4steam-forge-mc1.15.x-v0.3.1.jar` | Supported release |
| 1.16.x | 1.16.5 | Forge 36.2.42 | `e4steam-forge-mc1.16.x-v0.3.1.jar` | Supported release |
| 1.14.x | 1.14.4 | Fabric Loader 0.16.14 | `e4steam-fabric-mc1.14.x-v0.3.1.jar` | Supported release |
| 1.15.x | 1.15.2 | Fabric Loader 0.16.14 | `e4steam-fabric-mc1.15.x-v0.3.1.jar` | Supported release |
| 1.16.x | 1.16.5 | Fabric Loader 0.16.14 | `e4steam-fabric-mc1.16.x-v0.3.1.jar` | Supported release |

Minecraft 1.6.4 source remains available only as a reference port and is not
part of the 0.3.1 build, test or release matrix.

The three regular Fabric artifacts cover Minecraft `1.14.x`-`1.16.x`.
Minecraft 1.7.10-1.13.2 would require distinct **Legacy Fabric** or
**Ornithe** ports rather than regular Fabric/Quilt; Minecraft 1.13.2 may also
use a distinct **Rift** port. Those loaders are not part of the current release
matrix. There is no retro Quilt or single all-retro JAR.
The `.x` range belongs only to the minor branch named by that artifact.

## Architecture

`retro/core` reuses the current e4steam Steam Networking Messages transport,
App ID 480 lifecycle, retry/native-cache fixes and compact address model while
remaining Java 8-compatible. Branch modules provide only version-specific
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

## What differs from modern builds

- Retro artifacts run on Java 8 and use loader-specific hooks for old LAN
  screens and server lifecycles.
- Steam transport, compact addresses, App ID 480, retry behavior and native
  cache checks follow the same rules as modern e4steam.
- Forge 1.7.x keeps UniMixins external. Bundling another copy would create
  duplicate mod IDs in many existing modpacks.
- The optional Linux/macOS overlay relaunch is disabled by default and is not
  required for networking. On macOS Forge 1.7.x-1.12.x it is skipped to avoid
  hidden windows and restart loops.
- Each retro JAR contains a physical dedicated-server entrypoint. The normal
  `e4steam-dedicated.toml` rules still apply.

## Common problems

### Duplicate `unimixins` or `mixinbooterlegacy` mod IDs

The pack contains more than one UniMixins distribution. Keep one compatible
external UniMixins 0.1.20+ JAR for Forge 1.7.x and remove duplicate copies.
Do not delete unrelated libraries blindly; compare the filenames shown by FML.

### `Invalid session` when joining

All participants must use compatible e4steam builds. The host should reopen
the world and send the new address. Join through e4steam rather than the raw LAN
port. Offline Minecraft profiles are accepted only after the Steam connection
has been authenticated.

### The LAN screen has no e4steam control

Check that the JAR matches both the loader and branch and that only one e4steam
copy is installed. Some old versions show the address and invite actions in
chat after **Start LAN World** instead of placing every control on the screen.

### Spacewar starts but no address appears

Wait until Minecraft reports the local LAN port. Then try `/e4steam start` on
versions that register the command. If the command is not available on that
legacy command system, close and reopen the world and inspect the log for the
first e4steam error.

### The game restarts, hangs or opens no window on Linux/macOS

Turn off the optional overlay relaunch. Networking works without it. See
[`UNIX_OVERLAY.md`](UNIX_OVERLAY.md).

### A large modpack crashes before the menu

Start once with e4steam and its required loader dependencies only. If that
works, add the pack back in groups and look for duplicate coremods,
transformers or native libraries. Report the full first `Caused by` section
rather than only the final `InvocationTargetException` line.

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

## Manual verification checklist

For every branch artifact, test the listed baseline first and record the exact
loader, JDK and operating system. Check main-menu launch, world creation, LAN
opening, Spacewar presence, address/invite display, a second Steam account,
chunks and movement, disconnect/reconnect, Steam teardown when the world closes
and a physical server launch without client-class loading. The `.x` branch is
a supported release range; extra patch results make the evidence matrix more
useful.

For a player report, include the exact Minecraft patch, loader build, Java 8
vendor/version, operating system, complete e4steam filename, installed coremods
and the stage that failed: before the menu, while opening LAN or while joining.
Remove live addresses and account data before sharing a log.
