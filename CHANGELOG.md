# Changelog

All notable changes to e4steam are documented here. Version numbers below
belong to this fork and are independent of upstream e4mc releases.

## Unreleased

### English

- Fixed the Forge 1.17.1 startup crash caused by Forge renaming its server
  lifecycle events in 1.18. The shared Forge entrypoint now resolves the
  matching 1.17 or 1.18+ event API without linking an unavailable class.
  The corrected JAR reached the main menu on Forge 37.1.1/Minecraft 1.17.1
  with Java 16 and Forge 39.1.0/Minecraft 1.18.1 with Java 17 on Windows x64.
- Added an opt-in pre-LWJGL Steam Overlay relaunch for Linux x64 and macOS
  x86_64/arm64, including Java 8 retro artifacts. Prism/MultiMC-style
  launchers use a separate bounded Java 8 stdin
  agent so their original launch hand-off can be replayed to the replacement
  JVM. Failure to find or validate the launcher command, capture file or
  Valve-installed overlay now safely falls back to the original process.
- Documented the one-time playerdata migration that may be needed when a world
  moves from 0.2.4's Mojang/offline guest UUID to 0.3.0's stable Steam-derived
  UUID. Subsequent joins with the same Steam account keep the same identity.
- Added Steam-only dedicated-server ingress to every released retro Forge
  `1.7.x`–`1.16.x` and Fabric `1.14.x`–`1.16.x` artifact. The retro server
  uses anonymous Steam GameServer login, validates each auth ticket before
  opening a generation-bound loopback bridge, rejects direct TCP login and
  prints a `d-...steam` descriptor after readiness.
- Added replay and authenticated-loopback contract tests for the retro
  dedicated transport. Retained nonces are bounded, expire, and are zeroed
  when removed or during shutdown.
- Improved platform-specific `SteamAPI_Init` diagnostics for privilege-level,
  sandbox and operating-system user mismatches without changing Minecraft
  names, profiles or normal login behavior.
- Made the live Steam Networking Messages smoke test explicitly opt-in, so
  ordinary unit/CI runs no longer attach to the developer's signed-in Steam
  client or let native Steam diagnostics print account identity.
- Dedicated servers now print their `d-...steam` address automatically only
  after the Minecraft listener, world and Steam GameServer identity are all
  ready. Fixed the Minecraft 26.2 command-permission API change and moved
  Forge 1.7.x–1.12.x physical-server initialization before listener binding.
  Authenticated Windows x64 joins were manually verified on NeoForge 1.21.1,
  Fabric 26.2 and Forge 1.12.2.

### Русский

- Исправлен вылет при запуске Forge 1.17.1, вызванный переименованием событий
  жизненного цикла сервера в Forge 1.18. Общая точка входа Forge теперь
  выбирает подходящий API событий для 1.17 или 1.18+ без загрузки
  отсутствующего класса. Исправленный JAR дошёл до главного меню на Forge
  37.1.1/Minecraft 1.17.1 с Java 16 и Forge 39.1.0/Minecraft 1.18.1 с Java 17
  под Windows x64.
- Выделенные серверы теперь автоматически печатают адрес `d-...steam` только
  после готовности Minecraft listener, мира и Steam GameServer identity.
  Исправлено изменение API прав команд в Minecraft 26.2, а инициализация
  физического сервера Forge 1.7.x–1.12.x перенесена до привязки listener.
  На Windows x64 вручную подтверждены авторизованные входы на NeoForge 1.21.1,
  Fabric 26.2 и Forge 1.12.2.
- Добавлен опциональный перезапуск JVM до инициализации LWJGL для Steam Overlay
  на Linux x64 и macOS x86_64/arm64, включая Java 8 retro JAR. Для
  Prism/MultiMC используется отдельный
  ограниченный Java 8 stdin-agent, который сохраняет и повторяет исходный
  протокол запуска. Если команда, файл захвата или установленный Valve overlay
  не проходят проверку, Minecraft безопасно продолжает работу без перезапуска.
- Описан разовый перенос playerdata при обновлении мира с Mojang/offline UUID
  гостя из 0.2.4 на стабильный Steam-derived UUID версии 0.3.0. Все последующие
  входы с тем же Steam-аккаунтом используют одну identity.
- Steam-only вход выделенного сервера добавлен во все релизные retro JAR:
  Forge `1.7.x`–`1.16.x` и Fabric `1.14.x`–`1.16.x`. Retro-сервер анонимно
  входит через Steam GameServer, проверяет auth ticket до создания привязанного
  к поколению loopback bridge, отклоняет прямой TCP-вход и после готовности
  печатает адрес `d-...steam`.
- Добавлены contract-тесты защиты от повторного auth proof и создания
  авторизованного loopback-соединения в retro dedicated transport. Сохранённые
  nonce ограничены, истекают и обнуляются при удалении или остановке.
- Сообщения `SteamAPI_Init` теперь точнее объясняют несовпадение уровня прав,
  sandbox или пользователя ОС, не меняя ники, профили и обычную авторизацию
  Minecraft.
- Live smoke-тест Steam Networking Messages теперь запускается только явно:
  обычные unit/CI-проверки больше не подключаются к активному Steam разработчика
  и не позволяют нативной диагностике печатать identity аккаунта.

## 0.3.0 - 2026-08-25

### English

- Bound each authenticated Steam guest to a stable versioned Minecraft UUID
  and safe name derived from Steam identity. Persona-name changes no longer
  change ownership, and a guest cannot obtain the integrated-server owner
  bypass by supplying the host's Minecraft name. Added the matching legacy
  login adapter for Minecraft 1.17–1.18.2.
- Replaced the single RESET retry slot with a bounded, deduplicated,
  worker-generation-safe state machine using capped exponential backoff,
  jitter, maximum attempts/age and deterministic shutdown cleanup.
- Hardened Steam native extraction with an owner-controlled cache, no-follow
  type/owner/link checks, pinned size/SHA-256, bounded reads, atomic publication,
  process locks, absolute-path loading and redacted failures.
- Added loader-independent Java 8 Addon API `1.0.0`, typed services, Javadocs,
  a deterministic testkit, a compile-tested example, API JAR purity/classfile
  audits and a canonical binary-surface check. The API is published on Maven
  Central as `io.github.kamilhik:e4steam-api:1.0.0`.
- Implemented loader-native addon discovery and deterministic lifecycle with
  API/dependency/cycle validation, scoped capabilities, callback isolation,
  registration freeze and reverse-order resource cleanup. Core never scans or
  downloads arbitrary addon JARs.
- Implemented scoped runtime, events, scheduling, identity, session, access,
  lobby, UI, command, config, private storage, localization, logging and
  privacy-safe diagnostics adapters.
- Added authenticated namespaced addon networking with required/optional
  version negotiation, fragmentation/reassembly, replay and stale-generation
  rejection, bounded rates/queues and fair priority that protects Minecraft and
  control traffic. Added the bounded virtual UDP service.
- Added neutral World Settings, Modpack and Skin provider/staging contracts.
  Public Worlds, automatic mod installation, external skins and settings UI
  remain absent from core and require separate addons.
- Added universal macOS Intel/Apple Silicon Steam client/GameServer libraries,
  strict OS/architecture normalization, Mach-O slice/hash validation and Intel
  plus arm64 CI audits. Real macOS Steam multiplayer is not yet smoke-tested,
  so the platform remains experimental.
- Added a crash-safe Unix invitation path: Linux and macOS use the real Steam
  overlay when Steam reports it ready, otherwise e4steam opens the standalone
  Steam friends window while keeping lobby rich presence active.
- Added an opt-in headless `DEDICATED_GAME_SERVER` backend using anonymous Steam
  GameServer login, Steam auth-ticket validation, a generation-bound loopback
  ingress guard, stable identities, private/whitelist/unlisted policy, bans,
  console commands and graceful draining. Public advertising remains disabled.
- Added strict bounded `config/e4steam-dedicated.toml` parsing that rejects
  unknown/secret fields, unsafe bind/auth/publication settings and symlink or
  changing files. The App ID 480 backend intentionally has no GSLT input.
- Split modern and retro physical server entrypoints from client bootstrap and
  added transitive headless class-graph/JAR audits for Minecraft client, AWT,
  overlay and client Steam runtime leakage.
- Added supported Java 8 retro branch artifacts: Forge `1.7.x` through
  `1.16.x`, and Fabric `1.14.x` through `1.16.x`. Each branch
  is built on a documented representative patch and detects the actual running
  Minecraft version. Pre-1.14 Fabric-family ports are now described correctly
  as future Legacy Fabric or Ornithe targets (and Rift on 1.13.2), rather than
  regular Fabric/Quilt. There is no all-retro JAR, retro Quilt, pre-1.14
  Fabric-family artifact or non-Steam tunnel backend.
- Updated the retro Forge baselines to the latest official builds for 1.12.2
  (`14.23.5.2864`), 1.14.4 (`28.2.30`), 1.15.2 (`31.2.62`) and 1.16.5
  (`36.2.42`). Forge 1.7.x-1.12.x JARs now contain both uppercase and lowercase
  legacy language filenames, enforced by the artifact audit.
- Added an offline 19-profile Prism test kit and browser checklist for two-PC
  launch, LAN/Steam host, join, gameplay, reconnect and cleanup verification.
  Fabric/Quilt test profiles use SHA-pinned Fabric API files. Added a repeatable
  two-client Steam smoke runner that records only sanitized profile, hash and
  pass/fail evidence.
- Hardened Doctor output: it streams the mod hash, excludes raw Steam identity,
  redacts join addresses/secrets/user paths and bounds exception/report output.
- Tightened dedicated authentication ownership: every Steam auth session now
  has one cleanup owner, queued and timed-out admissions close deterministically,
  and copied tickets/nonces are zeroed immediately after use or cancellation.
- Reduced every retro JAR to the exact nine supported 64-bit Steam natives,
  excluding 32-bit and encrypted-ticket variants. Added a checked dependency/
  license inventory and a non-publishing Gradle/CI license audit.
- The root `releaseJars` task now builds, audits and collects all 13 retro branch
  JARs in `release/0.3.0` beside the six modern JARs, and rejects a release
  directory that is missing a candidate or contains an unexpected JAR.
- Expanded non-publishing SHA-pinned CI to Windows, Linux, macOS Intel, macOS
  arm64 and the retro branch artifact matrix. Retro branch JARs are regular
  supported release files; the compatibility matrix still records which exact
  baselines have received manual multiplayer checks.

### Русский

- Каждый подтверждённый Steam-гость теперь получает стабильный версионный
  Minecraft UUID и безопасное имя из Steam identity. Смена persona name не
  меняет владение, а гость не может получить права хоста, отправив его
  Minecraft-имя. Для Minecraft 1.17–1.18.2 добавлен отдельный login-адаптер.
- Один слот повторной отправки RESET заменён ограниченным, дедуплицируемым и
  привязанным к поколению Steam worker автоматом с capped exponential backoff,
  jitter, лимитами попыток/возраста и детерминированной очисткой при остановке.
- Кэш Steam-библиотек защищён проверками no-follow типа, владельца, ссылок,
  размера и закреплённого SHA-256, ограниченным чтением, атомарной публикацией,
  межпроцессной блокировкой, absolute-path загрузкой и редактированием ошибок.
- Реализован независимый от loader Java 8 Addon API `1.0.0`: typed services,
  Javadocs, детерминированный testkit, проверяемый пример, аудит чистоты и
  classfile API JAR, а также контроль бинарной поверхности. API опубликован в
  Maven Central как `io.github.kamilhik:e4steam-api:1.0.0`.
- Добавлены обнаружение аддонов обычными загрузчиками и детерминированный
  lifecycle с проверкой API/dependencies/cycles, scoped capabilities,
  изоляцией callback, заморозкой регистраций и обратным закрытием ресурсов.
  Core не ищет и не скачивает произвольные addon JAR.
- Реализованы scoped-адаптеры runtime, events, scheduler, identity, sessions,
  access, lobby, UI, commands, config, private storage, localization, logging и
  privacy-safe diagnostics.
- Добавлена сеть аддонов с namespaced-каналами, required/optional согласованием
  версий, fragmentation/reassembly, защитой от replay и stale generation,
  ограниченными rate/queues и fairness для защиты Minecraft/control traffic.
  Добавлен ограниченный virtual UDP service.
- Добавлены нейтральные контракты World Settings, Modpack и Skin providers/
  staging. Public Worlds, автоустановка модов, внешние скины и settings UI не
  входят в core и требуют отдельных аддонов.
- Добавлены universal macOS-библиотеки Steam client/GameServer для Intel и
  Apple Silicon, строгая нормализация OS/architecture, проверка Mach-O slices и
  hashes, CI для Intel и arm64. Реальный Steam multiplayer на macOS ещё не
  проверен, поэтому статус остаётся experimental.
- Добавлен безопасный путь приглашений для Unix: Linux и macOS используют
  настоящий Steam Overlay, когда Steam сообщает о его готовности, а иначе
  e4steam открывает отдельное окно друзей Steam, сохраняя rich presence лобби.
- Добавлен opt-in headless backend `DEDICATED_GAME_SERVER`: anonymous Steam
  GameServer login, проверка auth ticket, generation-bound loopback ingress,
  стабильные identity, private/whitelist/unlisted, bans, console-команды и
  graceful draining. Публичная публикация остаётся отключённой.
- Добавлен строгий ограниченный `config/e4steam-dedicated.toml`, который
  отклоняет неизвестные/секретные поля, небезопасные bind/auth/publication
  настройки, symlink и изменяемый во время чтения файл. Для App ID 480 нет GSLT
  input.
- Современные и retro physical-server entrypoints отделены от client bootstrap;
  добавлен транзитивный headless аудит class graph/JAR на утечки Minecraft
  client, AWT, overlay и клиентского Steam runtime.
- Добавлены поддерживаемые Java 8 retro JAR по веткам: Forge от `1.7.x` до
  `1.16.x` и Fabric от `1.14.x` до `1.16.x`. Каждая ветка
  собирается на документированной основной patch-версии, а мод определяет
  реальную запущенную версию Minecraft. Возможные порты до 1.14 теперь правильно
  названы отдельными целями Legacy Fabric или Ornithe, а для 1.13.2 также Rift,
  а не обычным Fabric/Quilt. Единого all-retro JAR, retro Quilt, Fabric-family
  артефактов до 1.14 и не-Steam tunnel backend нет.
- Основные Forge-версии обновлены до последних официальных сборок: 1.12.2
  (`14.23.5.2864`), 1.14.4 (`28.2.30`), 1.15.2 (`31.2.62`) и 1.16.5
  (`36.2.42`). В Forge JAR для 1.7.x-1.12.x теперь входят оба варианта имён
  старых lang-файлов; их наличие проверяется аудитом артефактов.
- Добавлены автономный набор из 19 Prism-профилей и браузерный чеклист для
  проверки запуска на двух ПК, открытия мира, Steam-подключения, игры,
  переподключения и завершения. Fabric/Quilt-профили используют Fabric API с
  закреплённым SHA-хешем. Добавлен повторяемый двухклиентный Steam smoke-скрипт,
  который сохраняет только безопасные сведения о профиле, SHA и результате.
- Doctor теперь потоково считает hash мода, исключает raw Steam identity,
  редактирует join addresses/secrets/user paths и ограничивает stack/report.
- Усилен lifecycle dedicated-аутентификации: у каждой Steam auth session теперь
  один владелец очистки, queued и timed-out admission завершаются
  детерминированно, а копии tickets/nonces сразу обнуляются после использования
  или отмены.
- В каждом retro JAR оставлены ровно девять поддерживаемых 64-bit Steam natives;
  32-bit и encrypted-ticket варианты исключены. Добавлены проверяемый список
  dependencies/licenses и непубликующий Gradle/CI license audit.
- Корневая задача `releaseJars` теперь собирает, проверяет и помещает все 13
  веточных retro JAR в `release/0.3.0` рядом с шестью современными JAR. Неполный
  набор или посторонний JAR в этой папке приводит к ошибке сборки.
- Непубликующий CI с actions по SHA расширен на Windows, Linux, macOS Intel,
  macOS arm64 и матрицу веточных retro JAR. Веточные retro JAR являются обычными
  поддерживаемыми файлами релиза; матрица совместимости отдельно показывает,
  какие основные версии вручную проверены в мультиплеере.

## 0.2.4 - 2026-08-09

### English

- Steam/Spacewar now starts automatically with Minecraft, stays active for the
  game process, and can recover cleanly when Steam becomes available later.
- Fixed Forge guest authentication and world loading races, including the
  Forge 1.20.1 timeout/infinite terrain loading path.
- Fixed reliable terminal packets being lost during temporary Steam network
  backpressure. Failed RESET packets no longer leave stale bridge slots.
- Oversized unrelated App ID 480 packets are now discarded without stopping
  the whole Steam runtime.
- Fixed direct Steam rich-presence invitations that do not use a lobby, and
  allowed canceled invitations to become usable again after stale callbacks.
- Fixed connection-capacity accounting during concurrent bridge shutdown and
  cleanup, plus duplicate command feedback on transitional Minecraft APIs.
- Improved Linux behavior for cached native libraries and invitation opening;
  removed instructions that incorrectly required launching Minecraft through Steam.
- Restored the `restoreDedicatedCommands` setting so disabling it no longer
  changes vanilla command permissions.
- Changed the e4steam project license from MIT to Apache License 2.0 while
  retaining the original MIT notices for inherited e4mc code.
- Forge clients now confirm that the Steam return path is ready before the
  host sends the large login-registry burst. A bounded fallback keeps older
  clients compatible instead of requiring several connection attempts.
- Broken Steam Networking Messages sessions now restart automatically, fixing
  repeated `.steam` address connections such as Fabric 26.2 after a failed join.

### Русский

- Steam/Spacewar теперь автоматически запускается вместе с Minecraft, остаётся
  активным до выхода из игры и корректно восстанавливается, если Steam стал
  доступен позже.
- Исправлены гонки авторизации гостя и загрузки мира на Forge, включая тайм-аут
  и бесконечную «Загрузку территории» на Forge 1.20.1.
- Надёжные завершающие пакеты больше не теряются при временной перегрузке сети
  Steam. Ошибка отправки RESET больше не оставляет занятый слот соединения.
- Слишком большие посторонние пакеты общего App ID 480 теперь отбрасываются и
  не останавливают весь Steam runtime.
- Исправлены прямые приглашения через Steam Rich Presence без лобби, а
  отменённое приглашение снова становится доступно после устаревшего callback.
- Исправлен учёт лимита при одновременном закрытии соединений и очистке, а также
  возможное дублирование сообщений команд в переходных версиях Minecraft.
- Улучшена работа Linux с кэшем нативных библиотек и открытием приглашений;
  удалены неверные советы запускать Minecraft через Steam.
- Настройка `restoreDedicatedCommands` снова соблюдается: её отключение больше
  не меняет стандартные права команд Minecraft.
- Лицензия проекта e4steam изменена с MIT на Apache License 2.0; исходные
  MIT-уведомления унаследованного кода e4mc сохранены.
- Клиент Forge теперь подтверждает готовность обратного Steam-соединения до
  отправки хостом большого пакета реестров. Ограниченный резервный таймер
  сохраняет совместимость со старыми клиентами и убирает повторные попытки входа.
- Повреждённые сессии Steam Networking Messages теперь перезапускаются
  автоматически; это исправляет повторный вход по адресу `.steam`, включая Fabric 26.2.

## 0.2.3 - 2026-08-06

### English

- Fixed the Forge 1.20.1 startup crash when another mod also bundles Kaleido
  Config. e4steam now isolates its private Kaleido runtime in every JAR.
- Fixed the Minecraft 26.2 crash when accepting a Steam invitation after the
  active screen and chat APIs moved from `Minecraft` to `Gui` and `Hud`.
- Replaced the full Java stack trace printed by `/e4steam doctor` in chat with
  a short diagnosis. Full technical details remain available in `latest.log`.

### Русский

- Исправлен вылет Forge 1.20.1 при запуске вместе с другим модом, который тоже
  включает Kaleido Config. Теперь e4steam изолирует свою копию Kaleido во всех JAR.
- Исправлен вылет Minecraft 26.2 при принятии приглашения Steam после переноса
  API активного экрана и чата из `Minecraft` в `Gui` и `Hud`.
- Вместо полного Java-стектрейса команда `/e4steam doctor` теперь показывает в
  чате краткую причину. Полная техническая информация сохраняется в `latest.log`.

## 0.2.2 - 2026-08-05

### English

- Added offline-launcher profile support for Steam guests. Mojang session
  authentication is bypassed only for an already authenticated Steam bridge;
  Minecraft keeps its normal offline profile through login and configuration.
- Replaced the deprecated `ISteamNetworking` transport with
  `ISteamNetworkingMessages`, the packet-oriented API built on Steam
  Networking Sockets.
- Raised the wire and lobby protocol version to 3 so legacy-transport clients
  fail compatibility checks instead of attempting an incompatible session.
- Added periodic acceptance for authenticated lobby peers and Steam Networking
  Sockets diagnostics to make session establishment reliable even when the
  asynchronous session-request callback is delayed.
- Restored the e4steam access selector on Minecraft 26.x, where the old
  `ShareToLanScreen` was replaced by `MultiplayerOptionsScreen`.
- Steam lobby creation now retries temporary network failures, improving
  startup on slow and VPN-routed connections.
- Fixed immediate guest disconnects and infinite falling during world loading
  by preserving reliable packet order, pacing bursts, and expanding the
  bounded localhost buffer.
- Restored the native Steam friends invitation dialog.
- Fixed missing Steamworks compatibility classes on Forge 1.18.1.
- Fixed the LAN configuration crash and restored the e4steam access selector
  on Fabric and Quilt 1.21.11.
- Removed the temporary movement lock after joining a world.

### Русский

- Добавлена поддержка офлайн-профилей лаунчеров для гостей Steam. Проверка
  сессии Mojang отключается только для уже авторизованного Steam bridge, а
  профиль Minecraft сохраняется штатным при переходе login/configuration.
- Устаревший транспорт `ISteamNetworking` заменён на
  `ISteamNetworkingMessages` — пакетный API поверх Steam Networking Sockets.
- Версия сетевого и lobby-протокола повышена до 3, чтобы клиенты со старым
  транспортом отклонялись проверкой совместимости.
- Добавлены периодическое принятие сессий проверенных участников lobby и
  диагностика Steam Networking Sockets на случай задержки callback-запроса.
- Возвращён выбор режима доступа e4steam на Minecraft 26.x, где старый
  `ShareToLanScreen` заменён новым `MultiplayerOptionsScreen`.
- Создание лобби Steam теперь повторяется при временных сетевых ошибках, что
  повышает надёжность запуска через медленные соединения и VPN.
- Исправлены мгновенные отключения гостей и бесконечное падение при загрузке
  мира: надёжные пакеты сохраняют порядок, обрабатываются равномерными
  порциями, а ограниченный локальный буфер увеличен.
- Возвращено нативное окно приглашения друзей Steam.
- Исправлены отсутствующие классы совместимости Steamworks на Forge 1.18.1.
- Исправлен вылет меню открытия мира для сети и возвращён выбор режима доступа
  e4steam на Fabric и Quilt 1.21.11.
- Убрана временная блокировка движения после подключения к миру.

## 0.2.1 - 2026-08-03

### English

- Removed the experimental 32-player expansion and restored Minecraft's
  standard integrated-world limit of 8 players including the host.
- Fixed the endless world/terrain loading screen on Minecraft 26.2.
- Rechecked world loading and Steam LAN sharing on Fabric 26.2, Quilt 1.20.2,
  Forge 1.20.2, and NeoForge 1.21.1.

### Русский

- Удалено экспериментальное расширение до 32 игроков и возвращён стандартный
  лимит Minecraft: 8 игроков вместе с хостом.
- Исправлена бесконечная загрузка мира/территории на Minecraft 26.2.
- Повторно проверены загрузка мира и открытие Steam-LAN на Fabric 26.2,
  Quilt 1.20.2, Forge 1.20.2 и NeoForge 1.21.1.

## 0.2.0 - 2026-08-01

### English

- Verified 99 Windows client launches across Fabric, Quilt, Forge, and NeoForge.
- Fixed modern Fabric API metadata for Minecraft 26.x.
- Fixed Forge 1.18.2 startup by removing an invalid inherited `PauseScreen.tick` injection.
- Spacewar now closes when Minecraft connects to a regular server.
- Replaced launcher-specific wording with generic Minecraft launcher guidance.
- Promoted e4steam to its first stable public release while keeping Steam App
  ID 480 as the permanent transport namespace.
- Removed obsolete alpha releases and normalized the stable release metadata.
- Documented installation, Steam Overlay setup, troubleshooting, supported
  files, platform limits, and the verified compatibility matrix.
- Added testable Steam lifecycle boundaries and regression coverage for
  restart, cancellation, invalid or expired invitations, unknown peers, queue
  overflow, Steam loss, world shutdown, lobby loss, and concurrent guests.
- Split Steam runtime responsibilities into lifecycle, packet transport,
  bridge registry, outbound queue, and lobby management components without
  changing the wire protocol.
- Updated the release, security, contribution, and bug-report documentation
  for the stable 0.2.0 line.
- Separated client-launch evidence from manual host/guest multiplayer evidence
  and added a native Windows build job to GitHub Actions.
- Documented that every loader/version JAR is shared by Windows x64 and Linux
  x64 and bundles native Steam libraries for both systems.

### Русский

- Проверен запуск 99 клиентов Windows на Fabric, Quilt, Forge и NeoForge.
- Исправлена зависимость Fabric API для Minecraft 26.x.
- Исправлен запуск Forge 1.18.2: удалено некорректное внедрение в унаследованный
  метод `PauseScreen.tick`.
- Spacewar теперь закрывается при подключении Minecraft к обычному серверу.
- Убраны упоминания конкретного лаунчера; подсказки подходят для любого
  лаунчера Minecraft.
- e4steam выпущен как первый стабильный релиз. Steam App ID 480 остаётся
  постоянным транспортным идентификатором проекта.
- Удалены устаревшие альфа-релизы и приведены в порядок данные стабильного релиза.
- Добавлены инструкции по установке, настройке оверлея Steam, устранению проблем,
  выбору файла и ограничениям платформ.
- Добавлены тесты перезапуска Steam, отмены подключения, неверных и просроченных
  приглашений, незнакомых пользователей, переполнения очереди, отключения Steam,
  закрытия мира, потери лобби и одновременных гостей.
- SteamRuntime разделён на компоненты жизненного цикла, транспорта пакетов,
  реестра соединений, очереди отправки и управления лобби без изменения протокола.
- Документы выпуска, безопасности, участия в разработке и сообщения об ошибках
  приведены к состоянию стабильной ветки 0.2.0.
- Проверки запуска клиента отделены от ручных host/guest-проверок, а в GitHub
  Actions добавлена отдельная сборка на Windows.
- Уточнено, что один JAR для выбранной версии и загрузчика используется на
  Windows x64 и Linux x64 и содержит Steam-библиотеки для обеих систем.

## 0.2.0-alpha.4 - 2026-08-01

- Added an activity-scoped UDP tunnel alongside the existing Minecraft TCP
  bridge, enabling voice chat and other UDP-based mods.
- Added automatic runtime port discovery for Simple Voice Chat and automatic
  Minecraft-port mapping for Plasmo Voice. The selected UDP endpoint is sent
  to guests during the Steam handshake.
- Voice datagrams use Steam's unreliable no-delay delivery and a separate
  bounded queue so voice traffic cannot starve the Minecraft connection.
- Added local UDP proxy tests, protocol validation, a configurable fallback
  `voiceChatPort`, and six-artifact UDP audits.
- Raised the e4steam wire and lobby protocol version to 2; both players must
  use the same `0.2.0-alpha.4` build.

## 0.2.0-alpha.3 - 2026-08-01

- Increased shared integrated-world capacity to 32 players total, including
  the host, and aligned the Steam lobby with the same limit.
- Added a shared, tested session-limit definition used by both Minecraft and
  the Steam transport.

## 0.2.0-alpha.2 - 2026-07-31

- Removed the direct pre-1.21 `GenericDirtMessageScreen` link and select the
  renamed 1.21+ `GenericMessageScreen` through the compatibility boundary.
- Corrected Fabric compatibility: the Command API v1 build now covers
  Minecraft 1.17–1.18.2, while the Command API v2 build starts at 1.19.
- Added an artifact audit that rejects a direct link to the renamed screen.

## 0.2.0-alpha.1 - 2026-07-31

- Renamed the separate project, mod ID, Java namespace, commands, and release
  artifacts to **e4steam**.
- Added public-repository contribution guidance, issue/PR templates, and
  Dependabot configuration.
- Included the complete Apache License 2.0 text required by the shaded Kaleido
  Config dependency in the third-party notices packaged with the mod.
- Added six release variants: separate experimental Fabric/Quilt and Forge
  legacy artifacts for 1.17.x and 1.17.1–1.18.1 respectively, Fabric/Quilt for
  1.18–1.21.11, Fabric/Quilt Modern for 26.1–26.2, Forge for
  1.18.2–1.20.2, and NeoForge for 1.20.2–26.2. Wider compatibility remains
  gated on per-version smoke tests.
- Shortened new connection addresses to the
  `s-<SteamID-in-base36>-<token-in-base36>.steam` form.
- Added runtime Minecraft-version discovery and compatibility adapters for
  buttons, tooltips, multiplayer connection, and world disconnect across the
  declared version families.

## 0.1.0-alpha.3 - 2026-07-30

- Added Steam friends-only and invitation-only lobby modes to Minecraft's Open to LAN screen.
- Added Shift+Tab invitation support through Steam lobbies and rich presence.
- Added a Steam friends button to Multiplayer and an invitation button to the pause menu.
- Added `/e4steam invite` and a clickable invitation action in the host chat message.
- Added a random 128-bit invitation check and direct host friendship check for every incoming bridge; invitation-only sessions also require current private-lobby membership.
- Made Steamworks restartable and activity-scoped: App ID 480 is inactive during ordinary Minecraft use and shuts down after hosting, waiting, or playing ends.
- Kept a local-only LAN mode that never initializes Steamworks.

## 0.1.0-alpha.2 - 2026-07-30

- Fixed Steam native library loading from isolated NeoForge, Forge, and Fabric mod class loaders.
- Added verified extraction of the bundled Windows/Linux x64 Steam libraries to a content-addressed local cache.
- Added detailed native loading errors instead of the previous generic initialization message.

## 0.1.0-alpha.1 - 2026-07-30

- Replaced the original public relay transport with a Steam P2P bridge.
- Added development initialization through App ID 480 (Spacewar) without launching the Spacewar game.
- Added authenticated host addresses for direct Steam connections.
- Added direct Steam P2P transport with Valve relay fallback.
- Required the mod and a signed-in Steam client on both host and guest.
- Targeted Windows x64 and Linux x64 for the first release.
- Limited the first release to Minecraft's integrated single-player server; dedicated servers are not yet supported.
- Documented that the legacy `ISteamNetworking` API is deprecated and should be replaced by Steam Networking Sockets in a future release.
- Added English and Russian in-game messages for the Steam-based flow.
- Added protocol tests, bounded queues, generation-safe terminal frames, graceful half-close handling, Steam send-queue draining, and redacted invite logging.
- Added a runtime check that refuses to continue unless Steam actually initializes the process as App ID 480.

## Upstream history

This repository is derived from e4mc by skyevg and contributors. The original project's release history predates this separate Steam fork and is intentionally not reused as this project's changelog.
