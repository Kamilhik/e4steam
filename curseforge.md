<div><img src="https://media.forgecdn.net/avatars/thumbnails/1954/891/256/256/639211001772528223.png" alt="e4steam logo" width="192" height="192"><h1>e4steam</h1><p><strong>Play in singleplayer Minecraft worlds with friends through Steam.</strong></p><p><a href="https://discord.gg/zFvrHz2ys7" rel="nofollow"><img src="https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&amp;logo=discord&amp;logoColor=white" alt="Discord" height="42"> </a><a href="https://t.me/K2Studio_Dev" rel="nofollow"><img src="https://img.shields.io/badge/Telegram-26A5E4?style=for-the-badge&amp;logo=telegram&amp;logoColor=white" alt="Telegram" height="42"> </a><a href="https://www.curseforge.com/minecraft/mc-mods/e4steam" rel="nofollow"><img src="https://img.shields.io/badge/CurseForge-F16436?style=for-the-badge&amp;logo=curseforge&amp;logoColor=white" alt="CurseForge" height="42"> </a><a href="https://github.com/Kamilhik/e4steam" rel="nofollow"><img src="https://img.shields.io/badge/GitHub-181717?style=for-the-badge&amp;logo=github&amp;logoColor=white" alt="GitHub" height="42"> </a><a href="https://modrinth.com/project/SqqdJF90" rel="nofollow"><img src="https://img.shields.io/badge/Modrinth-00AF5C?style=for-the-badge&amp;logo=modrinth&amp;logoColor=white" alt="Modrinth" height="42"> </a><a href="https://youtu.be/KJ1W_eJ2VK4" rel="nofollow"><img src="https://img.shields.io/badge/YouTube-FF0000?style=for-the-badge&amp;logo=youtube&amp;logoColor=white" alt="YouTube" height="42"></a></p><p><a href="https://central.sonatype.com/artifact/io.github.kamilhik/e4steam-api/1.0.0" rel="nofollow"><img src="https://img.shields.io/maven-central/v/io.github.kamilhik/e4steam-api?label=Addon%20API&amp;style=flat-square" alt="Addon API on Maven Central"></a> <a href="https://github.com/Kamilhik/e4steam/blob/main/LICENSE" rel="nofollow"><img src="https://img.shields.io/badge/License-Apache%202.0-2ea44f?style=flat-square" alt="Apache License 2.0"></a></p></div>

***

## Open Server

![Opening an e4steam server](https://cdn.modrinth.com/data/cached_images/d6b56bd5c9285ed7c0a56af61a1198657a334028.gif)

## Close Server

![Closing an e4steam server](https://cdn.modrinth.com/data/cached_images/6a93fb8208dcdeea1336607e1d75846af7e31cd7.gif)

***

## English

**e4steam 0.3.0** lets you play with friends in your singleplayer Minecraft worlds through Steam—without a dedicated server or router configuration.

Open your world to the network, select **Steam Friends** or **Invite Only**, and send an invitation through the Steam overlay. Friends can also join using Minecraft's **Direct Connection** menu and a short `s-...steam` address.

### Features

*   Play together in a regular singleplayer world
*   Invite friends through the Steam overlay with **Shift + Tab**
*   Choose between **Steam Friends** and **Invite Only** access modes
*   Join using a short direct-connection address
*   Copy the address, invite friends, or close the connection using colored chat buttons
*   Confirm before closing an active connection
*   Automatically close the connection when the host leaves the world
*   Reopen the connection using `/e4steam start`
*   Use the mod in multiple languages
*   Extend the mod with the stable **e4steam Addon API 1.0**
*   Run an optional dedicated server (currently experimental)

### Add-ons

| Icon | Add-on | Description |
| --- | --- | --- |
| <img src="https://raw.githubusercontent.com/K2-Studio-Development/e4steam-Friends/main/src/client/resources/assets/e4steam_friends/icon.png" alt="e4steam Friends icon" width="64" height="64"><br><img src="https://img.shields.io/badge/CLIENT-5865F2?style=flat-square" alt="Client add-on" height="20"> | [**e4steam Friends**](https://github.com/K2-Studio-Development/e4steam-Friends)<br>**Minecraft 26.2 · Fabric / NeoForge** | A Minecraft-style Steam friends screen with presence, search, invitations, joining, and join requests. |

### Addon API for developers

The stable **e4steam Addon API 1.0.0** is published on [Maven Central](https://central.sonatype.com/artifact/io.github.kamilhik/e4steam-api/1.0.0). No custom Maven repository is required:

```groovy
dependencies {
    compileOnly("io.github.kamilhik:e4steam-api:1.0.0")
}
```

[Full Addon API documentation](https://github.com/Kamilhik/e4steam/blob/main/docs/ADDON_API.md)

### How to play

1.  Start Steam and sign in to your account.
2.  Launch Minecraft and enter a singleplayer world.
3.  Open the pause menu and select **Open to LAN**.
4.  Choose **Steam Friends** or **Invite Only**.
5.  Invite a friend using the blue chat button or the Steam overlay.

Your friend can also copy the provided `s-...steam` address, open **Multiplayer → Direct Connection**, and paste it into the server address field.

> Steam and e4steam must be running for both the host and every joining player. The Minecraft version, mod loader, and e4steam version must match.

### Commands

*   `/e4steam start` — reopen the connection
*   `/e4steam invite` — open the friend invitation interface
*   `/e4steam restart` — restart the connection
*   `/e4steam doctor` — check whether Steam and the mod are ready

> **e4steam 0.3.0 is a full release.** Windows x64 is the primary supported platform. Linux x64, macOS, and dedicated servers are currently experimental. Addon API 1.0 is stable. The project is created and maintained by **Kamilchik** and is a separate, unofficial fork of e4mc.

***

## Русский

**e4steam 0.3.0** позволяет играть с друзьями в одиночных мирах Minecraft через Steam — без выделенного сервера и настройки роутера.

Откройте мир для сети, выберите режим **«Для друзей Steam»** или **«Только по приглашению»** и отправьте приглашение через оверлей Steam. Друг также может подключиться через меню **«Прямое подключение»**, используя короткий адрес `s-...steam`.

### Возможности

*   Игра с друзьями в обычном одиночном мире
*   Приглашения через оверлей Steam по сочетанию **Shift + Tab**
*   Режимы доступа **«Для друзей Steam»** и **«Только по приглашению»**
*   Короткий адрес для прямого подключения
*   Цветные кнопки в чате для копирования адреса, приглашения друзей и закрытия соединения
*   Подтверждение перед закрытием активного соединения
*   Автоматическое закрытие соединения при выходе владельца из мира
*   Повторный запуск соединения командой `/e4steam start`
*   Поддержка нескольких языков
*   Расширение возможностей мода через стабильный **e4steam Addon API 1.0**
*   Возможность запустить выделенный сервер (пока экспериментально)

### Аддоны

| Иконка | Аддон | Описание |
| --- | --- | --- |
| <img src="https://raw.githubusercontent.com/K2-Studio-Development/e4steam-Friends/main/src/client/resources/assets/e4steam_friends/icon.png" alt="Иконка e4steam Friends" width="64" height="64"><br><img src="https://img.shields.io/badge/CLIENT-5865F2?style=flat-square" alt="Клиентский аддон" height="20"> | [**e4steam Friends**](https://github.com/K2-Studio-Development/e4steam-Friends)<br>**Minecraft 26.2 · Fabric / NeoForge** | Экран друзей Steam в стиле Minecraft: статусы, поиск, приглашения, подключение и запросы на вход. |

### Addon API для разработчиков

Стабильный **e4steam Addon API 1.0.0** опубликован в [Maven Central](https://central.sonatype.com/artifact/io.github.kamilhik/e4steam-api/1.0.0). Сторонний Maven-репозиторий добавлять не нужно:

```groovy
dependencies {
    compileOnly("io.github.kamilhik:e4steam-api:1.0.0")
}
```

[Полная документация Addon API](https://github.com/Kamilhik/e4steam/blob/main/docs/ADDON_API_RU.md)

### Как начать игру

1.  Запустите Steam и войдите в свой аккаунт.
2.  Запустите Minecraft и зайдите в одиночный мир.
3.  Откройте меню паузы и нажмите **«Открыть для сети»**.
4.  Выберите **«Для друзей Steam»** или **«Только по приглашению»**.
5.  Пригласите друга через синюю кнопку в чате или оверлей Steam.

Друг также может скопировать полученный адрес `s-...steam`, открыть **«Сетевая игра» → «Прямое подключение»** и вставить его в поле адреса сервера.

> У владельца мира и всех подключающихся игроков должны быть запущены Steam и e4steam. Версии Minecraft, загрузчика модов и e4steam должны совпадать.

### Команды

*   `/e4steam start` — повторно открыть соединение
*   `/e4steam invite` — открыть интерфейс приглашения друзей
*   `/e4steam restart` — перезапустить соединение
*   `/e4steam doctor` — проверить готовность Steam и мода

> **e4steam 0.3.0 — полноценный релиз.** Основная поддерживаемая платформа — Windows x64. Linux x64, macOS и выделенные серверы пока имеют экспериментальный статус. Addon API 1.0 стабилен. Проект создан и поддерживается **Kamilchik** и является отдельным неофициальным форком e4mc.
