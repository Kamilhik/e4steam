# macOS

[Русская версия](MACOS_RU.md)

macOS support is included in the same e4steam JARs as Windows and Linux. There
is no separate macOS download. Intel and Apple Silicon native libraries are
packaged together, but the full host/join/reconnect matrix is still incomplete,
so macOS is marked **experimental** in 0.3.1.

## Quick start

1. Install the e4steam JAR that matches your Minecraft version and loader.
2. Use a 64-bit Java runtime that matches the way Minecraft is running:
   `x86_64` for Intel or Rosetta, `arm64` for native Apple Silicon.
3. Start the normal macOS Steam client and sign in.
4. Launch Minecraft normally from your launcher. Do **not** add the launcher to
   Steam as a non-Steam game.
5. Open a singleplayer world to LAN and use either the copied e4steam address
   or the invitation button.

Steam networking does not depend on the in-game overlay. If Shift+Tab does not
appear, copied addresses and Valve relay traffic can still work.

## Architecture and native libraries

| Minecraft process | Native slice used | Meaning |
| --- | --- | --- |
| Intel Mac, `x86_64` JVM | x86_64 | Native Intel run |
| Apple Silicon, `arm64` JVM | arm64 | Native Apple Silicon run |
| Apple Silicon, `x86_64` JVM | x86_64 | Rosetta fallback; not an arm64 result |

The runtime JAR contains universal `libsteam_api.dylib`,
`libsteamworks4j.dylib` and `libsteamworks4j-server.dylib` files. The build
checks their size and SHA-256, and macOS CI inspects them with `lipo`,
`otool -L` and `codesign --verify`. These checks prove that the files are
packaged correctly; they do not prove a real Steam multiplayer connection.

e4steam extracts native files only into its owner-controlled cache. It checks
file type, links, owner, size and SHA-256 before loading them. It does not load
Steam libraries from `PATH` or from an arbitrary launcher directory.

## Overlay support

The optional overlay relaunch is disabled by default. Enable it only when you
specifically want Valve's overlay inside the Minecraft window and first verify
that normal address-based joining works.

- Minecraft 1.17 and newer: set `overlayRelaunch = true` in
  `config/e4steam.toml`.
- Java 8 retro builds: add `-De4steam.overlayRelaunch=true` as a JVM argument.
- Older Prism/MultiMC combinations that hide their direct launch command cannot
  use the optional relaunch and continue without overlay injection.

Legacy Forge 1.7.x-1.12.x deliberately skips the relaunch on macOS. Those
versions can create more than one LWJGL window, and replacing the JVM may hide
the game, remove it from the Dock or start it repeatedly. Networking continues
without injected overlay support. Use the copied address or Steam's standalone
friends window instead.

The full explanation and rollback steps are in
[Unix overlay relaunch](UNIX_OVERLAY.md).

## Common problems

### `SteamAPI_Init failed`

Check that Steam is open, signed in and running as the same macOS user as
Minecraft. Quit stale Minecraft/Java processes, restart Steam, then start the
game again. Do not launch the Minecraft launcher through Steam.

### `Could not verify ownership of the native cache parent`

e4steam refused to trust the native cache location. Do not weaken file checks
or copy native libraries into random folders. Close the game, remove only the
e4steam native-cache directory shown in the log, and let the mod recreate it.
If the error remains, include the sanitized path category in a report.

### Minecraft restarts, disappears from the Dock or has no window

Disable the optional overlay relaunch. Remove
`-De4steam.overlayRelaunch=true` on retro versions or set
`overlayRelaunch = false` on modern versions. The Steam transport does not need
the relaunch.

### Invitation UI does not open

Copy the green `s-...steam` address and send it privately. The guest can paste
it into Minecraft's server address field. A missing overlay is not the same as
a failed Steam connection.

### Native architecture error

Check the architecture of the Java process, not only the Mac model. A Rosetta
launcher uses the x86_64 native slice. Use a matching 64-bit Java runtime and
avoid mixing arm64 and x86_64 Java/native files manually.

## Safe troubleshooting

e4steam never needs `sudo`, never disables Gatekeeper, never removes quarantine
flags and never changes macOS security settings. Do not run a command from an
untrusted support message merely to make a downloaded JAR load.

For a useful report, include:

- Mac model and CPU architecture;
- architecture reported by the active JVM;
- macOS, Java, Minecraft, loader and e4steam versions;
- whether normal address joining works without overlay relaunch;
- separate results for host, join, invitation, relay, disconnect and reconnect;
- the sanitized output from `/e4steam doctor`.

Never publish a live e4steam address, Steam ticket, account token or private
path. See [Diagnostics](DIAGNOSTICS.md) and
[Steam troubleshooting](STEAM_TROUBLESHOOTING.md).
