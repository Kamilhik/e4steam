# e4steam 0.3.6 verification

Release scope: six free runtime JARs, Minecraft 1.17–26.3. No retro artifacts,
Premium activation code, signing keys or customer/bot data are distributed.

- `apiChecks :common:test headlessEntrypointAudit` passed on Windows x64 with
  Corretto JDK 21. Core: 266 tests, zero failures, nine conditional skips.
- All six candidates passed ZIP/CRC, duplicate-entry, classfile-level, version,
  named-address class, support UI and credential/private-entry inspections.
- SHA-256 values are pinned in `tools/core-0.3.6.json` and the release checksums.
- The owner confirmed the free Fabric 1.21.1 Support window, blur fix and Boosty
  browser action during preparation. Premium activation/thank-you tests are
  separate and are not presented as free-client multiplayer tests.
- A complete new two-account, every-patch multiplayer matrix is not claimed.
  Linux/macOS remain experimental. Older dedicated-server/integrated-world
  checks do not establish a new complete matrix for these final hashes.

Published 0.3.4 was the previous official GitHub release; the local 0.3.5
connection and dedicated-setup changes are included in 0.3.6 and called out
separately in the release notes.
