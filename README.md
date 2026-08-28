<div align="center">

<img src="common/src/main/resources/assets/e4steam_minecraft/icon.png" width="180" alt="e4steam logo">

# e4steam

### Play Minecraft with friends through Steam

### Играйте с друзьями в Minecraft через Steam

🇬🇧 **English** · [🇷🇺 **Русский**](#русская-версия)

<a href="https://discord.gg/zFvrHz2ys7" title="Join the K2 Studio Discord"><img src="https://img.shields.io/badge/-%20-5865F2?style=for-the-badge&logo=discord&logoColor=white" height="42" alt="Discord"></a>
<a href="https://t.me/K2Studio_Dev" title="K2 Studio Telegram channel"><img src="https://img.shields.io/badge/-%20-26A5E4?style=for-the-badge&logo=telegram&logoColor=white" height="42" alt="Telegram"></a>
<a href="https://dalink.to/kamilchik1231" title="All project links on DAlink"><img src="docs/assets/dalink.svg" width="42" height="42" alt="DAlink"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/e4steam" title="Download on CurseForge"><img src="https://img.shields.io/badge/-%20-F16436?style=for-the-badge&logo=curseforge&logoColor=white" height="42" alt="CurseForge"></a>
<a href="https://github.com/Kamilhik/e4steam" title="GitHub repository"><img src="https://img.shields.io/badge/-%20-181717?style=for-the-badge&logo=github&logoColor=white" height="42" alt="GitHub"></a>
<a href="https://modrinth.com/project/SqqdJF90" title="View on Modrinth"><img src="https://img.shields.io/badge/-%20-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" height="42" alt="Modrinth"></a>
<a href="https://youtu.be/KJ1W_eJ2VK4" title="Watch the demonstration"><img src="https://img.shields.io/badge/-%20-FF0000?style=for-the-badge&logo=youtube&logoColor=white" height="42" alt="YouTube"></a>

[![Version](https://img.shields.io/github/v/release/Kamilhik/e4steam?display_name=tag&sort=semver&style=flat-square)](https://github.com/Kamilhik/e4steam/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/Kamilhik/e4steam/ci.yml?branch=main&label=build&style=flat-square)](https://github.com/Kamilhik/e4steam/actions/workflows/ci.yml)
[![Addon API](https://img.shields.io/maven-central/v/io.github.kamilhik/e4steam-api?label=Addon%20API&style=flat-square)](https://central.sonatype.com/artifact/io.github.kamilhik/e4steam-api/1.0.0)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square)](LICENSE)

**🇷🇺 Русская версия находится ниже — [открыть](#русская-версия)**

</div>

> [!IMPORTANT]
> **e4steam 0.3.0 is the current full release, not an alpha, beta or
> prerelease.** Windows x64 is the primary supported platform. Linux x64,
> macOS and dedicated servers are experimental. e4steam permanently uses the
> shared Steam test App ID 480 (Spacewar), so unrelated App ID 480 traffic is
> possible and is filtered.

> [!NOTE]
> 0.3.0 includes Addon API 1.0, loader-native addon discovery, macOS natives,
> an experimental fail-closed dedicated server and retro release artifacts.
> A Windows x64 NeoForge 1.21.1 dedicated server with one authenticated Steam
> client was manually verified on August 24, 2026. The two-client, macOS and
> remaining cross-platform matrices are not complete, so Linux, macOS and
> dedicated-server support remain experimental.

e4steam opens a Minecraft singleplayer world to Steam friends without port
forwarding or a public IP. Both players need the mod and a signed-in Steam
client. Minecraft TCP traffic and supported voice-chat UDP traffic travel over
Steam P2P or Valve relays.

Offline Minecraft launcher profiles are supported for Steam connections. In
0.3.0, the host derives each Steam guest's stable Minecraft
UUID and safe profile name from the authenticated SteamID rather than trusting
the name supplied by the client. Steam itself must still be running and signed
in on every computer.

## What's new in 0.3.0

The repository now contains a separate loader-independent Java 8 API artifact,
an addon testkit and a neutral compile-checked example. API 1.0 includes scoped
identity, session, dedicated, access, lobby, negotiated network, UDP, UI,
command, config, storage, world-settings, modpack, skin, diagnostics,
localization and logging contracts. Addons are discovered only through the
installed mod loader or Java service metadata; core never scans or downloads
arbitrary JAR files. See the [Addon API guide](docs/ADDON_API.md) and the
[compatibility matrix](COMPATIBILITY.md).

### Addon API for developers

The stable **e4steam Addon API 1.0.0** is published on
[Maven Central](https://central.sonatype.com/artifact/io.github.kamilhik/e4steam-api/1.0.0).
No custom Maven repository is required:

```groovy
dependencies {
    compileOnly("io.github.kamilhik:e4steam-api:1.0.0")
}
```

[Full Addon API documentation](docs/ADDON_API.md)

This API is not a sandbox: an installed addon is ordinary code in the same JVM
and must come from a trusted source. Core does not expose Steam passwords,
auth tickets, invite tokens, GSLT, native handles or raw protocol hooks.
Public Worlds, Modpack Sync, Offline Skins and World Settings remain separate
future addons. Their bounded API contracts exist, but none of those
user-facing features is included in core.

## Addons

Addon API 1.0 and addon support are stable parts of e4steam 0.3.0. Addons are
installed as normal loader mods; check each addon's version requirements.

### Client add-ons

| Icon | Add-on | Description |
| :---: | --- | --- |
| <img src="docs/assets/addons/e4steam-friends.png" width="64" alt="e4steam Friends icon"><br><sub>CLIENT</sub> | [**e4steam Friends**](https://github.com/K2-Studio-Development/e4steam-Friends) | A Minecraft-style Steam friends screen with presence, search, invitations, joining and join requests.<br>**Minecraft 26.2 · Fabric / NeoForge** |

Developers can start with the [Addon API guide](docs/ADDON_API.md), the
[testkit](api-testkit) and the compile-checked [example addon](example-addon).

Installed addons are ordinary code in the Minecraft JVM. Use only addons from
sources you trust; e4steam does not download or execute addons automatically.

## Which file should I download?

| Minecraft | Loader | File name contains | Extra dependency |
| --- | --- | --- | --- |
| 1.17–1.18.2 | Fabric/Quilt | `fabric-quilt-mc1.17-1.18.2` | Fabric API |
| 1.17.1–1.18.1 | Forge | `forge-mc1.17.1-1.18.1` | None |
| 1.19–1.21.x | Fabric/Quilt | `fabric-quilt-mc1.19-1.21.11` | Fabric API |
| 1.18.2–1.20.2 | Forge | `forge-mc1.18.2-1.20.2` | None |
| 1.20.2–1.21.x | NeoForge | `neoforge-mc1.20.2-26.2` | None |
| 26.1–26.2 | Fabric/Quilt or NeoForge | file containing `mc26.1-26.2` | Fabric API only for Fabric/Quilt |

The 0.3.0 release also includes retro JARs in `release/0.3.0`: Forge branch
JARs from `1.7.x` through `1.16.x`, and Fabric
branch JARs from `1.14.x` through `1.16.x`. Every filename identifies its loader
and minor branch. Fabric still requires the matching Fabric API. A successful
baseline test does not automatically prove every patch in that `.x` branch.

Each listed 0.3.0 JAR contains the required 64-bit Steam native libraries for
Windows, Linux and macOS. Download one file for your Minecraft version and
loader; there are no separate per-OS builds. Linux and macOS remain
experimental until their manual matrices are complete.

Declared ranges are broader than the manually tested matrix. Retro branch JARs
are supported release files; [COMPATIBILITY.md](COMPATIBILITY.md) records exact
manual coverage separately. Linux, macOS and dedicated modes remain experimental.

## Installation

1. Install the loader matching your Minecraft version.
2. Download the matching e4steam JAR from [GitHub Releases](https://github.com/Kamilhik/e4steam/releases), [CurseForge](https://www.curseforge.com/minecraft/mc-mods/e4steam), or [Modrinth](https://modrinth.com/mod/e4steam).
3. Put the JAR in the instance's `mods` folder. Fabric and Quilt also require Fabric API.
4. Install the same e4steam release on every player's computer.
5. Start Steam and sign in before launching Minecraft.

## Playing

1. Open a singleplayer world.
2. Select **Open to LAN → Steam friends** or **Invitation only**.
3. Press **Invite friends** and send the invitation in the Steam overlay.
4. Your friend accepts the invite and confirms joining in Minecraft.

Simple Voice Chat is detected automatically. Plasmo Voice is supported when it
shares Minecraft's port. Another UDP mod can use the `voiceChatPort` setting.

## Dedicated servers (experimental 0.3.0)

The same matching loader JAR can run on a headless Minecraft server. The
server uses anonymous Steam GameServer login, binds Minecraft to loopback and
prints a credential-free `d-...steam` descriptor for players. A personal Steam
client must not be launched on the server. Start with the
[dedicated deployment guide](docs/DEDICATED_DEPLOYMENT.md); the currently
recorded real smoke test is NeoForge 1.21.1 on Windows x64 with one client.

## If an invitation does not arrive

For `SteamAPI_Init failed`, first follow the
[Steam startup troubleshooting guide](docs/STEAM_TROUBLESHOOTING.md).

- Confirm both players are Steam friends, online, and using the same e4steam release.
- Confirm both use the same Minecraft version and compatible loaders.
- Make sure the Steam desktop client is running and signed in, then restart Minecraft.
- Ask the host to close and reopen the Steam connection, then send a new invite.
- For a friends-only lobby, copy the green e4steam address as a fallback.
- Restart Steam if Spacewar presence or the overlay is stuck.
- On Linux x64 or Intel macOS, see the optional
  [Steam overlay relaunch guide](docs/UNIX_OVERLAY.md). Prism/MultiMC require
  the separate Java 8 stdin agent included under `tools/` in the release.

## Known limitations

- App ID 480 is a shared test namespace and is not exclusive to e4steam.
- Windows x64 integrated worlds are the primary release path. Linux x64,
  macOS and dedicated servers are experimental. Dedicated
  NeoForge 1.21.1 has a one-client Windows smoke pass, but the full two-client
  and cross-platform matrix is still pending.
- 32-bit operating systems are unsupported.
- Both players need Steam, e4steam, matching Minecraft versions, and compatible loaders.
- Check the compatibility matrix for the exact versions and loaders that were manually smoke-tested.
- The shared world uses Minecraft's standard limit of 8 players including the host.

## Demo

[![e4steam video demonstration](https://img.youtube.com/vi/KJ1W_eJ2VK4/maxresdefault.jpg)](https://youtu.be/KJ1W_eJ2VK4)

![Open Server](https://cdn.modrinth.com/data/cached_images/d6b56bd5c9285ed7c0a56af61a1198657a334028.gif)

![Close Server](https://cdn.modrinth.com/data/cached_images/6a93fb8208dcdeea1336607e1d75846af7e31cd7.gif)

---

<details id="русская-версия">
<summary><h2>🇷🇺 Русская версия — нажмите, чтобы открыть</h2></summary>

> [!IMPORTANT]
> **e4steam 0.3.0 — текущий полноценный релиз, не alpha, beta или
> prerelease.** Основная поддерживаемая платформа — Windows x64. Linux x64,
> macOS и выделенные серверы имеют статус experimental. Мод навсегда использует
> общий тестовый Steam App ID 480 (Spacewar), поэтому посторонний трафик App ID
> 480 возможен и фильтруется.

> [!NOTE]
> В 0.3.0 реализованы Addon API 1.0, обнаружение аддонов через загрузчики,
> библиотеки macOS, защищённый experimental dedicated-сервер и отдельные
> релизные retro-сборки.
> 24 августа 2026 года вручную проверен выделенный NeoForge 1.21.1 сервер на
> Windows x64 с одним авторизованным Steam-клиентом. Проверки с двумя клиентами,
> macOS и оставшаяся межплатформенная матрица ещё не завершены, поэтому Linux,
> macOS и выделенные серверы пока имеют статус experimental.

e4steam позволяет открыть одиночный мир Minecraft друзьям из Steam без проброса
портов и белого IP. Мод и запущенный Steam нужны у всех игроков. TCP-трафик
Minecraft и UDP-трафик поддерживаемых голосовых модов передаются через Steam P2P
или ретрансляторы Valve.

Офлайн-профили Minecraft-лаунчеров поддерживаются для подключений через Steam.
В 0.3.0 хост создаёт стабильный Minecraft UUID и безопасное
имя гостя из подтверждённого SteamID, а не доверяет имени, присланному клиентом.
Сам Steam всё равно должен быть запущен, и на каждом компьютере должен быть
выполнен вход в аккаунт Steam.

## Что нового в 0.3.0

В репозитории появился отдельный независимый от загрузчика Java 8 API JAR,
testkit и нейтральный пример аддона с проверкой компиляции. API 1.0 содержит
scoped-контракты identity, sessions, dedicated, access, lobby, согласованных
сетевых каналов, UDP, UI, команд, config, storage, world settings, modpack,
skins, diagnostics, localization и logging. Аддоны обнаруживаются только
обычным загрузчиком модов или Java service metadata — core не ищет и не
скачивает произвольные JAR. Подробности: [Addon API](docs/ADDON_API_RU.md) и
[матрица совместимости](COMPATIBILITY.md).

### Addon API для разработчиков

Стабильный **e4steam Addon API 1.0.0** опубликован в
[Maven Central](https://central.sonatype.com/artifact/io.github.kamilhik/e4steam-api/1.0.0).
Сторонний Maven-репозиторий добавлять не нужно:

```groovy
dependencies {
    compileOnly("io.github.kamilhik:e4steam-api:1.0.0")
}
```

[Полная документация Addon API](docs/ADDON_API_RU.md)

API не является песочницей: установленный аддон — обычный код в той же JVM,
поэтому ставить можно только доверенные моды. Core не выдаёт пароли Steam,
auth tickets, invite tokens, GSLT, native handles и raw protocol hooks. Для
Public Worlds, Modpack Sync, Offline Skins и World Settings реализованы только
ограниченные API-контракты; самих пользовательских функций в e4steam core нет.

## Аддоны

Addon API 1.0 и поддержка аддонов — стабильные части e4steam 0.3.0. Аддоны
устанавливаются как обычные моды; точные требования указаны на их страницах.

### Клиентские аддоны

| Иконка | Аддон | Описание |
| :---: | --- | --- |
| <img src="docs/assets/addons/e4steam-friends.png" width="64" alt="Иконка e4steam Friends"><br><sub>CLIENT</sub> | [**e4steam Friends**](https://github.com/K2-Studio-Development/e4steam-Friends) | Экран друзей Steam в стиле Minecraft: статусы, поиск, приглашения, подключение и запросы на вход.<br>**Minecraft 26.2 · Fabric / NeoForge** |

Для разработчиков есть [руководство Addon API](docs/ADDON_API_RU.md),
[testkit](api-testkit) и проверяемый сборкой [пример](example-addon).

Установленный аддон — обычный код внутри JVM Minecraft. Используйте только
доверенные источники: e4steam сам не скачивает и не запускает аддоны.

## Какой файл скачивать

| Minecraft | Загрузчик | В названии файла | Дополнительно |
| --- | --- | --- | --- |
| 1.17–1.18.2 | Fabric/Quilt | `fabric-quilt-mc1.17-1.18.2` | Fabric API |
| 1.17.1–1.18.1 | Forge | `forge-mc1.17.1-1.18.1` | Ничего |
| 1.19–1.21.x | Fabric/Quilt | `fabric-quilt-mc1.19-1.21.11` | Fabric API |
| 1.18.2–1.20.2 | Forge | `forge-mc1.18.2-1.20.2` | Ничего |
| 1.20.2–1.21.x | NeoForge | `neoforge-mc1.20.2-26.2` | Ничего |
| 26.1–26.2 | Fabric/Quilt или NeoForge | файл с `mc26.1-26.2` | Fabric API только для Fabric/Quilt |

В релиз 0.3.0 также входят retro-сборки из `release/0.3.0`: веточные Forge JAR
от `1.7.x` до `1.16.x` и Fabric JAR от `1.14.x` до `1.16.x`.
В названии указаны загрузчик и ветка Minecraft. Для Fabric по-прежнему нужен
подходящий Fabric API. Проверка основной версии не доказывает автоматически
работу каждого патча внутри ветки `.x`.

Каждый JAR 0.3.0 содержит необходимые 64-битные библиотеки Steam для Windows,
Linux и macOS. Для своей версии Minecraft и загрузчика нужно скачать один файл
— отдельных сборок по ОС нет. Linux и macOS остаются экспериментальными до
завершения ручных проверок.

Заявленный диапазон шире проверенной матрицы. Retro JAR являются поддерживаемыми
файлами релиза, а [COMPATIBILITY.md](COMPATIBILITY.md) отдельно показывает точное
ручное покрытие. Linux, macOS и dedicated-режимы остаются экспериментальными.

## Как установить мод

1. Установите загрузчик, подходящий вашей версии Minecraft.
2. Скачайте нужный JAR с [GitHub Releases](https://github.com/Kamilhik/e4steam/releases), [CurseForge](https://www.curseforge.com/minecraft/mc-mods/e4steam) или [Modrinth](https://modrinth.com/mod/e4steam).
3. Поместите JAR в папку `mods`. Для Fabric и Quilt также установите Fabric API.
4. Установите одинаковый релиз e4steam всем игрокам.
5. Запустите Steam и войдите в аккаунт до запуска Minecraft.

## Как играть

1. Откройте одиночный мир.
2. Выберите **Открыть для сети → Для друзей Steam** или **Только по приглашению**.
3. Нажмите **Пригласить друзей** и отправьте приглашение через оверлей Steam.
4. Друг принимает приглашение и подтверждает вход в Minecraft.

Simple Voice Chat определяется автоматически. Plasmo Voice поддерживается,
когда использует порт Minecraft. Для другого UDP-мода укажите `voiceChatPort`.

## Выделенные серверы (experimental 0.3.0)

Обычный JAR для нужного загрузчика можно установить на headless Minecraft
server. Сервер анонимно входит через Steam GameServer, принимает Minecraft
только через локальный bridge и печатает адрес `d-...steam`. Личный клиент
Steam на серверном ПК запускать не нужно. Начните с
[инструкции по dedicated-серверу](docs/DEDICATED_DEPLOYMENT.md). Сейчас вручную
подтверждён NeoForge 1.21.1 на Windows x64 с одним клиентом.

## Если приглашение не приходит

Если появляется `SteamAPI_Init failed`, сначала откройте
[инструкцию по диагностике запуска Steam](docs/STEAM_TROUBLESHOOTING.md).

- Проверьте, что вы друзья в Steam, оба онлайн и используете одинаковый релиз e4steam.
- Проверьте совпадение версии Minecraft и совместимость загрузчиков.
- Убедитесь, что клиент Steam запущен и выполнен вход, затем перезапустите Minecraft.
- Закройте соединение, откройте его заново и отправьте новое приглашение.
- В режиме для друзей можно скопировать зелёный адрес как запасной вариант.
- Перезапустите Steam, если статус Spacewar или оверлей завис.
- На Linux x64 и Intel macOS можно включить дополнительный
  [перезапуск для оверлея Steam](docs/UNIX_OVERLAY.md). Для Prism/MultiMC нужен
  отдельный Java 8 агент из папки `tools/` внутри релиза.

## Известные ограничения

- App ID 480 — общий тестовый идентификатор, не принадлежащий e4steam.
- Открытые одиночные миры на Windows x64 — основной релизный режим. Linux x64,
  macOS и выделенные серверы имеют статус experimental. Dedicated NeoForge
  1.21.1 прошёл Windows-проверку с одним клиентом, но полная
  матрица с двумя клиентами и другими ОС ещё не завершена.
- 32-битные системы не поддерживаются.
- Всем нужны Steam, e4steam, одинаковая версия Minecraft и совместимые загрузчики.
- Точные вручную проверенные версии и загрузчики перечислены в матрице совместимости.
- Открытый мир использует стандартный лимит Minecraft: 8 игроков вместе с хостом.

</details>

---

Created and maintained by **Kamilchik**. e4steam is an unofficial fork of
[e4mc](https://github.com/vgskye/e4mc-minecraft-architectury), distributed under
the [Apache License 2.0](LICENSE), and is not affiliated with Valve, Mojang, or Microsoft.
