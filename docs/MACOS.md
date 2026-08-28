# macOS

Status for the 0.3.0 release: native loading and artifact support are implemented
and CI-tested on Intel and Apple Silicon runners. Real Steam host/join/reconnect
has not been performed, so both macOS variants remain **experimental**.

## Implemented platform matrix

| JVM/OS | Native selection | Automated status | Steam smoke |
| --- | --- | --- | --- |
| macOS x86_64 (Intel) | x86_64 slice of universal dylibs | Unit/build/native audit | Not verified |
| macOS arm64 (Apple Silicon) | arm64 slice of universal dylibs | Unit/build/native audit | Not verified |
| Rosetta x86_64 JVM | x86_64 slice | Build path only | Not verified |

`NativePlatform` normalizes `Mac OS X`, `macOS`, `OS X` and `Darwin`, plus
`amd64`/`x86_64` and `arm64`/`aarch64`. The active JVM architecture must exist
in every Mach-O library. Rosetta is only an optional fallback and is never
reported as native arm64 support.

Runtime JARs bundle pinned universal `libsteam_api.dylib`,
`libsteamworks4j.dylib` and `libsteamworks4j-server.dylib` from the declared
Steamworks4j dependencies. Exact size and SHA-256 are checked at build time and
again before extraction/load. CI runs `lipo`, `otool -L` and `codesign --verify`
diagnostics on both macOS runner architectures and rejects developer-local
dynamic dependency paths. Unsigned Valve/Steamworks redistributables are
reported honestly; e4steam does not claim to sign them.

The owner-controlled native cache uses no-follow type/owner/link checks,
bounded reads, content hashes, atomic publication and process locks. Only a
verified absolute e4steam cache path reaches `System.load`; PATH and working
directory fallbacks are forbidden.

## Installation and diagnostics

Use a 64-bit JVM matching the desired architecture, install the normal
loader/version JAR, start and sign in to the macOS Steam client, then launch
Minecraft normally. Do not add the launcher as a non-Steam game.

When Steam reports an injected overlay, e4steam can use it normally. Otherwise
the invitation button opens the standalone Steam friends window through the
fixed `steam://open/friends` URI; lobby rich presence remains available for
**Join Game**. Intel users may opt into the pre-LWJGL relaunch described in the
[Unix overlay guide](UNIX_OVERLAY.md). The relaunch uses Valve's installed
`gameoverlayrenderer.dylib`; Prism/MultiMC additionally require the supplied
Java 8 stdin agent. It is unavailable to a native Apple Silicon JVM because
Valve's macOS overlay renderer is currently x86_64.

e4steam never disables Gatekeeper, removes quarantine, asks for `sudo` or
changes system security policy. If macOS blocks Minecraft or a native library,
inspect the publisher/quarantine state yourself and install only from a trusted
source. Diagnostics redact user paths and never include passwords, tickets,
tokens, SteamID or join secrets by default.

Before a support claim, record macOS/JDK/loader/Minecraft versions and complete
host, join, invite, relay, disconnect and reconnect tests separately on a
native Intel JVM and native arm64 JVM.
