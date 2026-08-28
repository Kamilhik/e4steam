# Steam startup troubleshooting

`SteamAPI_Init failed` means the native Steam client API could not attach to
the Minecraft process. It is not a Minecraft login/session error and changing
the Minecraft nickname does not fix it.

## All platforms

1. Exit Minecraft and the launcher.
2. Start the normal Steam desktop client and sign in.
3. Start the Minecraft launcher normally. Do not launch the launcher through
   Steam and do not add it as a non-Steam game.
4. Keep `steam_appid.txt` next to the launched game process if e4steam created
   it. Its content must be exactly `480`; e4steam refuses to overwrite another
   App ID.
5. If Spacewar or an old session is stuck, fully exit Steam, start it again and
   then restart Minecraft.

## Windows

Steam and Minecraft must use the same Windows account and privilege level. A
common failure is Steam running as administrator while the launcher/Minecraft
runs normally, or the reverse. Close both and start both normally. e4steam
never requests administrator rights.

## Linux

Steam and the launcher must run as the same desktop user. Flatpak or Snap
packaging can hide the Steam installation or its per-user communication files
from a separately sandboxed launcher. Grant only the launcher-specific access
needed to see the user's Steam installation, or use a native package for one
of the applications. Do not run Steam or Minecraft with `sudo`.

If the log confirms that `steamclient.so` was found but `SteamAPI_Init` still
fails inside a sandbox, set these non-secret values as **per-instance
environment variables** in the launcher and restart the instance:

```text
SteamAppId=480
SteamGameId=480
SteamOverlayGameId=480
```

These values only identify the permanent Spacewar test App ID; they are not a
Steam login and do not replace the requirement that the normal Steam desktop
client is running under the same user. Do not put passwords, cookies, tickets
or API keys in launcher environment variables.

The optional overlay relaunch has separate instructions in
[UNIX_OVERLAY.md](UNIX_OVERLAY.md). The overlay is not required for copied
addresses or the Steam transport itself.

## Collecting a safe report

Run `/e4steam doctor`. Chat shows only a short reason; the bounded technical
report is written to `latest.log`. Join descriptors, SteamID64 values, named
credentials and the user-home path are redacted. Never post a raw auth ticket,
API token, password or private key in an issue.
