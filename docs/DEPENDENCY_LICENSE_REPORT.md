# Dependency and license report

This report describes direct dependencies relevant to distributed e4steam
artifacts. It does not treat Minecraft's complete loader/runtime classpath as
content of the mod JAR. Generated dependency trees remain available through
Gradle (`:common:dependencies` and the corresponding retro baseline project).

## Distributed project code

| Component | Version/source | Included in artifacts | Terms |
| --- | --- | --- | --- |
| e4steam core and API | 0.3.0 / API 1.0.0 | Yes | Apache License 2.0 |
| inherited e4mc portions | `vgskye/e4mc-minecraft-architectury` | Yes | MIT; notice retained in `THIRD_PARTY_NOTICES.md` |
| adapted retro seams | exact `xhyrom/e4mc-retro` revisions in `RETRO_PORTING.md` | Retro branch JARs | Apache License 2.0; attribution retained in `NOTICE` |

## Modern runtime JARs

| Dependency | Version | Packaging | License/redistribution status |
| --- | --- | --- | --- |
| steamworks4j | 1.10.0 | Java classes and selected 64-bit natives are shaded | MIT for the binding; text retained in `THIRD_PARTY_NOTICES.md` |
| steamworks4j-server | 1.10.0 | Java classes and selected 64-bit natives are shaded | MIT for the binding; text retained in `THIRD_PARTY_NOTICES.md` |
| Valve `steam_api` redistributables | supplied by the declared steamworks4j artifacts | Windows x64, Linux x64 and universal macOS dylib | Valve/Steamworks SDK terms; not relicensed under Apache 2.0 |
| Kaleido Config | 0.3.3+1.3.2 | Shaded and relocated under `link.e4steam.internal.kaleido` | Apache License 2.0; full text retained in `THIRD_PARTY_NOTICES.md` |
| Fabric API | loader/version-specific | Required beside Fabric/Quilt builds; not shaded | Apache License 2.0 |
| JNA | 5.10.0 in retro JARs; provided by modern Minecraft runtimes | Shaded only into Java 8 retro JARs | LGPL-2.1-or-later / Apache-2.0 dual licensing according to JNA upstream |

Minecraft, Fabric Loader, Forge, NeoForge, Architectury Loom, mappings and
their transitive runtime classpaths are build/loader inputs. They are not
copied into the six modern release JARs by e4steam's shadow configuration.

## Retro branch JARs

Every retro branch JAR shades the Java 8 e4steam core, JNA 5.10.0,
steamworks4j 1.10.0, steamworks4j-server 1.10.0 and exactly nine selected 64-bit Steam native files.
The build rejects 32-bit and encrypted-app-ticket native variants.

Additional compatibility dependencies are limited to the families that need
them:

| Target family | Dependency | Packaging/legal handling |
| --- | --- | --- |
| Forge 1.7.10 | UniMixins 0.1.20 | Shaded; its complete module-specific `META-INF/licenses` tree is preserved |
| Forge 1.8.9–1.12.2 | Sponge Mixin 0.7.11 | Shaded; upstream license resource remains in the JAR |
| Forge 1.13.2–1.16.5 | loader-provided Mixin | Compile-only; not shaded by e4steam |
| Fabric 1.14.4–1.16.5 | Fabric API and loader-provided Mixin | Runtime prerequisite/compile-only; not shaded by e4steam |

Because UniMixins aggregates modules under more than one upstream license, its
embedded per-module license bundle is authoritative; it must not be flattened
to a single e4steam license statement.

## Native and legal gates

- Runtime JARs must contain `LICENSE-e4steam.txt`, `NOTICE-e4steam.txt` and
  `THIRD_PARTY_NOTICES.md`.
- API and sources JARs must not contain runtime native libraries.
- Runtime JARs contain only the pinned 64-bit Windows, Linux and universal
  macOS native set; sizes and SHA-256 values are audited by Gradle.
- The macOS CI job verifies Mach-O x86_64 and arm64 slices plus dynamic-library
  paths. This is packaging evidence, not a real Steam multiplayer smoke test.
- No release may proceed until the maintainer confirms the applicable current
  Steamworks redistribution agreement and all manual gates in `RELEASING.md`.

Report baseline: e4steam 0.3.0 release, 2026-08-25.
