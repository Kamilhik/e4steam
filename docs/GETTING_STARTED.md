# Getting started with e4steam

[Русская версия](GETTING_STARTED_RU.md)

This guide covers a normal game hosted from a singleplayer world. Dedicated
servers have a separate [deployment guide](DEDICATED_DEPLOYMENT.md).

## What e4steam does

e4steam carries Minecraft traffic through Steam P2P or Valve relays. The host
opens a singleplayer world, chooses who may join and sends an invitation or a
short `s-...steam` address. No router changes or public IP are needed.

e4steam is not a VPN. A guest receives access to the shared Minecraft world and
configured UDP service, not to the host's files or home network.

## Before you install

Check these points on every player's computer:

1. Steam Desktop is installed, running and signed in.
2. Minecraft, the mod loader and e4steam versions match between host and guest.
3. The selected JAR matches both the Minecraft version and loader.
4. Fabric and Quilt installations also contain a compatible Fabric API.
5. Steam and Minecraft run as the same operating-system user and at the same
   privilege level.

Launch Minecraft normally from its launcher. Do not add the launcher to Steam
as a non-Steam game and do not start it through Steam; that can interfere with
the runtime e4steam creates for App ID 480.

## Choose the correct file

The filename states the loader and Minecraft range. It does not state the
operating system because one JAR contains the required 64-bit native libraries
for Windows, Linux and macOS.

| Minecraft | Loader | Filename contains | Extra dependency |
| --- | --- | --- | --- |
| 1.17-1.18.2 | Fabric or Quilt | `fabric-quilt-mc1.17-1.18.2` | Fabric API |
| 1.17.1-1.18.1 | Forge | `forge-mc1.17.1-1.18.1` | None |
| 1.19-1.21.11 | Fabric or Quilt | `fabric-quilt-mc1.19-1.21.11` | Fabric API |
| 1.18.2-1.20.2 | Forge | `forge-mc1.18.2-1.20.2` | None |
| 1.20.2-26.2 | NeoForge | `neoforge-mc1.20.2-26.2` | None |
| 26.1-26.2 | Fabric or Quilt | `fabric-quilt-mc26.1-26.2` | Fabric API |

Retro releases use one JAR per minor branch: Forge `1.7.x-1.16.x` and Fabric
`1.14.x-1.16.x`. Forge `1.7.x` also requires one external UniMixins `0.1.20`
or newer JAR. See [Retro porting and compatibility](RETRO_PORTING.md) before
adding a retro build to a large modpack.

When the filename and the table disagree, stop and check the current
[compatibility matrix](../COMPATIBILITY.md). Do not rename a JAR to make it look
compatible with another loader.

## Install the mod

1. Close Minecraft.
2. Open the game instance's `mods` directory.
3. Remove older e4steam JARs from that instance. Keep exactly one e4steam JAR.
4. Copy the matching release JAR into `mods`.
5. For Fabric or Quilt, install Fabric API in the same directory.
6. Start Steam and sign in.
7. Launch Minecraft normally and check the Mods screen or startup log for
   e4steam.

Repeat the same steps for every player. An addon may have its own loader and
Minecraft requirements; read its download page before installing it.

## Host a world

1. Enter the singleplayer world you want to share.
2. Open the pause menu and select **Open to LAN**.
3. Choose an access mode:
   - **Steam Friends** accepts compatible players who are Steam friends with
     the host;
   - **Invite Only** accepts players invited to the current private lobby.
4. Confirm the normal Minecraft LAN settings.
5. Wait for the green `s-...steam` address and chat buttons.

The Steam session starts for the open world and stops when the host closes the
connection or leaves the world. `/e4steam start` can reopen sharing while the
world is still running.

## Invite or join

The host can press the blue **Invite friends** button. If the overlay is not
available, e4steam may open Steam's separate friends window instead.

A guest can join in either way:

- accept the Steam invitation and confirm the Minecraft join request; or
- copy the host's green `s-...steam` address, open **Multiplayer → Direct
  Connection**, paste the address and connect.

The short address is valid only for the current sharing session. Opening the
world again creates a new session and invalidates the old token.

## Stop and restart sharing

- Use the red **Stop sharing** button to close the Steam connection. Minecraft
  asks for confirmation first.
- Use `/e4steam stop` when the command is available on that Minecraft branch.
- Use `/e4steam start` to open sharing again.
- Use `/e4steam restart` to replace a stuck session with a new one.
- Use `/e4steam doctor` to record a bounded local diagnostic report.

Leaving the world closes sharing automatically. It does not close Steam.

## Voice chat and other UDP mods

Minecraft itself uses TCP. e4steam can also carry a configured UDP endpoint.
Simple Voice Chat is detected automatically. Plasmo Voice works when it uses
the Minecraft port; another UDP mod can use the `voiceChatPort` setting.

Host and guest need compatible versions of the voice mod. A working Minecraft
connection does not prove that a separately configured UDP port is correct, so
test voice after the player has joined and spawned.

## Updating an existing world

Back up the world before changing e4steam, Minecraft or loader versions.

Worlds first used with e4steam `0.2.4` may show fresh guest progress once after
an update to `0.3.0+`. New releases derive a stable Minecraft UUID from the
authenticated Steam identity instead of the previous Mojang/offline UUID. If
you migrate a file in `world/playerdata`, copy the world first and keep the old
file until the correct player's inventory and position are confirmed.

## If something does not work

Start with the visible symptom:

| Symptom | First check |
| --- | --- |
| `SteamAPI_Init failed` | Follow [Steam startup troubleshooting](STEAM_TROUBLESHOOTING.md) |
| Invitation does not appear | Use the green address; confirm friendship/access mode and matching versions |
| `Invalid session` | Confirm both sides have the same e4steam build and reconnect through the current Steam session |
| Endless encryption or terrain loading | Save both host and guest `latest.log`; do not keep retrying with different JARs |
| Voice chat is disconnected | Verify the same voice-mod version and UDP port on both sides |
| Linux/macOS overlay is missing | Networking still works; read [Unix overlay relaunch](UNIX_OVERLAY.md) only if the overlay itself is needed |

Run `/e4steam doctor` and read [Diagnostics and privacy](DIAGNOSTICS.md) before
sharing a log. A screenshot usually hides the first useful exception; the full
text log is better.

## Limits and safety

- The integrated world keeps Minecraft's normal limit of eight players,
  including the host.
- App ID 480 is Valve's shared Spacewar test namespace, not a private e4steam
  namespace.
- Linux and macOS availability does not mean every desktop, JVM and loader
  combination has been manually verified.
- A join address should be treated as temporary private information. Stop and
  reopen sharing if it was posted publicly.
- Install only mods and addons you trust. Java mods run inside the same game
  process and are not sandboxed from one another.
