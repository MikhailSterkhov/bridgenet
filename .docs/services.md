# Bridgenet / Services

Services - Вложенный модуль системы Bridgenet, объединяющий все внутренние
<br>сервисы платформы. Состоит из двух сабмодулей:

- `model` - модельные интерфейсы сервисов (пакеты `me.moonways.bridgenet.model.service.<name>`),
  <br>доступные всем остальным модулям системы;
- `endpoint` - имплементации-эндпоинты этих интерфейсов (как правило, пакеты `me.moonways.endpoint.<name>`;
  <br>исключения - `friends`: `me.moonways.endpoint.friend` и `servers`: `me.moonways.bridgenet.endpoint.servers`),
  <br>каждая из которых собирается в отдельную директорию сборки и биндится
  <br>по протоколу RMI на собственный порт согласно конфигурации `assembly/etc/remote_services.xml`.

---

## BUILD

Модуль `services` объявлен в корневом `pom.xml` проекта (packaging `pom`)
<br>и содержит два maven-сабмодуля: `model` и `endpoint`.
<br>Версии управляются через properties корневого `pom.xml`:
<br>`model.version` (1.1), `endpoint.version` (1.2) и индивидуальные
<br>`service.<name>.version` (1.0) для каждого из четырнадцати сервисов.

Зависимости внутри проекта (см. `services/pom.xml` и pom-файлы сабмодулей):
- общие для всего модуля - `api` и `jdbc-provider` (scope `provided`);
- `model` дополнительно зависит от `rmi` и `mtp` (scope `provided`);
- `endpoint` зависит от `rmi`, `mtp` (scope `provided`) и от `model` (scope `compile`),
  <br>а также собирает каждый эндпоинт в fat-jar при помощи `maven-shade-plugin`.

Сборка эндпоинтов запускается скриптом `./bridgenet endpoints` (сокращенно `-e`)
<br>или в составе полной сборки `./bridgenet build`:
- `.scripts/services_build.sh` сначала выполняет `mvn clean install` для `services/model`,
  <br>затем компилирует каждый эндпоинт и копирует собранный jar
  <br>в директорию сборки `.build/services/<name>/`;
- `.scripts/services_assemble.sh` докладывает туда же конфигурации из `.scripts/etc`,
  <br>в том числе сгенерированный `endpoint.json`, в котором плейсхолдер `%endpoint%`
  <br>заменяется на имя конкретного сервиса.

При запуске сервера класс `me.moonways.bridgenet.rmi.endpoint.EndpointLoader`
<br>сканирует директорию `.build/services` (при локальном запуске) или `services`
<br>(при dedicated-запуске), после чего `EndpointRunner` загружает jar каждого
<br>эндпоинта отдельным `URLClassLoader`, находит в нем имплементацию модельного
<br>интерфейса и биндит ее на RMI-порт, указанный в `assembly/etc/remote_services.xml`.

---

## API

Каждый сервис описывается модельным интерфейсом, расширяющим
<br>`me.moonways.bridgenet.rmi.service.RemoteService`. Все методы модельных
<br>интерфейсов объявляют `throws RemoteException`, так как вызовы
<br>делегируются удаленной имплементации по протоколу RMI.

Полный список сервисов и их RMI-портов (согласно `assembly/etc/remote_services.xml`):

| Сервис        | Порт | Модельный интерфейс                                                     | Документация                                          |
|---------------|------|-------------------------------------------------------------------------|-------------------------------------------------------|
| `auth`        | 7000 | `me.moonways.bridgenet.model.service.auth.AuthServiceModel`             | [auth-endpoint.md](services/auth-endpoint.md)         |
| `gui`         | 7001 | `me.moonways.bridgenet.model.service.gui.GuiServiceModel`               | [gui-endpoint.md](services/gui-endpoint.md)           |
| `bus`         | 7002 | `me.moonways.bridgenet.model.service.bus.BusServiceModel`               | [bus-endpoint.md](services/bus-endpoint.md)           |
| `players`     | 7003 | `me.moonways.bridgenet.model.service.players.PlayersServiceModel`       | [players-endpoint.md](services/players-endpoint.md)   |
| `servers`     | 7004 | `me.moonways.bridgenet.model.service.servers.ServersServiceModel`       | [servers-endpoint.md](services/servers-endpoint.md)   |
| `friends`     | 7005 | `me.moonways.bridgenet.model.service.friends.FriendsServiceModel`       | [friends-endpoint.md](services/friends-endpoint.md)   |
| `games`       | 7006 | `me.moonways.bridgenet.model.service.games.GamesServiceModel`           | [games-endpoint.md](services/games-endpoint.md)       |
| `guilds`      | 7007 | `me.moonways.bridgenet.model.service.guilds.GuildsServiceModel`         | [guilds-endpoint.md](services/guilds-endpoint.md)     |
| `parties`     | 7008 | `me.moonways.bridgenet.model.service.parties.PartiesServiceModel`       | [parties-endpoint.md](services/parties-endpoint.md)   |
| `reports`     | 7009 | `me.moonways.bridgenet.model.service.reports.ReportsServiceModel`       | [reports-endpoint.md](services/reports-endpoint.md)   |
| `settings`    | 7010 | `me.moonways.bridgenet.model.service.settings.PlayerSettingsServiceModel` | [settings-endpoint.md](services/settings-endpoint.md) |
| `permissions` | 7011 | `me.moonways.bridgenet.model.service.permissions.PermissionsServiceModel` | [permissions-endpoint.md](services/permissions-endpoint.md) |
| `language`    | 7012 | `me.moonways.bridgenet.model.service.language.LanguageServiceModel`     | [language-endpoint.md](services/language-endpoint.md) |
| `mojang`      | 7013 | `me.moonways.bridgenet.model.service.mojang.MojangServiceModel`         | [mojang-endpoint.md](services/mojang-endpoint.md)     |

Имплементация каждого эндпоинта лежит в модуле `services/endpoint/<name>`,
<br>например `me.moonways.endpoint.mojang.MojangServiceEndpoint` для сервиса `mojang`.

Для использования любого сервиса достаточно проинжектить его модельный
<br>интерфейс - удаленную имплементацию система подставит автоматически:

```java

@Inject
private PlayersServiceModel playersServiceModel;
```

Для получения **онлайн-игрока** и **общего онлайна** мы можем
<br>использовать следующий функционал:

```java
PlayerStore store = playersServiceModel.store();
Optional<Player> playerOptional = store.get("Notch");

if (!playerOptional.isPresent()) {
    // player is not online.
}
```

```java
int totalOnline = playersServiceModel.getTotalOnline();
```

Для получения **зарегистрированного сервера** по его названию мы можем
<br>использовать следующий функционал:

```java

@Inject
private ServersServiceModel serversServiceModel;
```

```java
Optional<EntityServer> serverOptional = serversServiceModel.getServerExact("Lobby-1");

if (!serverOptional.isPresent()) {
    // server is not connected.
}
```

Для получения **списка друзей** игрока мы можем использовать
<br>следующий функционал:

```java

@Inject
private FriendsServiceModel friendsServiceModel;
```

```java
FriendsList friendsList = friendsServiceModel.getFriends("Notch");
```

Подробная документация по каждому из сервисов доступна по ссылкам
<br>из таблицы выше.

---
