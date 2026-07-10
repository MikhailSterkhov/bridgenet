# BridgeNet / Services / Bus

Bus - Внутренний сервис, поднимающий единую сетевую шину сообщений BridgeNet
<br>поверх протокола MTP: при инициализации он запускает TCP-сервер, через который
<br>внешние устройства (прокси, игровые серверы, пользователи) обмениваются
<br>сообщениями с единым сервером BridgeNet, а также активирует привязку
<br>всех зарегистрированных в системе сообщений и их слушателей.

---

## MODEL

Для использования сервиса необходимо использовать модельный
<br>интерфейс `me.moonways.bridgenet.model.service.bus.BusServiceModel`:

```java

@Inject
private BusServiceModel serviceModel;
```

Данный интерфейс является маркерным - он не содержит собственных методов
<br>и наследует `me.moonways.bridgenet.rmi.service.RemoteService`, регистрируя
<br>сервис шины в общем реестре RMI-сервисов системы. Вся прикладная работа
<br>с шиной ведется через сообщения сети MTP и константы модельного пакета.
<br>Приведем примеры.

Для формирования и чтения свойств **рукопожатия** (handshake) модельный пакет
<br>предоставляет константы `me.moonways.bridgenet.model.service.bus.HandshakePropertiesConst`:

```java
Properties properties = new Properties();

properties.setProperty(HandshakePropertiesConst.SERVER_NAME, "arcade-1");
properties.setProperty(HandshakePropertiesConst.SERVER_ADDRESS_HOST, "127.0.0.1");
properties.setProperty(HandshakePropertiesConst.SERVER_ADDRESS_PORT, "25565");
```

На клиентской стороне (модуль `client/client-api`) эти же свойства заполняются
<br>автоматически методом `Properties toProperties()` классов
<br>`me.moonways.bridgenet.client.api.data.ClientDto` и `me.moonways.bridgenet.client.api.data.UserDto`:

```java
ClientDto description = ClientDto.builder()
        .name("arcade-1")
        .host("127.0.0.1")
        .port(25565)
        .build();

Properties properties = description.toProperties();
```

Для выполнения **рукопожатия устройства** с единым сервером через шину
<br>мы можем использовать функционал `me.moonways.bridgenet.client.api.BridgenetServerSync`
<br>(доступен из `BridgenetClient` через `getBridgenetServerSync()`):

```java
Handshake.Result result = bridgenet.exportClientHandshake(description);

result.onSuccess(() -> {
    // handshake confirmed.
});
result.onFailure(() -> {
    // handshake rejected.
});

UUID clientId = result.getKey();
```

Для выполнения **рукопожатия пользователя** мы можем использовать
<br>следующий функционал:

```java
boolean isSuccess = bridgenet.exportUserHandshake(userDescription);
```

Для чтения свойств рукопожатия **на стороне единого сервера** - в подписчике
<br>сообщения `me.moonways.bridgenet.model.message.Handshake` мы можем
<br>использовать следующий функционал:

```java
Properties properties = handshake.getProperties();
String userUuidProperty = properties.getProperty(HandshakePropertiesConst.USER_UUID);
```

Для запроса у единого сервера **списка зарегистрированных команд**
<br>(этот запрос обрабатывает именно эндпоинт сервиса bus - сообщение
<br>`me.moonways.bridgenet.model.message.GetCommands`) мы можем использовать
<br>следующий функционал:

```java
List<String> commandsList = bridgenet.lookupServerCommandsList();
```

---

## ENDPOINT

Конфигурационные данные, на которых базируется запуск сервиса
<br>под имплементаций эндпоинта:

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7002</bindPort>
    <!-- Service direction name -->
    <name>bus</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.bus.BusServiceModel</modelPath>
</service>
```

- Реализация эндпоинта лежит в модуле `services/endpoint/bus`;
- Имплементацией основного модельного интерфейса сервиса
  <br>является `me.moonways.endpoint.bus.BusServiceEndpoint`;
- При инициализации эндпоинт выполняет привязку всех сообщений и их слушателей
  <br>через `me.moonways.bridgenet.mtp.BridgenetNetworkController`
  <br>(методы `bindMessages()` и `bindMessageListeners()`);
- Эндпоинт поднимает TCP-сервер сети MTP на Netty при помощи
  <br>`me.moonways.bridgenet.mtp.connection.BridgenetNetworkConnectionFactory`:
  <br>количество потоков boss/child групп берется из `NetworkJsonConfiguration`,
  <br>а направление канала выставляется в `ChannelDirection.TO_CLIENT` через
  <br>`me.moonways.bridgenet.mtp.inbound.InboundChannelOptionsHandler`;
- Эндпоинт регистрирует слушателя `me.moonways.endpoint.bus.handler.GetCommandsMessageHandler`,
  <br>который в ответ на сообщение `GetCommands` отправляет `GetCommands.Result`
  <br>со списком команд, зарегистрированных в `me.moonways.bridgenet.api.command.CommandRegistry`.
