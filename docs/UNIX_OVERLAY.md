# Steam overlay relaunch on Linux and macOS

[Русская версия](UNIX_OVERLAY_RU.md)

e4steam networking works without an injected Steam overlay. The optional
relaunch exists only for users who want Valve's Shift+Tab interface inside the
Minecraft window on Linux or macOS.

> **Recommended default:** leave overlay relaunch disabled. First verify that
> hosting and address-based joining work. Enable the relaunch only if the
> overlay itself matters to you.

## Choose the right path

| Situation | What to do |
| --- | --- |
| Address joining works and you do not need Shift+Tab | Leave relaunch off |
| Invitation button opens Steam's standalone friends window | Leave relaunch off unless you specifically want injection |
| Minecraft 1.17+ on Linux/macOS | Try `overlayRelaunch = true` |
| Retro Minecraft on Java 8 | Try `-De4steam.overlayRelaunch=true` |
| Old Prism/MultiMC does not expose a safe launch command | Relaunch is unavailable; keep normal networking without injection |
| Forge 1.7.x-1.12.x on macOS | Do not force relaunch; this path is skipped intentionally |
| Game restarts twice, hangs or loses its window | Disable relaunch immediately |

Windows does not use this setting. The Steam client performs its normal
Windows overlay injection.

## Normal setup for Minecraft 1.17+

Keep the desktop Steam client running and signed in. Do not add Minecraft or
its launcher as a non-Steam game.

Open `config/e4steam.toml` and set:

```toml
overlayRelaunch = true
```

On a successful run, e4steam restarts the JVM once before LWJGL creates the
persistent game window. It preserves safe launcher arguments, preloads Valve's
installed renderer and marks the replacement process so it cannot relaunch
again.

To undo the change:

```toml
overlayRelaunch = false
```

## Retro setup for Java 8

Add this JVM argument to the instance:

```text
-De4steam.overlayRelaunch=true
```

Remove it, or change it to `false`, to disable the feature:

```text
-De4steam.overlayRelaunch=false
```

Forge 1.7.x-1.12.x has a special early startup path because Forge's splash
screen creates another LWJGL drawable. On Linux, e4steam starts the replacement
before that drawable and disables only the in-memory splash flag. It does not
edit `config/splash.properties`.

On macOS, the Forge 1.7.x-1.12.x relaunch is skipped. Testing showed that a
replacement JVM can disappear from the Dock, lose the game window or restart
repeatedly. Steam networking remains available without the overlay.

Forge 1.13.x-1.16.x uses an early ModLauncher hook and starts the replacement
with `-Dfml.earlyprogresswindow=false`, before the persistent Minecraft window
is created.

## Prism and MultiMC

Modern Prism versions expose enough information for e4steam to reconstruct the
direct Minecraft command. No additional agent is required.

Older Prism or MultiMC versions may hide important launch data. If the log says
that a safe direct command could not be recovered, e4steam cancels the optional
relaunch and keeps the original process instead of guessing arguments. Steam
networking and copied addresses remain available without overlay injection.

## What e4steam loads

Only Valve's installed `gameoverlayrenderer.so` or
`gameoverlayrenderer.dylib` is considered. The search covers:

- normal Steam installations on Linux;
- common Flatpak Steam locations;
- common Snap Steam locations;
- the standard Steam application bundle on macOS.

The selected renderer must resolve to a readable regular file. Existing
`LD_PRELOAD` or `DYLD_INSERT_LIBRARIES` values are preserved. The replacement
receives the fixed non-secret App ID values for Spacewar (`480`) so the Steam
context survives a launcher or sandbox boundary.

e4steam does not download an overlay renderer, use an arbitrary file from
`PATH`, modify Steam, request `sudo`, disable Gatekeeper or install system
files.

## Current status

These ranges have been reported outside the automated build environment:

| Loader | Minecraft range | Reported result |
| --- | --- | --- |
| Fabric | 1.14.x-26.2 | Overlay reported working |
| Forge | 1.17.x-1.20.2 | Overlay reported working |
| NeoForge | 1.20.2-26.2 | Overlay reported working |
| Retro Forge on Linux | 1.7.x-1.16.x | Early lifecycle path; more external retesting required |
| Retro Forge on macOS | 1.7.x-1.12.x | Relaunch skipped; transport works without injection |

Build tests audit entry points, packaged files and relaunch-loop prevention.
They cannot prove that Valve's widgets render on every desktop environment,
macOS release, launcher or graphics stack.

## If it fails

1. Disable relaunch and confirm that Minecraft starts normally.
2. Confirm that address-based e4steam joining works without the overlay.
3. Close every stale Java/Minecraft process before another attempt.
4. Check that Steam and Minecraft run as the same operating-system user.
5. On Linux, record whether Steam is native, Flatpak or Snap.
6. Run `/e4steam doctor` and inspect the report before sharing it.

Repeated relaunches, two LWJGL initializations, a missing Dock entry, a hidden
window or a process that remains only in the background all mean that the
optional compatibility path should be turned off for that setup.

When reporting a problem, include OS and architecture, desktop environment,
Steam packaging, launcher, Java, Minecraft, loader and e4steam version. Never
post a live join address or launcher secrets.
