# Modern release verification — 2026-11-27

Core 0.3.8 / Premium 0.1.2. The release JAR checksums are pinned in SHA256SUMS.txt.

## Real Minecraft checks (Windows x86_64)

| Minecraft | Loader | Client | First-attempt personal-address join | UUID equals launcher account | Rejoined without restarting |
| --- | --- | --- | --- | --- | --- |
| 1.20.1 | forge | premium | passed | passed | passed |
| 1.19.2 | fabric | premium | passed | passed | passed |
| 1.19.2 | fabric | free | passed | passed | not repeated in this case |
| 26.2 | fabric | premium | passed | passed | passed |
| 26.2 | fabric | free | passed | passed | not repeated in this case |
| 1.17.1 | forge | premium | passed | passed | passed |
| 1.17.1 | forge | free | passed | passed | not repeated in this case |
| 1.18.2 | forge | premium | passed | passed | passed |
| 1.18.2 | forge | free | passed | passed | not repeated in this case |
| 1.21.11 | fabric | premium | passed | passed | passed |
| 26.1 | neoforge | premium | passed | passed | passed |
| 26.1 | neoforge | free | passed | passed | not repeated in this case |
| 26.3 | fabric | premium | passed | passed | passed |
| 1.17.1 | fabric | premium | passed | passed | passed |
| 1.17.1 | fabric | free | passed | passed | not repeated in this case |

Each recorded client exited normally after entering the real dedicated-server world. All nine test servers were stopped normally. Hosts used Premium 0.1.2 with anonymous Steam GameServer personal names; both free and Premium clients were exercised. Free server entrypoints were audited, but free hosts were not launched separately. The six client artifact families cover both editions.

## Automated checks

- Unit checks: 1106 executed cases passed; 33 reported skipped. No failures or errors.
- API, headless-entrypoint and dependency-license audits passed.
- All twelve JARs passed version, Java bytecode, login API/linkage, no-secret and no-usage-statistics audits.
- Bundled Linux x86_64 libraries passed checksum/XZ extraction checks and a native shared-library loading smoke test on Linux.

Fabric 1.19.2 additionally passed three separate cold client/server starts and three same-process rejoins using the exact final Premium JAR. Deterministic unit coverage reproduces the off-loop read-mode interleaving and verifies that serializing it preserves read interest. The final game checks also cover the new encryption-state adapter on 26.2 and 26.3.

The real Forge 1.20.1 regression case also confirmed a backup of the player's owned offline save, an unchanged original file, Mojang authentication and a UUID matching the launcher account.

## Limits

- Runtime servers and clients ran on one Windows PC. WAN/relay routing and a second independent Steam user were not exercised by these runs.
- This is sampled runtime coverage, not every Minecraft patch or loader build. Quilt, macOS and Linux Minecraft gameplay were not launched in this matrix.
- Voice audio between two PCs and world handoff between separate Steam users were not repeated in this release matrix. Earlier user tests covered those flows; this report does not extend that evidence to every version.
- Regression coverage includes repeated dedicated-ingress identity reads, revocation before socket reuse, stale lease/generation rejection and owned offline-save migration with backups. The earlier first-login rejection was traced to a destructive identity lookup; the final runtime matrix requires every cold join to succeed on its first attempt.
