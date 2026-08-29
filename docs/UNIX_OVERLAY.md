# Steam overlay relaunch on Linux and macOS

e4steam can optionally restart the Minecraft JVM before LWJGL creates the game
window and preload Valve's installed overlay renderer. This is an opt-in
compatibility mode for **Linux x64** and **macOS x86_64/arm64**. Normal Steam
transport, addresses and invitations do not require it.

The automatic relaunch is included in both Java 16+ Minecraft 1.17+ artifacts
and the Java 8 retro artifacts for Minecraft 1.7.10–1.16.5. Current macOS Steam
installations provide a universal `gameoverlayrenderer.dylib` with Intel and
Apple Silicon slices, so the matching native JVM can use either. Both macOS
paths remain experimental until real host/join/relaunch checks are recorded.
Windows uses the Steam client's normal overlay injection and does not use this
setting.

## Prism Launcher and MultiMC

1. Take `tools/e4steam-stdin-agent-v0.3.0.jar` from the e4steam release. The
   agent is required for this optional mode on both modern and retro artifacts.
2. In the instance Java arguments, add an absolute path:

   ```text
   -javaagent:"/absolute/path/to/e4steam-stdin-agent-v0.3.0.jar"
   ```

3. For Minecraft 1.17+, start Minecraft once so `config/e4steam.toml` exists,
   close it and set:

   ```toml
   overlayRelaunch = true
   ```

   For a Java 8 retro artifact (Minecraft 1.7.10–1.16.5), add this second JVM
   argument instead; retro artifacts do not have the modern TOML option:

   ```text
   -De4steam.overlayRelaunch=true
   ```

4. Keep the Steam desktop client running and signed in, then start the instance
   normally. Do **not** add or launch the Minecraft launcher as a non-Steam game.

Prism and MultiMC pass launch metadata through standard input. The small Java 8
agent preserves at most 1 MiB of that hand-off in an owner-readable temporary
file so the replacement JVM receives the same data. A missing, changed,
truncated or unsafe capture makes e4steam skip the relaunch and continue the
original process instead of guessing arguments.

## Retro Forge status

The Java 8 relaunch path is now present and build-tested, but a relaunch alone
does not prove that Valve's widgets attach to every old Forge window. Fabric
1.14–1.16 has a successful external Intel-macOS report. Forge 1.7–1.13 has
reported overlay-initialization crashes, while Forge 1.14–1.16 can reach the
renderer hooks without making the widgets visible. Leave the option disabled
on those Forge versions unless testing it, and use the standalone Steam friends
window or copied address when the injected overlay is unavailable.

## Other launchers

Enable `overlayRelaunch` without the agent first. If the JVM command line cannot
be reconstructed safely, e4steam records a warning and continues without a
restart. Never copy Java arguments from an untrusted person: JVM arguments can
load arbitrary code before Minecraft starts.

## What e4steam searches

Only Valve's installed `gameoverlayrenderer.so` or
`gameoverlayrenderer.dylib` is used. The lookup covers native Steam, Flatpak and
Snap locations on Linux and the standard Steam application bundle on macOS.
The selected path must resolve to a readable regular file. Existing
`LD_PRELOAD` or `DYLD_INSERT_LIBRARIES` entries are preserved.
The replacement JVM receives the fixed non-secret `SteamAppId`, `SteamGameId`
and `SteamOverlayGameId` value `480`, which also avoids losing the App ID at a
sandbox/relaunch boundary.

e4steam does not disable Gatekeeper, remove quarantine, request `sudo`, modify
Steam, or install system files. If relaunch fails, disable `overlayRelaunch` and
use the standalone Steam friends window or a copied e4steam address.
