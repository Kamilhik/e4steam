<div align="center">

<img src="common/src/main/resources/assets/e4steam_minecraft/icon.png" width="180" alt="e4steam logo">

# e4steam

### Play Minecraft with friends through Steam

🇬🇧 **English** · 🇷🇺 **Русский**

<a href="https://discord.gg/k2EsPGQfMu" title="Join the Discord server"><img src="https://img.shields.io/badge/-%20-5865F2?style=for-the-badge&logo=discord&logoColor=white" height="42" alt="Discord"></a>
<a href="https://t.me/Kamilchikm" title="Telegram channel"><img src="https://img.shields.io/badge/-%20-26A5E4?style=for-the-badge&logo=telegram&logoColor=white" height="42" alt="Telegram"></a>
<a href="https://dalink.to/kamilchik1231" title="All project links on DAlink"><img src="docs/assets/dalink.svg" width="30" height="30" alt="DAlink"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/e4steam" title="Download on CurseForge"><img src="https://img.shields.io/badge/-%20-F16436?style=for-the-badge&logo=curseforge&logoColor=white" height="42" alt="CurseForge"></a>
<a href="https://github.com/Kamilhik/e4steam" title="GitHub repository"><img src="https://img.shields.io/badge/-%20-181717?style=for-the-badge&logo=github&logoColor=white" height="42" alt="GitHub"></a>
<a href="https://modrinth.com/project/SqqdJF90" title="View on Modrinth"><img src="https://img.shields.io/badge/-%20-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" height="42" alt="Modrinth"></a>
<a href="https://youtu.be/KJ1W_eJ2VK4" title="Watch the demonstration"><img src="https://img.shields.io/badge/-%20-FF0000?style=for-the-badge&logo=youtube&logoColor=white" height="42" alt="YouTube"></a>

[![Version](https://img.shields.io/github/v/release/Kamilhik/e4steam?display_name=tag&sort=semver&style=flat-square)](https://github.com/Kamilhik/e4steam/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/Kamilhik/e4steam/ci.yml?branch=main&label=build&style=flat-square)](https://github.com/Kamilhik/e4steam/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square)](LICENSE)

**🇷🇺 Русская версия находится ниже — [открыть](#русская-версия)**

</div>

> [!IMPORTANT]
> **e4steam 0.2.4 is the current stable release.** Windows x64 is the primary
> supported platform. Linux x64 is experimental. Dedicated servers and macOS
> are not supported. e4steam permanently uses the shared Steam test App ID 480
> (Spacewar), so unrelated App ID 480 traffic is possible and is filtered.

> [!WARNING]
> **0.3.0 is under development and is not a release yet.** The development
> branch contains Addon API 1.0, loader-native addon discovery, macOS natives,
> a fail-closed dedicated-server foundation and retro branch artifacts. These
> parts are built and unit-tested, but macOS Steam, dedicated two-client and
> retro Minecraft smoke tests have not been completed. They are experimental
> or build-only, not supported release features.

e4steam opens a Minecraft singleplayer world to Steam friends without port
forwarding or a public IP. Both players need the mod and a signed-in Steam
client. Minecraft TCP traffic and supported voice-chat UDP traffic travel over
Steam P2P or Valve relays.

Offline Minecraft launcher profiles are supported for Steam connections. In
0.3.0 development builds, the host derives each Steam guest's stable Minecraft
UUID and safe profile name from the authenticated SteamID rather than trusting
the name supplied by the client. Steam itself must still be running and signed
in on every computer.

## 0.3.0 platform development

The repository now contains a separate loader-independent Java 8 API artifact,
an addon testkit and a neutral compile-checked example. API 1.0 includes scoped
identity, session, dedicated, access, lobby, negotiated network, UDP, UI,
command, config, storage, world-settings, modpack, skin, diagnostics,
localization and logging contracts. Addons are discovered only through the
installed mod loader or Java service metadata; core never scans or downloads
arbitrary JAR files. See [docs/ADDON_API.md](docs/ADDON_API.md) and the current
[0.3.0 implementation status](docs/0.3.0_ROADMAP.md).

This API is not a sandbox: an installed addon is ordinary code in the same JVM
and must come from a trusted source. Core does not expose Steam passwords,
auth tickets, invite tokens, GSLT, native handles or raw protocol hooks.
Public Worlds, Modpack Sync, Offline Skins and World Settings remain separate
future addons. Their bounded API contracts exist, but none of those
user-facing features is included in core.

## Which file should I download?

| Minecraft | Loader | File name contains | Extra dependency |
| --- | --- | --- | --- |
| 1.17–1.18.2 | Fabric/Quilt | `fabric-quilt-mc1.17-1.18.2` | Fabric API |
| 1.17.1–1.18.1 | Forge | `forge-mc1.17.1-1.18.1` | None |
| 1.19–1.21.x | Fabric/Quilt | `fabric-quilt-mc1.19-1.21.11` | Fabric API |
| 1.18.2–1.20.2 | Forge | `forge-mc1.18.2-1.20.2` | None |
| 1.20.2–1.21.x | NeoForge | `neoforge-mc1.20.2-26.2` | None |
| 26.1–26.2 | Fabric/Quilt or NeoForge | file containing `mc26.1-26.2` | Fabric API only for Fabric/Quilt |

The 0.3.0 build-only retro candidates are also collected in `release/0.3.0`:
Forge branch JARs from `1.7.x` through `1.16.x`, and Fabric
branch JARs from `1.14.x` through `1.16.x`. Every filename identifies its loader
and minor branch. Fabric still requires the matching Fabric API. A successful
baseline test does not automatically prove every patch in that `.x` branch.

Each listed JAR already contains both Windows x64 and Linux x64 Steam native
libraries. Download one file for your Minecraft version and loader; there are
no separate Windows and Linux builds.

Declared ranges are broader than the manually tested matrix. Check
[COMPATIBILITY.md](COMPATIBILITY.md); unverified combinations are experimental.

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

## If an invitation does not arrive

- Confirm both players are Steam friends, online, and using the same e4steam release.
- Confirm both use the same Minecraft version and compatible loaders.
- Make sure the Steam desktop client is running and signed in, then restart Minecraft.
- Ask the host to close and reopen the Steam connection, then send a new invite.
- For a friends-only lobby, copy the green e4steam address as a fallback.
- Restart Steam if Spacewar presence or the overlay is stuck.

## Known limitations

- App ID 480 is a shared test namespace and is not exclusive to e4steam.
- The current 0.2.4 release supports integrated singleplayer worlds only.
- Windows x64 is primary; Linux x64 is experimental. The 0.3.0 macOS and
  dedicated implementations still require manual Steam smoke testing.
- 32-bit operating systems are unsupported.
- Both players need Steam, e4steam, matching Minecraft versions, and compatible loaders.
- Some declared version/loader combinations are experimental until manually smoke-tested.
- The shared world uses Minecraft's standard limit of 8 players including the host.

## Demo

[![e4steam video demonstration](https://img.youtube.com/vi/KJ1W_eJ2VK4/maxresdefault.jpg)](https://youtu.be/KJ1W_eJ2VK4)

![Open Server](https://cdn.modrinth.com/data/cached_images/d6b56bd5c9285ed7c0a56af61a1198657a334028.gif)

![Close Server](https://cdn.modrinth.com/data/cached_images/6a93fb8208dcdeea1336607e1d75846af7e31cd7.gif)

---

<details id="русская-версия" open>
<summary><h2>🇷🇺 Русская версия</h2></summary>

> [!IMPORTANT]
> **e4steam 0.2.4 — текущий стабильный релиз.** Основная поддерживаемая
> платформа — Windows x64. Linux x64 пока экспериментальный. Выделенные серверы
> и macOS не поддерживаются. Мод навсегда использует общий тестовый Steam App ID
> 480 (Spacewar), поэтому посторонний трафик App ID 480 возможен и фильтруется.

> [!WARNING]
> **0.3.0 находится в разработке и ещё не является релизом.** В рабочей ветке
> реализованы Addon API 1.0, обнаружение аддонов через загрузчики, библиотеки
> macOS, защищённый фундамент dedicated-сервера и отдельные retro-сборки. Они
> собираются и покрыты автоматическими тестами, но ручные Steam-проверки macOS,
> dedicated с двумя клиентами и старых Minecraft ещё не завершены. Поэтому их
> статус — experimental или build-only, а не готовая поддержка.

e4steam позволяет открыть одиночный мир Minecraft друзьям из Steam без проброса
портов и белого IP. Мод и запущенный Steam нужны у всех игроков. TCP-трафик
Minecraft и UDP-трафик поддерживаемых голосовых модов передаются через Steam P2P
или ретрансляторы Valve.

Офлайн-профили Minecraft-лаунчеров поддерживаются для подключений через Steam.
В тестовых сборках 0.3.0 хост создаёт стабильный Minecraft UUID и безопасное
имя гостя из подтверждённого SteamID, а не доверяет имени, присланному клиентом.
Сам Steam всё равно должен быть запущен, и на каждом компьютере должен быть
выполнен вход в аккаунт Steam.

## Разработка платформы 0.3.0

В репозитории появился отдельный независимый от загрузчика Java 8 API JAR,
testkit и нейтральный пример аддона с проверкой компиляции. API 1.0 содержит
scoped-контракты identity, sessions, dedicated, access, lobby, согласованных
сетевых каналов, UDP, UI, команд, config, storage, world settings, modpack,
skins, diagnostics, localization и logging. Аддоны обнаруживаются только
обычным загрузчиком модов или Java service metadata — core не ищет и не
скачивает произвольные JAR. Подробности: [Addon API](docs/ADDON_API.md) и
[статус реализации 0.3.0](docs/0.3.0_ROADMAP.md).

API не является песочницей: установленный аддон — обычный код в той же JVM,
поэтому ставить можно только доверенные моды. Core не выдаёт пароли Steam,
auth tickets, invite tokens, GSLT, native handles и raw protocol hooks. Для
Public Worlds, Modpack Sync, Offline Skins и World Settings реализованы только
ограниченные API-контракты; самих пользовательских функций в e4steam core нет.

## Какой файл скачивать

| Minecraft | Загрузчик | В названии файла | Дополнительно |
| --- | --- | --- | --- |
| 1.17–1.18.2 | Fabric/Quilt | `fabric-quilt-mc1.17-1.18.2` | Fabric API |
| 1.17.1–1.18.1 | Forge | `forge-mc1.17.1-1.18.1` | Ничего |
| 1.19–1.21.x | Fabric/Quilt | `fabric-quilt-mc1.19-1.21.11` | Fabric API |
| 1.18.2–1.20.2 | Forge | `forge-mc1.18.2-1.20.2` | Ничего |
| 1.20.2–1.21.x | NeoForge | `neoforge-mc1.20.2-26.2` | Ничего |
| 26.1–26.2 | Fabric/Quilt или NeoForge | файл с `mc26.1-26.2` | Fabric API только для Fabric/Quilt |

В `release/0.3.0` также находятся тестовые retro-сборки: веточные Forge JAR
от `1.7.x` до `1.16.x` и Fabric JAR от `1.14.x` до `1.16.x`.
В названии указаны загрузчик и ветка Minecraft. Для Fabric по-прежнему нужен
подходящий Fabric API. Проверка основной версии не доказывает автоматически
работу каждого патча внутри ветки `.x`.

Каждый указанный JAR уже содержит библиотеки Steam для Windows x64 и Linux x64.
Для своей версии Minecraft и загрузчика нужно скачать один файл — отдельных
сборок для Windows и Linux нет.

Заявленный диапазон шире проверенной матрицы. Смотрите
[COMPATIBILITY.md](COMPATIBILITY.md): непроверенные сочетания считаются экспериментальными.

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

## Если приглашение не приходит

- Проверьте, что вы друзья в Steam, оба онлайн и используете одинаковый релиз e4steam.
- Проверьте совпадение версии Minecraft и совместимость загрузчиков.
- Убедитесь, что клиент Steam запущен и выполнен вход, затем перезапустите Minecraft.
- Закройте соединение, откройте его заново и отправьте новое приглашение.
- В режиме для друзей можно скопировать зелёный адрес как запасной вариант.
- Перезапустите Steam, если статус Spacewar или оверлей завис.

## Известные ограничения

- App ID 480 — общий тестовый идентификатор, не принадлежащий e4steam.
- Текущий релиз 0.2.4 работает только с открытыми одиночными мирами.
- Windows x64 — основная платформа, Linux x64 экспериментальный. Реализации
  macOS и dedicated из 0.3.0 ещё требуют ручных Steam-проверок.
- 32-битные системы не поддерживаются.
- Всем нужны Steam, e4steam, одинаковая версия Minecraft и совместимые загрузчики.
- Непроверенные сочетания версий и загрузчиков считаются экспериментальными.
- Открытый мир использует стандартный лимит Minecraft: 8 игроков вместе с хостом.

</details>

---

Created and maintained by **Kamilchik**. e4steam is an unofficial fork of
[e4mc](https://github.com/vgskye/e4mc-minecraft-architectury), distributed under
the [Apache License 2.0](LICENSE), and is not affiliated with Valve, Mojang, or Microsoft.
