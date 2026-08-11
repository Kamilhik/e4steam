# Retro porting policy

Retro support is not implemented by the current 0.3.0 foundation. The fixed
target policy for later isolated toolchains is:

| Minecraft | Loader | Status |
| --- | --- | --- |
| 1.6.4, 1.7.10, 1.8.9, 1.9.4, 1.10.2, 1.11.2, 1.12.2, 1.13.2, 1.14.4, 1.15.2, 1.16.5 | Forge | Planned, unsupported |
| 1.14.x / 1.14.4 | Separate Fabric target | Planned, unsupported |
| 1.15-1.16.5 Fabric | None | Intentionally unsupported |
| Legacy Fabric, Ornithe, Rift, retro Quilt | None | Intentionally unsupported |

No generic retro JAR is allowed. Every artifact must identify exact Minecraft
and loader ranges, Java/toolchain family, build-only/experimental/supported
status and manual smoke evidence.

## Reference audit

Reference audit only: no e4mc-retro source or binary was copied by this Draft
PR. The inspected upstream revisions were:

| Branch | Commit SHA | Upstream baseline | Forge | Build/mappings notes |
| --- | --- | --- | --- | --- |
| `1.6.x` | `906261a35fd18cd8362bd2232c1cf54d7c37f180` | 1.6.4 | 9.11.1.1345 | Java 8, Gradle 8.12, Unimined 1.3.15, Forge builtin MCP |
| `1.7.x` | `00d98a2236c8575c3f521bba249d2175b61456a7` | 1.7.10 | 10.13.4.1614 | Java 8, Gradle 8.12, Unimined 1.4.2-SNAPSHOT, MCP stable 12 |
| `1.8.x` | `0d9894c09e41973dabc5273bf006e4d58107a224` | 1.8.9 | 11.15.1.2318 | Java 8, Gradle 8.12, Essential Architectury Loom 1.9.31, MCP stable 22 |
| `1.9-1.12.x` | `5802050583a1ea24dceec5b1f1092ab4cb4a8070` | 1.12.2 | 14.23.5.2847 | Java 8, Gradle 8.12, Essential Architectury Loom 1.9.31, MCP stable 39 |
| `1.13.x` | `938c95d909ae5f8ea965cf85b5cf1c86d7fc1297` | 1.13.2 | 25.0.223 | Java 8, Gradle 8.12, Unimined 1.4.2-SNAPSHOT, MCP snapshot 20180921 |
| `1.14.x` | `aa46e72f60a9b306d5ee6e3b5b1940d84e7b2fdd` | 1.14.4 | 28.2.26 | Java 8, Gradle 8.12, Unimined 1.4.2-SNAPSHOT, intermediary + Mojmap; Fabric Loader 0.16.14 reference |
| `1.15.x` | `6c421dcce2d3889b2965fa69ded9dbd46ec416da` | 1.15.2 | 31.2.57 | Java 8, Gradle 8.12, Unimined 1.4.2-SNAPSHOT, intermediary + Mojmap |
| `1.16.x` | `6d32a539731c4cf22bb1923083f48dce5f9adb22` | 1.16.5 | 36.2.34 | Java 8, Gradle 8.12, Unimined 1.4.2-SNAPSHOT, intermediary + Mojmap |
| `forge/1.12.2` | `39e9fea7c86969bea2507a2f749321f6cd48875b` | 1.12.2 | 14.23.5.2847 | Java 8, Gradle 8.8, Essential Architectury Loom, MCP stable 39 |

These references use modern compatibility plugins around old game/Forge
versions rather than historical ForgeGradle generations. Their relevant seams
include integrated/server connection listeners, player-list and command
mixins, address parsing and native-loader adapters; exact mixin signatures must
be revalidated independently for every e4steam target. The upstream Fabric,
Quilt, Ornithe and Rift variants were examined only for comparison. The fixed
e4steam deliverable remains Forge for 1.6.4-1.16.5 plus a separate Fabric
1.14.x artifact.

A later port must preserve the upstream repository's Apache-2.0 license and
the MIT notices for inherited original e4mc material as applicable to each
copied file. It must not copy the Cloudflare/Quiclime/cloudflared tunnel
backend. e4steam continues to use Steam App ID 480 and its own Steam
transport/security model.
