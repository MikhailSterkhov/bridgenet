# BridgeNet / Services / Servers

Servers - Внутренний сервис, отвечающий за учет игровых серверов,
<br>подключенных к системе BridgeNet: регистрацию серверов при рукопожатии,
<br>ведение реестра активных подключений, а также перенаправление
<br>игроков между серверами.

---

## MODEL

Для использования сервиса необходимо использовать модельный
<br>интерфейс `me.moonways.bridgenet.model.service.servers.ServersServiceModel`:

```java

@Inject
private ServersServiceModel serviceModel;
```

Данный интерфейс предоставляет доступ к реестру подключенных
<br>к системе серверов в виде удаленных сущностей `EntityServer`.
<br>Приведем примеры.

Для получения **списка всех зарегистрированных серверов**
<br>мы можем использовать следующий функционал:

```java
List<EntityServer> totalServers = serviceModel.getTotalServers();
```

Для получения списков серверов по их **предназначению**: стандартные
<br>серверы для входа игроков и fallback-серверы, отвечающие за редирект
<br>при падении текущих серверов:

```java
List<EntityServer> defaultServers = serviceModel.getDefaultServers();
List<EntityServer> fallbackServers = serviceModel.getFallbackServers();
```

Для получения **конкретного сервера** по его точному названию или
<br>уникальному ключевому идентификатору мы можем использовать
<br>следующий функционал:

```java
Optional<EntityServer> serverOptional = serviceModel.getServerExact("Lobby-1");

if (!serverOptional.isPresent()) {
    // server is not connected.
}
```

```java
UUID serverKey = server.getUniqueId();
Optional<EntityServer> serverOptional = serviceModel.getServerExact(serverKey);
```

Если точное название неизвестно, поиск можно выполнить по
<br>**примерному** само-идентифицированному названию сервера:

```java
Optional<EntityServer> serverOptional = serviceModel.getServer("Lobby");
```

Для быстрой **проверки наличия** подключенного сервера в системе
<br>мы можем использовать следующий функционал:

```java
boolean hasServer = serviceModel.hasServer("Lobby-1");
```

Полученная сущность `EntityServer` позволяет взаимодействовать
<br>с сервером напрямую - например, **подключить к нему игрока**
<br>или получить общее число онлайна:

```java
EntityServer server = serverOptional.get();

CompletableFuture<Boolean> redirectFuture = server.connectThat(player);
int totalOnline = server.getTotalOnline();
```

а также проверить установленные при рукопожатии **флаги сервера**
<br>через объект `ServerInfo`:

```java
ServerInfo serverInfo = server.getServerInfo();
boolean isLobby = serverInfo.hasFlag(ServerFlag.LOBBY_SERVER);
```

---

## ENDPOINT

Конфигурационные данные, на которых базируется запуск сервиса
<br>под имплементаций эндпоинта:

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7004</bindPort>
    <!-- Service direction name -->
    <name>servers</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.servers.ServersServiceModel</modelPath>
</service>
```

- Реализация эндпоинта лежит в модуле `services/endpoint/servers`;
- Имплементацией основного модельного интерфейса сервиса
  <br>является `me.moonways.bridgenet.endpoint.servers.ServersServiceEndpoint`;
- Реестр подключенных серверов хранится в памяти процесса - в контейнере
  <br>`me.moonways.bridgenet.endpoint.servers.ServersContainer` (синхронизированная Map),
  <br>без обращений к базе данных;
- Регистрация сервера происходит при обработке MTP-сообщения `Handshake`
  <br>в листенере `me.moonways.bridgenet.endpoint.servers.handler.ServersInputMessagesListener`,
  <br>там же обрабатываются сообщения `Redirect` (перенаправление игрока
  <br>на сервер) и `Disconnect` (отключение сервера);
- При регистрации и отключении сервера эндпоинт публикует события
  <br>`ServerHandshakeEvent` и `ServerDisconnectEvent` (пакет `me.moonways.bridgenet.model.event`)
  <br>через `EventService`;
- Обрыв сетевого канала отслеживается листенером `ServersDownstreamListener`
  <br>(по событию `ChannelDownstreamEvent`) - сервер при этом автоматически
  <br>удаляется из реестра;
- Привязка "игрок -> сервер" ведется вспомогательным сервисом
  <br>`me.moonways.bridgenet.endpoint.servers.players.PlayersOnServersConnectionService`,
  <br>также в памяти процесса.
