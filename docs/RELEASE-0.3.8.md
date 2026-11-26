# e4steam 0.3.8

## English

- Fix intermittent dedicated-server login rejection: admitted Steam identity remains valid throughout the connection and is revoked when its socket closes.
- Restore an authenticated player's owned offline-identity save when returning to their verified Minecraft UUID. Backups are kept; newer vanilla progress is preserved.
- Fix player-identity verification when newer Minecraft versions use a separate owner-profile type.
- Fix authenticated logins on Minecraft 26.2+ after vanilla removed the connection encryption getter.
- Serialize local connection read-mode changes to prevent an older vanilla login race from leaving the client waiting for server data.
- Fix Forge 1.18.2 crashes in the login hook and player UUID tracking after joining. The actual Minecraft player UUID is preserved.
- Keep background server-list pings and concurrent personal-address lookups from interrupting real Steam connections.
- Bundle the Linux x86_64 dedicated Steam SDK and extract verified libraries automatically; no manual `.so` installation is needed.
- Use the Minecraft UDP port for personal-address discovery when no explicit query port is configured. Hosting firewalls must permit that UDP port.
- Do not start a player-world handoff listener for an anonymous dedicated server.

Six JARs for Minecraft 1.17–26.3: Fabric/Quilt, Forge and NeoForge. Install one matching e4steam JAR. Fabric/Quilt also require Fabric API. No retro JARs or usage-statistics sender are included.

## Русский

- Исправлены случайные отказы при входе на выделенный сервер: подтверждённая личность Steam сохраняется на время соединения и удаляется при его закрытии.
- При возвращении к подтверждённому UUID Minecraft переносится прогресс из прежнего офлайн-сохранения этого же владельца. Создаётся резервная копия; более свежий ванильный прогресс не заменяется.
- Исправлена проверка личности игрока на новых Minecraft с отдельным типом профиля владельца.
- Исправлен вход с подтверждением Minecraft на 26.2+, где удалён прежний метод проверки шифрования соединения.
- Изменения режима чтения локального соединения выполняются последовательно, чтобы старый сетевой код Minecraft не оставлял клиент в ожидании ответа сервера.
- Исправлены вылеты Forge 1.18.2 при подключении и чтении UUID после входа. Используется настоящий UUID персонажа Minecraft.
- Фоновые пинги списка серверов и одновременные поиски персонального адреса больше не вмешиваются в подключение через Steam.
- Linux-библиотеки выделенного Steam-сервера встроены в JAR и распаковываются с проверкой контрольных сумм. Вручную устанавливать `.so` не нужно; поддерживается Linux x86_64.
- При стандартной настройке для поиска персонального адреса используется UDP на игровом порту. Хостинг должен разрешать этот UDP-порт.
- Клиент не пытается принимать передачу мира от анонимного выделенного сервера.

Шесть JAR для Minecraft 1.17–26.3: Fabric/Quilt, Forge и NeoForge. Установите один подходящий e4steam JAR; Fabric/Quilt также требуют Fabric API. Ретро-сборки и отправка статистики использования в этот выпуск не входят.
