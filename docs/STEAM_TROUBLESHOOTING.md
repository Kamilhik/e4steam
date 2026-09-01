# Steam startup troubleshooting

[Русская версия](STEAM_TROUBLESHOOTING_RU.md)

`SteamAPI_Init failed` means that Minecraft could not attach to the running
Steam client API. It is not a Minecraft account-session error. Changing a
Minecraft nickname, deleting player data or reinstalling a world will not fix
it.

## Fast checklist

Try these steps in order:

1. Close Minecraft and its launcher.
2. Exit Steam completely, including its tray process.
3. Start the normal Steam desktop client and sign in.
4. Start the Minecraft launcher normally, not through Steam.
5. Launch the correct instance with one matching e4steam JAR.
6. If it still fails, run `/e4steam doctor` and open `latest.log`.

Do not add the launcher as a non-Steam game. e4steam initializes App ID 480
itself; wrapping the launcher in another Steam game context can give the JVM
the wrong owner or App ID.

## Understand the message

| Error text contains | Usually means |
| --- | --- |
| `Steam is not running or the current user is not signed in` | The Steam client is closed, logged out, hidden by a sandbox or running as another user/privilege level |
| `Could not verify ownership of the native cache parent` | The e4steam cache directory has an unexpected owner, link or permission layout |
| `Native cache entry is owned by another account` | A verified native file was copied from another OS account or inherited unsafe ownership |
| `Could not load steam_api and steamworks4j native libraries` | Wrong architecture, blocked/corrupted native file or an unsupported runtime environment |
| `Steam lobby creation failed: Timeout` | Steam initialized, but lobby or network callbacks did not complete; this is later than `SteamAPI_Init` |

Always use the first concrete exception from the log. Repeated lines after it
are often retry messages rather than separate causes.

## Windows

Steam and Minecraft must run under the same Windows account and privilege
level. The common failure is one program running as administrator and the other
running normally.

1. Close Steam, Minecraft and the launcher.
2. Open Task Manager and make sure no old game or Steam process from the failed
   attempt remains.
3. Start Steam normally. Do not use **Run as administrator**.
4. Start the launcher normally and retry.

e4steam does not need administrator rights. Running both programs as
administrator is not a recommended workaround.

If the error says that a native cache entry belongs to another account, close
the game and remove only e4steam's cache for the affected user. Do not weaken
permissions on the whole launcher or Steam directory. The next launch will
extract the pinned libraries again and verify their hash.

Antivirus software may quarantine a native library. Check its history for the
exact e4steam cache file instead of disabling protection globally. Restore a
file only when the JAR came from an official project release and the reported
path belongs to e4steam.

## Linux

Steam and the launcher must run as the same desktop user. Never run either with
`sudo`.

Native Steam, Flatpak and Snap use different filesystem and IPC boundaries.
Typical problem combinations are:

- native launcher with sandboxed Steam;
- Flatpak launcher with native Steam but no permission to see its files;
- two separate Steam installations where the wrong one is running.

Prefer running Steam and the launcher in compatible environments. If one is a
Flatpak, grant only the filesystem/socket access required to see the user's
actual Steam installation. Do not grant access to the entire system as a first
step.

When the log confirms that `steamclient.so` was found but App ID information
was lost at the sandbox boundary, set these non-secret variables for the game
instance:

```text
SteamAppId=480
SteamGameId=480
SteamOverlayGameId=480
```

They identify Spacewar. They do not log in to Steam and do not replace a
running desktop client. Never put a Steam password, cookie, ticket or API key
in launcher environment variables.

## macOS

Use a 64-bit JVM matching the intended architecture: x86_64 for an Intel JVM or
arm64 for an Apple Silicon JVM. A Rosetta x86_64 launch is a separate fallback,
not a native arm64 run.

Start Steam first, then launch Minecraft normally. e4steam does not disable
Gatekeeper, remove quarantine or request administrator rights. If macOS blocks
the JAR or native library, verify that the file came from an official release
and inspect the operating-system warning. Do not paste an untrusted command
that removes quarantine recursively.

See the full [macOS guide](MACOS.md) for architecture and overlay details.

## `steam_appid.txt`

e4steam may create `steam_appid.txt` beside the game process. Its complete
content must be:

```text
480
```

The mod refuses to overwrite a different App ID. If another mod or launcher
creates the file with another value, identify that owner instead of repeatedly
editing the file while the game is running.

## Spacewar and the overlay

Spacewar presence means App ID 480 started. It does not prove that the current
lobby, invitation or Minecraft bridge is healthy.

The overlay is optional for transport. If Shift+Tab does not work, the host can
use the blue invitation button, Steam's standalone friends window or the green
address. Linux/macOS overlay injection has a separate, opt-in
[relaunch guide](UNIX_OVERLAY.md). Leave it disabled unless the missing overlay
is the actual problem.

## VPN, firewall and relay problems

If Steam initializes but lobby creation or joining times out, the failure is
later in the connection path. Save a fresh Doctor report and compare host and
guest logs. A VPN or firewall can block Steam/Valve relay traffic even though
the desktop client itself is online.

Do not disable the firewall permanently. Test a narrow rule for Steam and the
Java process, then restore normal protection. Record whether the result changes
with the VPN disconnected so the cause can be reproduced.

## What to include in a report

- full `latest.log` from the failing attempt;
- crash report if one exists;
- Minecraft, loader, Java and e4steam versions;
- operating system and CPU architecture;
- launcher and whether Steam is native, Flatpak or Snap;
- role: host, guest or dedicated server;
- exact steps from launch to the error.

Read [Diagnostics and privacy](DIAGNOSTICS.md) before posting a log. Never send
a password, authentication ticket, live join address, cookie or private key.
