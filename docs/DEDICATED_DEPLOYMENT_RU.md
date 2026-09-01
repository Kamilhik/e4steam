# Запуск выделенного сервера e4steam

[English version](DEDICATED_DEPLOYMENT.md)

Инструкция предполагает, что модифицированный Minecraft-сервер уже работает
без e4steam. Сначала проверьте обычный запуск, затем устанавливайте e4steam и
включайте Steam-only вход.

Основная поддерживаемая платформа — Windows x64. На Linux и macOS используются
те же JAR, но их полная ручная матрица пока не завершена.

## Что понадобится

- 64-битная ОС и Java, подходящая версии Minecraft;
- рабочий сервер Fabric/Quilt, Forge или NeoForge;
- обычный JAR e4steam для этой версии и загрузчика;
- Fabric API для Fabric/Quilt, если он требуется сборке;
- права записи в папку сервера и исходящий доступ к Steam/Valve.

Личный Steam-аккаунт, настольный Steam и GSLT серверу не нужны. Не запускайте
сервер через Steam.

Перед изменением рабочего сервера сохраните копию мира, `server.properties` и
папки `config`.

## 1. Установите JAR

1. Штатно остановите Minecraft-сервер.
2. Удалите старые версии e4steam из `mods`. В папке должен быть один JAR.
3. Скопируйте файл, подходящий загрузчику и диапазону Minecraft.
4. Для Fabric/Quilt добавьте Fabric API, если он требуется.
5. При необходимости один раз запустите сервер для создания папок загрузчика,
   затем снова остановите.

Отдельного серверного или отдельного JAR для Windows/Linux/macOS нет.

## 2. Закройте прямой вход в Minecraft

В `server.properties` укажите:

```properties
server-ip=127.0.0.1
enable-rcon=false
enable-query=false
```

`127.0.0.1` оставляет Minecraft за локальным авторизованным мостом e4steam.
RCON и стандартный query отключаются, потому что это отдельные сетевые входы,
которые не проходят проверку Steam.

Не используйте `0.0.0.0`, LAN-IP или публичный адрес. При небезопасной
настройке e4steam специально останавливает запуск. Runtime принимает и `::1`,
но без уже проверенной IPv6-конфигурации лучше оставить `127.0.0.1`.

## 3. Создайте конфигурацию

Создайте UTF-8 файл `config/e4steam-dedicated.toml`:

```toml
schema-version = 1
enabled = true
access-mode = "PRIVATE"
max-peers = 8
query-port = 65535
server-name = "e4steam Minecraft server"
whitelist = ["76561198000000001"]
auth-mode = "ANONYMOUS"
publication = false
ingress-guard = "STEAM_ONLY"
diagnostics-level = "BASIC"
relay-policy = "OFFICIAL_AUTOMATIC"
```

Замените пример SteamID64 на идентификаторы разрешённых игроков.

### Все поля

| Поле | Допустимое значение | Назначение |
| --- | --- | --- |
| `schema-version` | `1` | Версия строгого формата конфига |
| `enabled` | `true` или `false` | Включает dedicated backend e4steam |
| `access-mode` | `PRIVATE`, `WHITELIST`, `UNLISTED` | Определяет необходимость allowlist |
| `max-peers` | от `1` до `64` | Лимит Steam-подключений; обычное значение — `8` |
| `query-port` | от `0` до `65535` | Настройка query-порта Steam GameServer; по умолчанию `65535` |
| `server-name` | непустой текст до 64 символов | Безопасное имя backend |
| `whitelist` | массив строк SteamID64 | Начальный список для private/whitelist режима |
| `auth-mode` | `ANONYMOUS` | Фиксированный режим; GSLT не принимается |
| `publication` | `false` | Ядро не публикует сервер в общий каталог |
| `ingress-guard` | `STEAM_ONLY` | Перед Minecraft-login требуется авторизованный Steam-мост |
| `diagnostics-level` | `OFF` или `BASIC` | Уровень ограниченной диагностики e4steam |
| `relay-policy` | `OFFICIAL_AUTOMATIC` | Steam выбирает прямой P2P или официальный relay Valve |

`CUSTOM` предназначен для отдельного provider-аддона и не работает в одном
ядре.

Парсер поддерживает небольшой набор TOML. Неизвестное поле, дубликат, неверный
escape, symlink, файл больше 32 КиБ или ослабление обязательной защиты
останавливают запуск. Опечатка не должна превращаться в небезопасную настройку.

## 4. Переменные среды и JVM properties

Несекретные поля можно переопределить через `E4STEAM_DEDICATED_*` или
`-De4steam.dedicated.*`. JVM property имеет приоритет над файлом, а переменная
среды используется, если property отсутствует.

```text
E4STEAM_DEDICATED_ENABLED=true
E4STEAM_DEDICATED_ACCESS=WHITELIST
E4STEAM_DEDICATED_MAX_PEERS=8
E4STEAM_DEDICATED_QUERY_PORT=65535
E4STEAM_DEDICATED_NAME=e4steam Minecraft server
E4STEAM_DEDICATED_WHITELIST=76561198000000001,76561198000000002
```

Не записывайте пароль, auth ticket, cookie, token или private key в конфиг,
окружение или аргументы JVM. Настройки GSLT нет.

## 5. Запустите сервер

Используйте обычный start-скрипт или службу Minecraft. Личный Steam-клиент под
тем же серверным аккаунтом запускать не нужно.

Ожидаемые строки:

```text
e4steam dedicated state: TRANSPORT_READY
e4steam dedicated address: d-...steam
```

В retro-сборке первая строка может содержать `e4steam retro`. Адрес выводится
только после перехода в состояние `ACCEPTING`.

Если адреса нет:

1. найдите выше первую ошибку e4steam;
2. проверьте `server-ip`, RCON и query;
3. проверьте соответствие JAR, загрузчика и Minecraft;
4. проверьте исходящий доступ к Steam и архитектуру Java;
5. сохраните полный startup log.

## 6. Подключите игрока

1. Игрок устанавливает такой же релиз e4steam и запускает личный Steam.
2. Администратор приватно отправляет актуальный `d-...steam`.
3. Игрок открывает **«Сетевая игра → Прямое подключение»**, вставляет адрес и
   входит.
4. Проверьте появление в мире, загрузку чанков и повторный вход.

Версии Minecraft, загрузчика и e4steam должны совпадать. Вход по обычному
IP:port должен завершаться отказом, потому что Minecraft слушает loopback и
требует запись о Steam-допуске.

## Команды администратора

В современных версиях доступны команды уровня permission 4:

```text
e4steam-dedicated status
e4steam-dedicated descriptor
e4steam-dedicated allow <SteamID64 или e4steam UUID>
e4steam-dedicated unallow <SteamID64 или e4steam UUID>
e4steam-dedicated ban <SteamID64 или e4steam UUID>
e4steam-dedicated unban <SteamID64 или e4steam UUID>
e4steam-dedicated stop
```

`status` показывает состояние, режим, игроков и защиту. `descriptor` печатает
адрес только в `ACCEPTING`. Команды allow/ban меняют постоянное локальное
хранилище `config/e4steam-dedicated-access.txt`. `stop` закрывает новые входы
e4steam, но не завершает Minecraft.

В retro-серверах этих команд нет; allowlist редактируется до запуска.

## Остановка и обновление

Для сохранения мира используйте обычную команду Minecraft `stop`.
`e4steam-dedicated stop` останавливает только приём новых Steam-соединений.

Порядок обновления:

1. остановите Minecraft;
2. сделайте резервную копию;
3. замените старый JAR одним новым и обновите клиентов до той же версии;
4. прочитайте changelog о протоколе, идентификаторах и конфиге;
5. запустите сервер и дождитесь нового адреса;
6. проверьте один вход и один повторный вход.

При проблеме верните старый JAR и его конфиг из копии. Не храните две версии
e4steam в `mods`.

## Хостинги и контейнеры

Даже если панель сама создаёт команду запуска, сохраняйте условия:

- Minecraft и e4steam находятся в одном network namespace, listener доступен
  только через loopback;
- config и native cache принадлежат серверному пользователю;
- разрешён исходящий трафик Steam/Valve;
- публичный порт не ведёт напрямую на Minecraft listener;
- журналы и копии доступны только администраторам.

Официального container image и SLA в e4steam 0.3.1 нет. Перед переносом мира
проверьте точный образ, JVM и загрузчик.

## Финальная проверка

- [ ] Сервер дошёл до `ACCEPTING` и напечатал адрес.
- [ ] Разрешённый игрок вошёл и получил чанки.
- [ ] Повторный вход после отключения работает.
- [ ] Запрещённый или забаненный игрок отклоняется.
- [ ] Прямой TCP из LAN/интернета не доходит до Minecraft-login.
- [ ] Лимит игроков работает.
- [ ] `e4steam-dedicated stop` закрывает новые входы, но не убивает Minecraft.
- [ ] Обычный `stop` сохраняет мир и завершает backend.

Обоснование этих требований описано в
[модели безопасности](DEDICATED_SECURITY_RU.md).
