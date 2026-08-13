# e4steam compatibility

Compilation, client launch, integrated-world multiplayer, macOS native loading
and dedicated GameServer operation are separate claims.

Legend: ✅ manually verified · 🧪 automatically tested/audited · 🧱 build-only
· ⏳ not yet manually verified · — unsupported.

## 0.3.0 automated platform status

| Area | Windows x64 | Linux x64 | macOS Intel | macOS arm64 |
| --- | --- | --- | --- | --- |
| Java 8 API/testkit/example | 🧪 | 🧪 | 🧪 | 🧪 |
| Modern core/unit/headless graph | 🧪 | 🧪 | 🧪 | 🧪 |
| Six modern runtime JAR audit | 🧪 | 🧪 | 🧪 | 🧪 |
| Native names/hash/header selection | 🧪 | 🧪 | 🧪 + Mach-O audit | 🧪 + Mach-O audit |
| Integrated two-client Steam regression | ⏳ | ⏳ | ⏳ | ⏳ |
| Dedicated GameServer/two clients | ⏳ | ⏳ | ⏳ | ⏳ |

macOS and dedicated therefore remain experimental. Linux remains experimental
under the existing release policy. No 32-bit target is supported.

## Modern client launch evidence

The most recent broad manual launch record predates 0.3.0: on 2026-08-01, 99
Windows x64 instances with e4steam 0.2.0 reached Minecraft's main menu. This is
loader-start evidence, not proof that 0.3.0 or multiplayer has been smoke-tested.

| Loader | Minecraft versions launched | Historical result |
| --- | --- | --- |
| Fabric | 1.17–1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 | 33/33 ✅ on 0.2.0 |
| Quilt | 1.17–1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 | 33/33 ✅ on 0.2.0 |
| Forge | 1.17.1–1.20.2 | 12/12 ✅ on 0.2.0 |
| NeoForge | 1.20.2–1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 | 21/21 ✅ on 0.2.0 |

## Historical integrated host/guest evidence

The maintainer manually reconfirmed the 0.2.x integrated-world flow on
2026-08-02. These results remain regression targets, not a 0.3.0 pass.

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

## 0.3.0 exact retro matrix

Every entry below has an isolated Java 8 build and exact JAR audit, but no
Minecraft launch or Steam join has been recorded.

| Minecraft | Forge | Fabric | Windows/Linux/macOS runtime |
| --- | --- | --- | --- |
| 1.6.4 | 🧱 | — | ⏳ |
| 1.7.10 | 🧱 | — | ⏳ |
| 1.8.9 | 🧱 | — | ⏳ |
| 1.9.4 | 🧱 | — | ⏳ |
| 1.10.2 | 🧱 | — | ⏳ |
| 1.11.2 | 🧱 | — | ⏳ |
| 1.12.2 | 🧱 | — | ⏳ |
| 1.13.2 | 🧱 | — | ⏳ |
| 1.14.4 | 🧱 | 🧱 | ⏳ |
| 1.15.2 | 🧱 | 🧱 | ⏳ |
| 1.16.5 | 🧱 | 🧱 | ⏳ |

Legacy Fabric, Ornithe, Rift and retro Quilt are intentionally unsupported.
Retro dedicated GameServer behavior is not verified and must not be advertised.

## 0.3.0 dedicated matrix

| Loader family | Headless entry/class graph | GameServer startup | Two clients |
| --- | --- | --- | --- |
| Fabric/Quilt 1.17+ | 🧪 | ⏳ | ⏳ |
| Forge 1.17.1+ | 🧪 | ⏳ | ⏳ |
| NeoForge 1.20.2+ | 🧪 | ⏳ | ⏳ |
| Exact retro artifacts | Physical-side audit for Forge; client mixins separated | ⏳ | ⏳ |

Before any status becomes supported, record exact artifact SHA-256, Minecraft,
loader, Java, OS/arch, host/join/invite/reconnect, direct-TCP rejection and
shutdown results. A green compile or main menu is not that evidence.
