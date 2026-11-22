# World handoff — Premium 0.1.1 / free 0.3.7

Premium hosts start a handoff from Esc → Save and Quit. A custom exit dialog
provides Pass to a friend, Leave and Back. Pass selects an authenticated Steam
player actually connected to this integrated server, not an arbitrary friend.
The recipient receives a custom confirmation inside their current game; no
separate receiving screen or key is required beforehand. Declining leaves the
source world open. Only acceptance saves and closes the source world.

After transfer the recipient automatically loads the verified imported copy
and publishes it for Steam friends, using a fresh available port. The source
world's LAN game mode, command permission, PvP, online mode and player limit
are carried in the versioned offer. The recipient does not need Premium.
Minecraft's internal save/load and publication APIs remain necessary; there
is no manual vanilla world-selection or Open to LAN workflow for this handoff.

The free edition has no world-transfer buttons in the title screen, pause menu
or LAN settings. It handles receiving confirmation and automatic return only.
When the original owner reconnects to the transferred world through Steam,
the custodian sees return progress and saves/transfers the current copy back.
A sealed local receipt permits only that world's return to that owner.
Automatic acceptance by the owner additionally requires a persisted local
record that this exact world was delegated to this exact custodian. It is
never enabled merely by an incoming packet claiming someone is the owner.
A repeat handoff/return is identified by world lineage and generation.

Players reconnect to the newly opened Steam host. This is a saved-world handoff,
not migration of live Minecraft sockets. Originals are moved into managed local backups after confirmation, received
copies get a new saves folder, and divergent copies are never silently merged.
Personal Steam address settings and licence data are not sent with the world.

Safety and limits:

- Offers can arrive only from the authenticated Steam host being played on.
- Sender selection is limited to authenticated connected player identities.
- No world snapshot/source closure before the recipient accepts the first handoff.
- The independent bounded reliable stream survives closing the Minecraft world.
- SHA-256, CRC, ordered offsets, bounded native queues and cancellation are checked.
- Limits: 2 GiB compressed, 8 GiB expanded, 200,000 entries, bounded paths/depth.
- Path traversal, Windows reserved names, case collisions, links and arbitrary
  overwrites are rejected. Imports are staged and atomically committed.
- Local-host player data is saved under its owner and removed from level.dat,
  so the recipient does not inherit the old host's inventory. Steam/local UUID
  aliases preserve inventories, stats and advancements across role changes.
- Third-party mod data and entity ownership still need multiplayer testing.

The common suite reports 332 tests, zero failures and 10 skips. API and headless
checks passed. A two-account Fabric 1.21.1 handoff/return was confirmed in game,
including the recipient inventory fix. Bulk profile v2's congestion recovery was
verified by regressions; a fresh two-PC throughput measurement is still pending.
The isolated Windows native benchmark acknowledged both data rounds and restored
settings, but its client exited with an access violation inside SteamAPI.shutdown.
That standalone harness does not establish a clean native shutdown result.

Confirmed source saves are moved out of saves to the profile-local e4steam-world-backups directory. A managed retirement record starts a 3-day retention period; cleanup runs in the background on startup and at most once per minute while idle. Only canonical UUID directories with matching managed records can expire; open backups and unregistered folders are kept. World address settings stay local to the original owner and are restored before loading a returned world.

All twelve non-retro candidates were rebuilt with the final handoff, retention, owner-address and version adapters. The installed Prism matrix contains 27 existing modern test profiles. Fabric 26.3 started successfully to its menu; its world handoff still needs multiplayer validation.
