# Public Directory API 1.1

[English version](PUBLIC_DIRECTORY_API.md)

`PublicDirectoryService` — стабильная граница между e4steam и аддоном,
который публикует или показывает каталог открытых миров и выделенных серверов.
Сервис входит в Addon API 1.1.0 и требует e4steam 0.3.2 или новее.

Аддон отвечает за интерфейс каталога и протокол реестра. e4steam отвечает за
Steam-аутентификацию, секреты подключения, generation текущей сессии и сам
вход. API не выдаёт raw Steam ticket, токен приглашения, IP-адрес, объект
Steamworks или пакет внутреннего протокола.

## Зависимость и диапазон версий

Пока API 1.1.0 не опубликован отдельно в Maven Central, используйте JAR из
комплекта для разработки или из этого репозитория:

```groovy
dependencies {
    compileOnly(files("libs/e4steam-api-1.1.0.jar"))
}
```

Не кладите этот JAR в `mods` и не встраивайте его в аддон. Runtime-классы уже
находятся в e4steam.

Аддону с этим сервисом нужен диапазон:

```java
new ApiVersionRange(
        ApiVersion.parse("1.1.0"),
        ApiVersion.parse("2.0.0")
)
```

В metadata загрузчика укажите открытый диапазон: `>=0.3.2` для Fabric/Quilt
или `[0.3.2,)` для Forge/NeoForge. Не привязывайтесь только к 0.3.2.

## Capabilities

Запрашивайте только нужные операции:

| Capability | Что разрешает |
| --- | --- |
| `DIRECTORY_PUBLICATION` | Получить непрозрачную цель активной generation хоста |
| `DIRECTORY_ATTESTATION` | Запросить у core привязанный к реестру publisher receipt |
| `DIRECTORY_JOIN` | Подключиться к непрозрачной цели или отменить подключение |

Обязательную capability добавьте и в `requestedCapabilities`, и в
`requiredCapabilities`. Если она запрещена, аддон отключится до `initialize`.
Отказ необязательной возможности должен выключать только связанную функцию.

## Доступность

```java
PublicDirectoryService directory = context.api().publicDirectory();
ApiResult<PublicDirectoryService.DirectoryAvailability> result =
        directory.availability();
```

| Состояние | Значение |
| --- | --- |
| `AVAILABLE` | Работает совместимый клиентский или выделенный runtime |
| `NO_ACTIVE_SESSION` | Core загружен, но публиковать или подключать пока нечего |
| `STEAM_UNAVAILABLE` | Steam не запустился или текущий runtime потерян |
| `UNSUPPORTED` | В этой среде мост недоступен |

Это снимок состояния, а не постоянная гарантия. Любая следующая операция может
вернуть типизированную ошибку, если сессия успела измениться.

## Публикация активной цели

```java
PublicDirectoryService.DirectoryOrigin origin =
        new PublicDirectoryService.DirectoryOrigin("https://registry.example");
PublicDirectoryService.PublicationTargetRequest request =
        new PublicDirectoryService.PublicationTargetRequest(
                PublicDirectoryService.TargetKind.INTEGRATED_WORLD,
                origin
        );

directory.publicationTarget(request).thenAccept(result -> {
    if (!result.isSuccess() || !result.value().isPresent()) return;
    PublicDirectoryService.PublicationTarget target = result.value().get();
    // В доверенный реестр уходят target.targetRef() и target.generation().
});
```

`TargetKind` бывает `INTEGRATED_WORLD` или `DEDICATED_SERVER`. Ссылка остаётся
непрозрачной и привязана к generation. После закрытия мира, остановки сервера
или начала новой сессии старую запись нужно удалить. Нельзя декодировать её или
считать подтверждённой личностью.

Цель одиночного мира появляется только после выбора зарегистрированного
публичного режима и готовности Steam-лобби. Цель выделенного сервера доступна,
пока защищённый dedicated backend принимает игроков.

## Attestation владельца

Реестр создаёт короткоживущий challenge: origin, challenge ID, nonce, action и
expiry. Аддон передаёт его ядру:

```java
directory.attest(challenge).thenAccept(result -> {
    if (!result.isSuccess() || !result.value().isPresent()) return;
    PublicDirectoryService.AttestationReceipt receipt = result.value().get();
    // Верните ограниченный receipt тому же origin.
});
```

Допустимые action: `PUBLISH_INTEGRATED`, `PUBLISH_DEDICATED`, `UPDATE` и
`DELETE`. Core привязывает подтверждение ко всем полям challenge и возвращает
только receipt. Исходный материал подтверждения не пересекает API.

`DirectoryOrigin` принимает HTTPS без credentials, path, query и fragment.
Обычный HTTP запрещён. Исключение — literal loopback для явно включённой
локальной разработки.

Текущий runtime 0.3.2 намеренно закрыто отклоняет production-attestation по
HTTPS, пока оператор не настроит доверенный verifier реестра. Для локальных
интеграционных тестов есть отдельно включаемый loopback HMAC. Его нельзя
выставлять в локальную сеть или интернет; он не подтверждает личность Steam.

## Подключение к цели каталога

Реестр выдаёт непрозрачную цель и новый одноразовый join handle:

```java
PublicDirectoryService.JoinRequest request =
        new PublicDirectoryService.JoinRequest(
                new PublicDirectoryService.OpaqueTargetRef(targetFromRegistry),
                new PublicDirectoryService.JoinHandleRef(handleFromRegistry)
        );

directory.join(request).thenAccept(result -> {
    if (!result.isSuccess() || !result.value().isPresent()) return;
    PublicDirectoryService.JoinOperation operation = result.value().get();
    currentOperation = operation.id();
});
```

Сохраняйте только локальный `JoinOperationId`. Join handle нельзя использовать
повторно. Читайте `joinSnapshot(id)` с ограниченной частотой интерфейса и
вызывайте `cancel(id)`, когда пользователь закрывает экран. Отмена идемпотентна:
поздний Steam callback не превратит отменённую или неудачную попытку в успех.

| Состояние | Значение |
| --- | --- |
| `IDLE` / `RESOLVING` | Core проверяет запрос |
| `JOINING_STEAM_TARGET` | Steam подключается к лобби или находит цель |
| `AUTHENTICATING` | Идёт защищённое рукопожатие e4steam |
| `CONNECTING_MINECRAFT` | Minecraft открывает локальный мост |
| `ACTIVE` | Нужная сессия активна |
| `CANCELLED` | Пользователь или аддон отменил попытку |
| `STALE` | Цель или generation больше не существует |
| `FULL` | На хосте закончились места |
| `INCOMPATIBLE` | Не совпали Minecraft, протокол или обязательные аддоны |
| `FAILED` | Произошла ограниченная общая ошибка |

`detailCode()` — безопасная категория для локализации, а не текст исключения.

## Публичный режим доступа

Аддон каталога может зарегистрировать `AccessService.AccessModeProvider`. Если
этот режим публикует мир, реализуйте
`AccessService.ConfirmableAccessModeProvider` и верните ключи локализации для
заголовка и текста предупреждения. Публикация начинается только после явного
подтверждения пользователя и успешного запуска мира.

Решение admission policy вызывается после обязательных проверок core. Оно не
может отменить отказ из-за неверного endpoint token, устаревшей generation,
ошибки Steam-аутентификации, несовместимого протокола, бана, replay или лимита
игроков. Отсутствующий provider и его исключения приводят к безопасному отказу.

## Обнаружение загрузчиком

- Fabric и Quilt: entrypoint `e4steam` в `fabric.mod.json`.
- Forge и NeoForge: файл
  `META-INF/services/link.e4steam.api.addon.E4steamAddonEntrypoint`.

Minecraft GUI держите в отдельных source set для загрузчика и версии. Общий
API-код не должен ссылаться на клиентские классы: иначе сломается выделенный
сервер.

## Проверка перед выпуском

1. Подключите API 1.1.0 через `compileOnly`.
2. Убедитесь, что в JAR нет `link/e4steam/api/**`, Steam natives, абсолютных
   путей и секретов.
3. Проверьте отсутствие Steam, capability, конфигурации и несовместимый API без
   crash.
4. Проверьте одноразовые handle, expiry, отмену, stale target, полный хост и
   повторное открытие GUI.
5. Проверьте очистку публикации одиночного мира и выделенного сервера.
6. Ограничьте запросы к реестру по размеру, тайм-ауту, частоте и redirects.
7. Перед заявлением совместимости проведите тест хост/гость с двумя аккаунтами.

Автотесты локального реестра проверяют контракт, но для production всё равно
нужны доверенный attestation verifier и реальный тест двумя пользователями.
