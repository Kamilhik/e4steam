# Contributing

Thanks for helping with e4steam. This repository is an
unofficial derivative of e4mc; it is not affiliated with Valve, Steam, Mojang,
Microsoft, or the original e4mc maintainers.

The project author and current maintainer is **Kamilchik**.

## Before opening an issue

- Use GitHub's private vulnerability reporting for security problems.
- Never post a complete generated join address, Steam session details, private
  account information, or an unreviewed log in a public issue.
- Check that both players use the same mod release and a compatible Minecraft
  version and loader.

## Development setup

Install JDK 21, clone the repository, and run:

```powershell
.\gradlew.bat clean releaseJars
.\gradlew.bat apiChecks
.\gradlew.bat headlessEntrypointAudit
.\gradlew.bat -p retro auditRetroArtifacts
```

On Linux:

```bash
./gradlew clean releaseJars
./gradlew apiChecks
./gradlew headlessEntrypointAudit
./gradlew -p retro auditRetroArtifacts
```

The produced bytecode and supported game versions are defined by the checked-in
Gradle configuration. Do not claim compatibility based only on a Minecraft
version-family label; test every loader/version combination listed in release
metadata.

Before submitting a pull request, also run:

```powershell
git diff --check
.\gradlew.bat --no-daemon releaseJars
.\gradlew.bat --no-daemon apiChecks
.\gradlew.bat --no-daemon -p retro auditRetroArtifacts
```

The root aggregate covers the two Minecraft 1.17 legacy artifacts, standard and
modern Fabric variants, Forge and NeoForge. The separate `retro` build covers
10 Forge branch and 3 Fabric branch Java 8 artifacts. Do not silently omit a
variant or infer runtime support from compilation.

## Pull requests

- Keep changes focused and explain how they were tested.
- Add or update tests for protocol, address-parsing, and lifecycle changes.
- Update `CHANGELOG.md` and compatibility documentation for user-visible
  behavior.
- Keep `:api` Java 8 and free from Minecraft, loader, JNA, Steamworks and
  internal implementation types. Use a new typed service instead of silently
  breaking the canonical API surface baseline.
- Treat addons as trusted code in the same JVM, not as sandboxed plugins. Never
  expose passwords, tickets, invite tokens, native handles or raw packet hooks.
- Do not commit build output, Minecraft instances, logs, credentials, signing
  keys, or generated `steam_appid.txt` files. The root `steam_appid.txt`
  containing only `480` is the intentional development fixture.
- Preserve all Apache 2.0 and third-party license notices, and update
  `THIRD_PARTY_NOTICES.md` when a dependency, native library or adapted
  upstream file changes.

## Binary distribution

Source contributions are welcome under the repository's Apache License 2.0. A compiled
JAR bundles Valve Steamworks redistributables that are not covered by that
license. Stable binaries may be published only after the applicable automated
and manual verification steps in `RELEASING.md` pass. Unverified macOS,
dedicated or retro artifacts must not be labeled supported or silently
included in a stable release.
