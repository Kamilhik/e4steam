# e4steam Addon API 1.1 — руководство

[English version](ADDON_API.md)

[![Maven Central](https://img.shields.io/maven-central/v/io.github.kamilhik/e4steam-api?label=Maven%20Central&style=flat-square)](https://central.sonatype.com/artifact/io.github.kamilhik/e4steam-api/1.0.0)

Addon API позволяет обычному моду Fabric, Quilt, Forge или NeoForge расширять
e4steam, не копируя Steam runtime и не привязываясь к внутренним классам
Minecraft. API `1.1.0` входит в ветку разработки e4steam `0.3.2`, сохраняет
всю публичную бинарную поверхность API 1.0 и собирается под Java 8. Аддоны,
которым хватает API 1.0, могут оставить диапазон `[1.0.0, 2.0.0)` и работать
с совместимыми версиями e4steam 0.3.x. Новые функции Public Directory требуют
e4steam 0.3.2+.
Встроенный runtime аддонов сейчас запускается в JAR для Minecraft 1.17 и
новее; retro-JAR 1.7–1.16 намеренно не связываются с современными классами
аддонов и GUI.

Подписанный API `1.0.0`, исходники, Javadocs и POM опубликованы в
[Maven Central](https://repo1.maven.org/maven2/io/github/kamilhik/e4steam-api/1.0.0/).
API 1.1.0 пока собирается из этого репозитория и до отдельной публикации Maven
должен подключаться только через `compileOnly`.

> [!IMPORTANT]
> Аддон работает как обычный код внутри JVM Minecraft. API ограничивает
> доступ к возможностям e4steam, но не является песочницей. Устанавливайте
> только доверенные аддоны.

## Как читать документацию

Эта страница — полное руководство для первого аддона. Отдельные документы
подробнее разбирают важные контракты:

| Тема | Подробный документ |
| --- | --- |
| Поиск аддона, проверка, запуск и освобождение ресурсов | [Жизненный цикл](ADDON_LIFECYCLE.md) |
| Контексты выполнения, тайм-ауты и отмена задач | [Потоки и планировщик](API_THREADING.md) |
| Каналы, кодирование, очереди и виртуальный UDP | [Сеть](API_NETWORKING.md) |
| Возможности, порядок допуска и изоляция ошибок | [Безопасность](ADDON_SECURITY.md) |
| Идентификаторы, логи, диагностика и хранилище | [Приватность](API_PRIVACY.md) |
| Версии API/мода/протокола и Maven | [Совместимость](API_COMPATIBILITY.md) |
| Публикация и подключение к публичным целям | [Public Directory API](PUBLIC_DIRECTORY_API_RU.md) |

Точные сигнатуры находятся в Javadocs и в `api/src/main/java`. Канонический
пример — проверяемый сборкой модуль `example-addon`; фрагменты в документации
показывают только относящуюся к разделу часть.

## Быстрый старт

### 1. Подключите API

Для разработки с API 1.1 положите `e4steam-api-1.1.0.jar` в папку `libs`
проекта аддона и подключите только на этапе компиляции. Во время игры API
предоставляет e4steam, поэтому отдельный JAR нельзя класть в `mods` или
встраивать в аддон:

~~~groovy
dependencies {
    compileOnly(files("libs/e4steam-api-1.1.0.jar"))
}
~~~

Для новых аддонов используйте зависимость из Maven Central:
`compileOnly("io.github.kamilhik:e4steam-api:1.1.0")`. Аддоны, собранные с API
1.0, сохраняют бинарную совместимость с этой runtime-версией. Сторонний
репозиторий не нужен. Не используйте `implementation`, `include`, `jarJar` или
Shadow и проверьте, что в готовом аддоне нет `link/e4steam/api/**`.

### 2. Создайте точку входа

~~~java
package example.hello;

import link.e4steam.api.ApiVersion;
import link.e4steam.api.ApiVersionRange;
import link.e4steam.api.addon.AddonContext;
import link.e4steam.api.addon.AddonDescriptor;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.addon.E4steamAddonEntrypoint;

import java.util.Collections;

public final class HelloAddon implements E4steamAddonEntrypoint {
    @Override
    public AddonDescriptor descriptor() {
        return new AddonDescriptor(
                new AddonId("example:hello"),
                "Hello addon",
                ApiVersion.parse("0.1.0"),
                new ApiVersionRange(
                        ApiVersion.parse("1.1.0"),
                        ApiVersion.parse("2.0.0")
                ),
                Collections.emptyList(),
                Collections.emptySet()
        );
    }

    @Override
    public void initialize(AddonContext context) {
        // Здесь регистрируются события, команды, UI и другие ресурсы.
    }
}
~~~

ID должен быть уникальным, написан строчными буквами и иметь namespace:
`yourstudio:your_addon`. Диапазон `[1.1.0, 2.0.0)` принимает совместимые
версии API 1.1+: нижняя граница включена, верхняя — нет. Если функции 1.1 не
используются, укажите `[1.0.0, 2.0.0)`.

### 3. Зарегистрируйте аддон в загрузчике

Для Fabric добавьте точку входа `e4steam` в `fabric.mod.json`:

~~~json
{
  "entrypoints": {
    "e4steam": [
      "example.hello.HelloAddon"
    ]
  },
  "depends": {
    "e4steam": ">=0.3.2"
  }
}
~~~

Для Forge и NeoForge создайте файл:

~~~text
src/main/resources/META-INF/services/link.e4steam.api.addon.E4steamAddonEntrypoint
~~~

В нём должна быть одна строка с полным именем класса:

~~~text
example.hello.HelloAddon
~~~

Аддон всё равно должен оставаться обычным модом со своим `mods.toml` или
`neoforge.mods.toml`. e4steam не сканирует и не запускает случайные JAR.
Используйте открытый диапазон вроде `>=0.3.2` или `[0.3.2,)`, а не точную
версию. Аддон только на API 1.0 может оставить `>=0.3.0`.

## Возможности и разрешения

Аддон перечисляет нужные возможности в `AddonDescriptor`:

- requested — желательные; недоступную возможность e4steam может не выдать;
- required — обязательное подмножество requested; без него аддон не запустится.

| Задача | Примеры capability | Сервис |
| --- | --- | --- |
| Наблюдать или управлять сессией | `SESSION_OBSERVE`, `SESSION_CONTROL` | `api.sessions()` |
| Читать безопасные данные игроков | `IDENTITY_MINECRAFT_READ`, `IDENTITY_STEAM_PROFILE_READ` | `api.identities()` |
| Создавать режимы доступа и лобби | `ACCESS_MODE_REGISTER`, `LOBBY_CREATE`, `LOBBY_SEARCH` | `api.access()`, `api.lobbies()` |
| Создавать сетевой канал или virtual UDP | `NETWORK_CHANNEL_REGISTER`, `UDP_PROVIDER_REGISTER` | `api.network()`, `api.udp()` |
| Добавлять UI и команды | `UI_CONTRIBUTE`, `COMMANDS_REGISTER` | `api.ui()`, `api.commands()` |
| Хранить настройки и данные | `CONFIG_READ`, `CONFIG_WRITE`, `STORAGE_PRIVATE` | `api.config()`, `api.storage()` |
| Работать с dedicated backend | `DEDICATED_OBSERVE`, `DEDICATED_ADMIN`, `DEDICATED_PUBLICATION_PROPOSE` | `api.dedicatedServers()` |
| Публиковать цели каталога и подключаться к ним | `DIRECTORY_PUBLICATION`, `DIRECTORY_ATTESTATION`, `DIRECTORY_JOIN` | `api.publicDirectory()` |
| Дополнять диагностику | `DIAGNOSTICS_CONTRIBUTE` | `api.diagnostics()` |

World Settings, Modpack Sync и Skins — только API-контракты для отдельных
аддонов. В core e4steam этих пользовательских функций нет.

## API выделенного сервера

Сервис `DedicatedServerService` доступен через
`context.api().dedicatedServers()`. Он работает, когда e4steam запущен на
выделенном сервере. В обычном клиенте методы возвращают типизированную ошибку
`UNSUPPORTED` или `UNAVAILABLE`.

Для чтения состояния и ожидания готовности запросите
`DEDICATED_OBSERVE`:

~~~java
import link.e4steam.api.ApiResult;
import link.e4steam.api.dedicated.DedicatedServerService;

DedicatedServerService dedicated = context.api().dedicatedServers();
ApiResult<DedicatedServerService.DedicatedServerSnapshot> result =
        dedicated.snapshot();

if (result.isSuccess() && result.value().isPresent()) {
    DedicatedServerService.DedicatedServerSnapshot snapshot =
            result.value().get();
    boolean acceptingPlayers = snapshot.state()
            == DedicatedServerService.DedicatedServerState.ACCEPTING;
}
~~~

Снимок содержит состояние жизненного цикла, режим доступа, число игроков,
лимит, статус ingress-защиты и публикации. `config()` возвращает настройки без
секретов, а `readiness()` завершается только после готовности Steam-транспорта,
защиты входа и Minecraft. Изменения можно получать через
`DedicatedStateEvent`.

Метод `drain(reasonCode)` требует `DEDICATED_ADMIN` и останавливает публикацию
сервера через e4steam, но не завершает процесс Minecraft. Для предложения
внешней публикации нужна `DEDICATED_PUBLICATION_PROPOSE`, однако ядро всё равно
может отказать, если нет разрешённого провайдера или публикация запрещена в
конфигурации. API не выдаёт Steam tickets, GSLT, закрытые данные дескриптора,
native handles и сырые пакеты протокола.

`DedicatedServerService.proposePublication(...)` остаётся отдельным защищённым
контрактом предложения. В API 1.1 появился `PublicDirectoryService`: аддон
каталога получает привязанную к generation непрозрачную цель и запускает
безопасное подключение, не видя внутренние объекты GameServer. Production
attestation закрыто отклоняется, пока оператор не настроит доверенный verifier
реестра.

## Public Directory API 1.1

`context.api().publicDirectory()` — общий мост для аддонов каталога. Он выдаёт
только непрозрачные цели публикации, ограниченные attestation receipts и
отменяемое состояние подключения. Steam tickets, токены приглашений, IP-адреса,
native handles и сырые пакеты остаются внутри core. Полный контракт,
capabilities, примеры и правила безопасности описаны в
[отдельном руководстве](PUBLIC_DIRECTORY_API_RU.md).

## Передавайте ресурсы под управление e4steam

Каждую подписку, регистрацию, сетевой канал и фоновую задачу нужно передать
в `context.resources()`. Тогда e4steam корректно закроет их при остановке:

~~~java
ApiResult<Subscription> result = context.api().events().subscribe(
        RuntimeReadyEvent.TYPE,
        event -> {
            // Обработчик должен быть коротким и неблокирующим.
        }
);

if (!result.isSuccess() || !result.value().isPresent()) {
    throw new IllegalStateException("Подписка отклонена");
}
context.resources().own(result.value().get());
~~~

Не создавайте второй Steam runtime, callback loop, lobby manager или набор
нативных библиотек. Используйте сервисы из `AddonContext`.

## Жизненный цикл

1. Загрузчик находит обычную точку входа аддона.
2. e4steam проверяет ID, версию API, зависимости и capabilities.
3. Аддоны сортируются по зависимостям.
4. В `initialize(context)` они регистрируют свои ресурсы.
5. Перед согласованием сетевых каналов регистрация замораживается.
6. При остановке все owned-ресурсы закрываются в обратном порядке.

Не блокируйте Minecraft-поток и Steam callback-поток. Для фоновой работы
используйте `api.scheduler()`. Изменения GUI возвращайте на клиентский поток
Minecraft через адаптер своего загрузчика.

## Правила сети

- ID сетевого канала должен иметь namespace и собственную версию.
- Канал регистрируется только во время инициализации.
- Канал открывается после основной Steam-аутентификации и проверки
  совместимости на обоих клиентах.
- Размер payload, очередь и режим доставки должны быть ограничены.
- Любой входящий payload считается недоверенным.
- Пароли, Steam tickets, секреты входа, cookies и native handles нельзя
  передавать, хранить или выводить в диагностике.

Полные правила: [сеть](API_NETWORKING.md), [потоки](API_THREADING.md),
[приватность](API_PRIVACY.md) и [безопасность](ADDON_SECURITY.md).

## Краткая карта сервисов

| Область | Метод |
| --- | --- |
| Runtime, аддоны, capabilities, события и scheduler | `runtime()`, `addons()`, `capabilities()`, `events()`, `scheduler()` |
| Игроки и сессии | `identities()`, `sessions()` |
| Режимы доступа и лобби | `access()`, `lobbies()` |
| Сетевые каналы и UDP | `network()`, `udp()` |
| Интерфейс и команды | `ui()`, `commands()` |
| Настройки и приватное хранилище | `config()`, `storage()` |
| Выделенные серверы | `dedicatedServers()` |
| Публичный каталог | `publicDirectory()` |
| Дополнительные provider-контракты | `worldSettings()`, `modpacks()`, `skins()` |
| Диагностика, локализация и журнал | `diagnostics()`, `localization()`, `logger()` |

## Проверка

В репозитории e4steam выполните:

~~~text
gradlew.bat apiChecks
~~~

Команда проверяет Java 8 bytecode, бинарную совместимость API, запрещённые
зависимости, Javadocs, testkit и пример аддона.
`api-testkit/build/libs/e4steam-api-testkit-1.1.0.jar` содержит
детерминированные подмены для тестов без запуска Minecraft и Steam.

`example-addon` показывает события, сетевой канал, UI, команды, config,
storage и scheduler. Это нейтральный пример для компиляции, а не готовый JAR:
в нём специально нет metadata конкретного загрузчика.

После `gradlew.bat :api:javadoc` откройте
`api/build/docs/javadoc/index.html` — там находится полный справочник классов
и методов.

## Пример выпущенного аддона: e4steam Friends

[e4steam Friends](https://github.com/K2-Studio-Development/e4steam-Friends) —
первый полноценный аддон e4steam. Он добавляет экран друзей Steam в стиле
Minecraft, статусы, поиск, приглашения, подключение и запросы на вход для
Fabric и NeoForge на Minecraft 26.2.

Этот проект показывает упаковку под загрузчики, клиентский жизненный цикл и
интеграцию интерфейса. Для работы с публичным API ориентируйтесь на
`example-addon` из основного репозитория. В e4steam Friends есть отдельная
прослойка совместимости для социальных данных, которых пока нет в API 1.1.
Новым аддонам нельзя копировать её или зависеть от
`link.e4steam.internal`.

## Частые ошибки

- API-классы встроены внутрь JAR аддона.
- Нет `e4steam` entrypoint у Fabric или service-файла у Forge/NeoForge.
- Вызов сервиса сделан без проверки выданной capability.
- Регистрация не передана в `context.resources().own(...)`.
- Сетевой канал регистрируется после инициализации.
- Аддон напрямую вызывает Steamworks или содержит ещё одну копию natives.
- GUI Minecraft изменяется с worker-потока.
- В лог попадают SteamID, токены или содержимое пакетов.

Версии независимы друг от друга:

- Addon API — `1.1.0` с бинарной совместимостью с 1.0.x;
- мод e4steam — `0.3.2`;
- основной сетевой протокол — `4`;
- у каждого сетевого канала аддона свой диапазон версий.

Публичная поверхность Addon API 1.1 стабильна и сохраняет всю бинарную
поверхность 1.0. Только специально отделённые
типы из `link.e4steam.api.experimental` не имеют гарантии бинарной
совместимости; это не делает аддоны или всю систему аддонов экспериментальными.
