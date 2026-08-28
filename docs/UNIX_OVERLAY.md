# Steam overlay relaunch on Linux and macOS

e4steam can optionally restart the Minecraft JVM before LWJGL creates the game
window and preload Valve's installed overlay renderer. This is an opt-in
compatibility mode for **Linux x64** and **Intel macOS x86_64**. Normal Steam
transport, addresses and invitations do not require it.

Apple Silicon JVMs are intentionally excluded: Valve's current macOS overlay
renderer is x86_64 and cannot be injected into an arm64 JVM. Windows uses the
Steam client's normal overlay injection and does not use this setting.

## Prism Launcher and MultiMC

1. Take `tools/e4steam-stdin-agent-v0.3.0.jar` from the e4steam release.
2. In the instance Java arguments, add an absolute path:

   ```text
   -javaagent:"/absolute/path/to/e4steam-stdin-agent-v0.3.0.jar"
   ```

3. Start Minecraft once so `config/e4steam.toml` exists.
4. Close Minecraft and set:

   ```toml
   overlayRelaunch = true
   ```

5. Keep the Steam desktop client running and signed in, then start the instance
   normally. Do **not** add or launch the Minecraft launcher as a non-Steam game.

Prism and MultiMC pass launch metadata through standard input. The small Java 8
agent preserves at most 1 MiB of that hand-off in an owner-readable temporary
file so the replacement JVM receives the same data. A missing, changed,
truncated or unsafe capture makes e4steam skip the relaunch and continue the
original process instead of guessing arguments.

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

e4steam does not disable Gatekeeper, remove quarantine, request `sudo`, modify
Steam, or install system files. If relaunch fails, disable `overlayRelaunch` and
use the standalone Steam friends window or a copied e4steam address.
