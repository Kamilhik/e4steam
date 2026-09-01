<!--
Stable CurseForge description.
Do not rewrite this text for patch releases. Until a product-wide change,
only new rows may be added to the Add-ons table.
-->

<div align="center">
<img src="https://media.forgecdn.net/avatars/thumbnails/1954/891/256/256/639211001772528223.png" width="180" alt="e4steam logo">
<h1>e4steam</h1>
<p><strong>Play Minecraft with friends through Steam — no port forwarding.</strong></p>
<p>
<a href="https://discord.gg/zFvrHz2ys7"><img src="https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&amp;logo=discord&amp;logoColor=white" height="36" alt="Discord"></a>
<a href="https://t.me/K2Studio_Dev"><img src="https://img.shields.io/badge/Telegram-26A5E4?style=for-the-badge&amp;logo=telegram&amp;logoColor=white" height="36" alt="Telegram"></a>
<a href="https://dalink.to/kamilchik1231"><img src="https://img.shields.io/badge/DALINK-FF6B00?style=for-the-badge&amp;logo=linktree&amp;logoColor=white" height="36" alt="DALink"></a>
<a href="https://github.com/Kamilhik/e4steam"><img src="https://img.shields.io/badge/GitHub-181717?style=for-the-badge&amp;logo=github&amp;logoColor=white" height="36" alt="GitHub"></a>
<a href="https://youtu.be/KJ1W_eJ2VK4"><img src="https://img.shields.io/badge/YouTube-FF0000?style=for-the-badge&amp;logo=youtube&amp;logoColor=white" height="36" alt="YouTube"></a>
</p>
</div>

## English

e4steam opens a singleplayer Minecraft world or a dedicated server to friends through Steam P2P and Valve relays.

- Choose **Steam Friends** or **Invite Only**.
- Invite through Steam or share a short `s-...steam` address.
- No router setup, public IP, or port forwarding is required.
- TCP and UDP traffic are supported.

### How to play

1. Install the matching e4steam JAR on both computers. Fabric and Quilt also require Fabric API.
2. Start Steam, sign in, and launch Minecraft normally.
3. Enter a singleplayer world and choose **Open to LAN → Steam Friends** or **Invite Only**.
4. Press **Invite friends**, or copy the green address and send it to your friend.

Both players must use matching Minecraft, loader, and e4steam versions.

Dedicated servers use the normal matching JAR and a `d-...steam` address. See the [dedicated-server guide](https://github.com/Kamilhik/e4steam/blob/main/docs/DEDICATED_SERVER.md).

---

## Русский

e4steam открывает одиночный мир Minecraft или выделенный сервер друзьям через Steam P2P и ретрансляторы Valve.

- Режимы **«Для друзей Steam»** и **«Только по приглашению»**.
- Приглашение через Steam или короткий адрес `s-...steam`.
- Не нужен проброс портов, публичный IP или настройка роутера.
- Поддерживается TCP- и UDP-трафик.

### Как играть

1. Установите подходящий JAR e4steam на оба компьютера. Для Fabric и Quilt также нужен Fabric API.
2. Запустите Steam, войдите в аккаунт и откройте Minecraft обычным способом.
3. Зайдите в одиночный мир и выберите **«Открыть для сети → Для друзей Steam»** или **«Только по приглашению»**.
4. Нажмите **«Пригласить друзей»** либо скопируйте зелёный адрес и отправьте его другу.

У обоих игроков должны совпадать версии Minecraft, загрузчика и e4steam.

Для выделенного сервера используется обычный подходящий JAR и адрес `d-...steam`. Подробности есть в [инструкции](https://github.com/Kamilhik/e4steam/blob/main/docs/DEDICATED_SERVER.md).

---

## Demonstration / Демонстрация

[![Watch the e4steam demonstration / Смотреть демонстрацию e4steam](https://img.youtube.com/vi/KJ1W_eJ2VK4/maxresdefault.jpg)](https://youtu.be/KJ1W_eJ2VK4)

### Open Server / Открытие мира

![Open Server](https://cdn.modrinth.com/data/cached_images/d6b56bd5c9285ed7c0a56af61a1198657a334028.gif)

### Close Server / Закрытие мира

![Close Server](https://cdn.modrinth.com/data/cached_images/6a93fb8208dcdeea1336607e1d75846af7e31cd7.gif)

---

## Add-ons / Аддоны

| Icon | Add-on | Description / Описание |
| :---: | --- | --- |
| <img src="https://raw.githubusercontent.com/K2-Studio-Development/e4steam-Friends/main/src/client/resources/assets/e4steam_friends/icon.png" width="56" alt="e4steam Friends icon"><br>CLIENT | [**e4steam Friends**](https://github.com/K2-Studio-Development/e4steam-Friends)<br>Minecraft 26.2 · Fabric / NeoForge | Minecraft-style Steam friends screen.<br>Экран друзей Steam в стиле Minecraft. |

### Addon API

For developers / Для разработчиков:

`compileOnly("io.github.kamilhik:e4steam-api:1.0.0")`

[Maven Central](https://central.sonatype.com/artifact/io.github.kamilhik/e4steam-api/1.0.0) · [Documentation](https://github.com/Kamilhik/e4steam/blob/main/docs/ADDON_API.md) · [Документация](https://github.com/Kamilhik/e4steam/blob/main/docs/ADDON_API_RU.md)

---

Created and maintained by **Kamilchik**. e4steam is an unofficial derivative of [e4mc](https://github.com/vgskye/e4mc-minecraft-architectury), distributed under the [Apache License 2.0](https://github.com/Kamilhik/e4steam/blob/main/LICENSE).
