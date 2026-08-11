# e4steam compatibility

Client startup and Steam multiplayer are tracked separately. A successful
main-menu launch proves loader compatibility; it does not by itself prove a
two-player Steam session.

Legend: ✅ verified · ⏳ not yet manually verified · — unsupported.

## 0.3.0 development verification

The 0.3.0 Draft PR currently has automated source/unit coverage only. It has
not completed a manual two-client Minecraft smoke matrix and is not a release.

| Area | Windows | Linux | Manual Steam host/guest |
| --- | --- | --- | --- |
| Java 8 API/testkit/example compile and tests | ✅ local Windows, including an actual Java 8 compiler | CI pending | — |
| Core and API unit tests | ✅ 180 reported: 172 executed, 8 assumption skips, 0 failures/errors | CI pending | — |
| Native cache security suite | ✅ core checks; symlink/hardlink and live-Steam cases partly skipped locally | CI pending | — |
| Existing six modern runtime artifacts | ✅ local clean build and content audit | CI pending | ⏳ |

The Windows filesystem/account used for this run could not create symbolic
links, did not expose Unix hard-link count, and had no live Steam binding in
the test process. Those four assumptions were skipped once in modern tests and
once in the duplicated Java 16 legacy suite. Linux CI is expected to exercise
the filesystem-specific cases; a real two-client Steam smoke is still manual.

macOS client runtime, dedicated server mode and Minecraft 1.6.4–1.16.5 retro
artifacts remain unimplemented in this foundation and therefore unsupported.

## 0.2.1 control run

On 2026-08-03, the final 0.2.1 sources were checked on one representative
version per loader. Each client entered a single-player world, opened it to
LAN, initialized Steam App ID 480, and created an e4steam connection.

| Loader | Minecraft | World | LAN and Steam connection |
| --- | --- | --- | --- |
| Fabric | 26.2 | ✅ | ✅ |
| Quilt | 1.20.2 | ✅ | ✅ |
| Forge | 1.20.2 | ✅ | ✅ |
| NeoForge | 1.21.1 | ✅ | ✅ |

## Windows client launch matrix

On 2026-08-01, 99 clean Windows x64 test instances reached Minecraft's main
menu with e4steam 0.2.0 installed. Fabric and Quilt instances included the
matching Fabric API.

| Loader | Minecraft versions launched | Result |
| --- | --- | --- |
| Fabric | 1.17–1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 | 33/33 ✅ |
| Quilt | 1.17–1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 | 33/33 ✅ |
| Forge | 1.17.1–1.20.2 | 12/12 ✅ |
| NeoForge | 1.20.2–1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 | 21/21 ✅ |

The machine-readable local results are generated in
`build/client-compatibility.json`. Minecraft 26.x uses the modern
Fabric/Quilt artifact.

## Offline launcher profiles

Steam guest connections support Minecraft offline-mode launcher profiles. The
Mojang session check is skipped only for the exact loopback socket belonging
to an authenticated and authorized Steam bridge. Ordinary LAN sockets keep
Minecraft's normal authentication behavior. In 0.3.0 development builds,
e4steam replaces only that authenticated bridge guest's offline profile with a
stable versioned UUID and safe name derived from SteamID. Ordinary LAN and
online-authenticated sockets remain unchanged. Steam must remain online and
signed in.

## Planned retro policy (not built by this Draft PR)

| Minecraft | Permitted retro loader target | Current status |
| --- | --- | --- |
| 1.6.4–1.16.5 | Forge | Not implemented / unsupported |
| 1.14.x only | Separate Fabric artifact | Not implemented / unsupported |
| 1.15–1.16.5 Fabric | None | Intentionally unsupported |
| Legacy Fabric / Ornithe / Rift / retro Quilt | None | Intentionally unsupported |

## Windows host/guest multiplayer matrix

The maintainer manually reconfirmed the supported multiplayer flow on
2026-08-02: open a single-player world, create the Steam connection, invite a
second Steam account, join as a guest, exchange Minecraft TCP traffic, and use
UDP voice-mod traffic. These checks are manual and are not currently executed
by GitHub Actions.

| Artifact boundary | Loader | Host/guest | Steam invitation | TCP | UDP voice |
| --- | --- | --- | --- | --- | --- | --- |
| 1.17 | Fabric / Quilt | ✅ | ✅ | ✅ | ✅ |
| 1.17.1 | Forge | ✅ | ✅ | ✅ | ✅ |
| 1.18.2 | Fabric / Quilt / Forge | ✅ | ✅ | ✅ | ✅ |
| 1.20.2 | Fabric / Quilt / Forge / NeoForge | ✅ | ✅ | ✅ | ✅ |
| 1.21.11 | Fabric / Quilt / NeoForge | ✅ | ✅ | ✅ | ✅ |
| 26.2 | Fabric / Quilt / NeoForge | ✅ | ✅ | ✅ | ✅ |

This table records the principal artifact boundaries, not every intermediate
loader build. The full 99-entry client matrix remains the broader loader-start
coverage.

## Platform status

| Platform | Status |
| --- | --- |
| Windows x64 | ✅ Primary platform; client launch and manual multiplayer verified |
| Linux x64 | Experimental; CI compiles and tests, multiplayer not manually verified |
| macOS | — Unsupported |
| 32-bit operating systems | — Unsupported |

Dedicated servers are unsupported.

The same loader/version JAR is used on Windows x64 and Linux x64. All six
release artifacts bundle native libraries for both operating systems; Linux
remains experimental because its multiplayer path has not been manually
verified yet.
