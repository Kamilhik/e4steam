# Diagnostics and privacy

[Русская версия](DIAGNOSTICS_RU.md)

Use `/e4steam doctor` when Steam sharing will not start, an invitation cannot
be created or a connection stops before Minecraft login. Doctor does not repair
the session. It records enough local state to tell which startup stage failed.

## What you will see

The command produces two outputs:

1. chat shows a short status that is safe to read during the game;
2. `latest.log` receives a more detailed, bounded report for troubleshooting.

Typical status fields are:

| Field | Meaning |
| --- | --- |
| Steam runtime | Whether the Steam client API started and which lifecycle state it reached |
| Steam session | Whether e4steam is stopped, starting, sharing, joining or unhealthy |
| Local port | The loopback port used by the current Minecraft bridge |
| Recorded exception | The first bounded failure stored for the current runtime or session |
| Mod hash | A SHA-512 identifier for the installed JAR, useful for finding mismatched builds |

A value such as `UNHEALTHY` is a summary, not the root cause. Read the first
`recorded exception` and the earlier e4steam lines in `latest.log`.

## How to collect a useful report

1. Reproduce the problem once. Avoid twenty retries because repeated failures
   can hide the first useful message.
2. Run `/e4steam doctor` before closing the game.
3. Open the instance's `logs/latest.log`.
4. Save the complete text file. A screenshot usually cuts off the cause and
   makes stack traces hard to search.
5. Record Minecraft, loader, Java, e4steam, operating system and whether this
   computer was host, guest or a dedicated server.
6. If it was a connection failure, collect the host and guest logs from the
   same attempt.

For a crash, also attach the matching file from `crash-reports`. Do not send a
different launch log just because it is newer.

## What Doctor removes

The built-in report is limited to 64 KiB. Exception depth and individual
sections are bounded. The mod JAR is hashed as a stream instead of being loaded
entirely into memory.

Before writing diagnostic fields, e4steam removes or masks:

- credential-bearing `.steam` addresses and invite secrets;
- patterns that look like SteamID64 values;
- fields named as passwords, tickets, tokens, cookies, GSLT or private keys;
- the current user's home-directory path;
- native handles and raw packet contents.

Doctor does not include arbitrary files and never uploads a report. Everything
stays in the local Minecraft log until the user chooses to share it.

## What Doctor cannot remove

`latest.log` is shared by Minecraft, the loader and every installed mod. Another
mod may write a username, server address, file path or its own secret outside
the e4steam diagnostic block. Read the complete file before posting it in a
public issue or Discord channel.

If a live `s-...steam` or `d-...steam` address appears anywhere, stop and reopen
the session before sharing the log. That invalidates the old session data.

## Addon diagnostic contributions

Addon API 1.0 provides `DiagnosticsService`. An addon needs the
`diagnostics.contribute` capability before it can add a section. Contributions
run away from native and caller threads, have a two-second timeout and are
limited by field, section and total preview size. Core redaction runs again on
the final output.

`PrivacyOptions` can request selected non-secret Steam or lobby identifiers
only when a host UI permits them. Credentials can never be opted into. Addons
are normal Java mods rather than sandboxed programs, so users should still
install only code they trust.

## Reading common results

| Result | Next step |
| --- | --- |
| Steam runtime failed before a Steam ID exists | Follow [Steam startup troubleshooting](STEAM_TROUBLESHOOTING.md) |
| Runtime is running, session is stopped | Open a world for LAN or run `/e4steam start` |
| Session stays `STARTING` and records a timeout | Save the full log; check Steam connectivity, VPN/firewall routing and the first transport error |
| Mod hashes differ between host and guest | Install the same release JAR on both sides |
| No e4steam exception is recorded | Look earlier for a loader, Mixin, Minecraft login or another mod's failure |

Do not delete broad launcher caches or download replacement native libraries
from random websites. Use the first concrete error to decide the next step.
