# Contributing

e4steam is an unofficial derivative of e4mc. It is maintained by
**Kamilchik** and is not affiliated with Valve, Steam, Mojang, Microsoft or the
original e4mc maintainers.

## Quick path

1. Reproduce the problem in the smallest clean instance you can make.
2. Choose one focused change; do not mix a bug fix with unrelated formatting or
   generated files.
3. Add an automated test when the behavior can be isolated.
4. Run the checks for every affected modern, retro, client, server or API
   target.
5. Record manual evidence honestly: “builds” and “joins through Steam” are
   different results.
6. Update the changelog and compatibility matrix when user-visible behavior or
   support changes.

Small, well-tested fixes are welcome. You do not need to understand every
Minecraft version before improving one clearly identified target.

## Before opening an issue

- Report security problems through GitHub's private vulnerability form.
- Remove live join addresses, Steam session data, private account information,
  credentials and unrelated personal paths from logs.
- Check that all players use the same e4steam release, Minecraft version and a
  compatible loader.
- Include the smallest reproducible mod list and say whether the problem
  affects client hosting, joining or a dedicated server.

For a crash, attach the complete crash report and the relevant `latest.log`.
The useful cause is often above the last `InvocationTargetException`. For a
connection problem, say which machine hosted, which joined, whether an address
or invitation was used and on which attempt it succeeded. Do not post the live
address itself.

## Development setup

Install JDK 21, clone the repository and run on Windows:

```powershell
.\gradlew.bat clean releaseJars
.\gradlew.bat apiChecks
.\gradlew.bat headlessEntrypointAudit
.\gradlew.bat -p retro auditRetroArtifacts
```

On Linux or macOS:

```bash
./gradlew clean releaseJars
./gradlew apiChecks
./gradlew headlessEntrypointAudit
./gradlew -p retro auditRetroArtifacts
```

The checked-in Gradle configuration defines bytecode levels and supported
Minecraft ranges. A branch label such as `1.14.x` is not proof that every patch
in that branch works. Record the exact Minecraft, loader, Java and OS versions
used in a manual test.

Before opening a pull request, run:

```powershell
git diff --check
.\gradlew.bat --no-daemon releaseJars
.\gradlew.bat --no-daemon apiChecks
.\gradlew.bat --no-daemon -p retro auditRetroArtifacts
```

The root build covers the modern Fabric/Quilt, Forge and NeoForge artifacts,
including the two Minecraft 1.17-era files. The separate `retro` build covers
ten Forge and three Fabric Java 8 branch artifacts. Do not omit a variant
silently or describe a compile-only result as runtime support.

## Pull requests

- Keep the change focused and describe how it was tested.
- Add or update tests for protocol, address parsing, authentication and
  lifecycle changes.
- Update `CHANGELOG.md` and `COMPATIBILITY.md` when behavior changes.
- Keep `:api` on Java 8 and free of Minecraft, loader, JNA, Steamworks and
  internal implementation types. Extend it through typed services instead of
  changing an established interface incompatibly.
- Treat addons as trusted code in the same JVM, not sandboxed plugins. Never
  expose tickets, passwords, invite tokens, GSLT, native handles or raw packet
  hooks.
- Do not commit build output, launcher instances, logs, credentials, signing
  keys or generated `steam_appid.txt` files. The repository-root file that
  contains only `480` is an intentional development fixture.
- Preserve Apache 2.0 and third-party notices. Update
  `THIRD_PARTY_NOTICES.md` when dependencies, native libraries or adapted
  upstream files change.

In the pull request description, list:

- affected Minecraft versions and loaders;
- client, integrated server or dedicated server;
- exact commands that passed;
- exact manual scenarios that passed or failed;
- files deliberately left untested and why.

Do not include launcher instances, locally generated HTML/checklists,
PowerShell helpers, JavaScript previews, build output or personal test data in a
pull request. Mandatory loader resources already present in the repository are
the exception; for example, Forge coremod JavaScript is runtime code and must
remain packaged.

## Binary releases

Source contributions use the repository's Apache License 2.0. Compiled JARs
also contain Valve Steamworks redistributables under their own terms. Publish
stable binaries only after the automated and manual checks in `RELEASING.md`.
Do not mark Linux or macOS as verified without recorded evidence, and do not
turn a build, class audit or main-menu launch into a multiplayer claim.
