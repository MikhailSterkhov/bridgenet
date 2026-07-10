# Bridgenet / TCP MTP

Message Transfer Protocol (MTP) - Специализированный протокол для общения между
<br>сетевыми соединениями и каналами единой сети Bridgenet.

---

## BUILD

Протокол реализован в отдельном maven-модуле `mtp`, который подключается
<br>в корневом `pom.xml` проекта, а его версия указывается там же в блоке
<br>`properties` (свойство `<mtp.version>1.0</mtp.version>`).
<br>
<br>В качестве сетевого транспорта модуль использует библиотеку **Netty**
<br>(`io.netty:netty-all`, версия `4.1.50.Final`), а из внутренних модулей
<br>системы зависит только от модуля `api`.

При полной сборке проекта (`./bridgenet build` или `./bridgenet jar`,
<br>скрипт `.scripts/project_build.sh`) модуль устанавливается в очереди сборки
<br>сразу после модуля `api`, так как далее он подключается как зависимость
<br>к модулям `rmi`, `client`, `services/model`, `services/endpoint`, `bootstrap` и `testing`.
<br>Отдельным артефактом в директорию '.build' модуль не попадает - его классы
<br>упаковываются внутрь исполняемого файла `bridgenet-server.jar` (maven-shade-plugin
<br>модуля `bootstrap`), который скрипт сборки копирует в '.build'.

Конфигурация протокола хранится в унифицированном ресурсе `mtpconfig.json`
<br>(константа `ResourcesTypes.MTP_CONFIG_JSON` модуля `assembly`) и читается
<br>фабрикой `me.moonways.bridgenet.mtp.connection.BridgenetNetworkConnectionFactory`
<br>в дескриптор `me.moonways.bridgenet.mtp.config.descriptor.NetworkSettingsDescriptor`:

```json
{
  "host": "127.0.0.1",
  "port": 6791,
  "workers": {
    "bossThreads": 3,
    "childThreads": 4
  },
  "security": {
    "algorithm": "RSA",
    "transformation": "RSA/ECB/PKCS1Padding",
    "privateKey": "...",
    "publicKey": "..."
  }
}
```

Блок `security` описывает параметры шифрования передаваемых сообщений
<br>(`me.moonways.bridgenet.mtp.message.encryption.MessageEncryption`).

---

## API

Центральной точкой входа протокола является контроллер
<br>`me.moonways.bridgenet.mtp.BridgenetNetworkController`:

```java

@Inject
private BridgenetNetworkController networkController;
```

Через него выполняется регистрация сообщений и слушателей, а также
<br>проброс входящих сообщений по зарегистрированным обработчикам:

```java
networkController.bindMessages(); // регистрация всех найденных сообщений
networkController.bindMessageListeners(); // регистрация всех найденных слушателей
networkController.register(new GamesInputMessageListener(container)); // регистрация конкретного слушателя
```

### Сообщения

Сообщение протокола - это обычный класс, помеченный аннотацией
<br>`@ClientMessage` (передается от клиента к серверу) и/или `@ServerMessage`
<br>(передается от сервера к клиенту) из пакета `me.moonways.bridgenet.mtp.message.persistence`.
<br>Передаваемые поля помечаются аннотацией `me.moonways.bridgenet.mtp.transfer.ByteTransfer`,
<br>а способ их сериализации определяется провайдерами из пакета
<br>`me.moonways.bridgenet.mtp.transfer.provider`. Реальный пример -
<br>сообщение `me.moonways.bridgenet.model.message.SendTitle`:

```java
@Getter
@ClientMessage
@ServerMessage
@AllArgsConstructor
@NoArgsConstructor(onConstructor_ = @Inject)
public class SendTitle {

    @ByteTransfer(provider = ToUUIDProvider.class)
    private UUID playerId;

    @ByteTransfer
    private String title;
    @ByteTransfer
    private String subtitle;

    @ByteTransfer
    private int fadeIn, stay, fadeOut;
}
```

Помеченные классы автоматически находятся сканером аннотаций и
<br>регистрируются в сервисе `me.moonways.bridgenet.mtp.message.NetworkMessagesService`,
<br>который присваивает каждому сообщению идентификатор и направление
<br>(`me.moonways.bridgenet.mtp.channel.ChannelDirection`).

### Слушатели входящих сообщений

Для обработки входящих сообщений класс слушателя помечается аннотацией
<br>`@InboundMessageListener`, а его методы-обработчики - аннотацией `@SubscribeMessage`.
<br>Метод может принимать как само сообщение, так и контекст
<br>`me.moonways.bridgenet.mtp.message.InboundMessageContext`, через который
<br>можно получить канал отправителя и ответить на сообщение:

```java
@InboundMessageListener
public class GamesInputMessageListener {

    @SubscribeMessage
    public void handle(InboundMessageContext<CreateGame> context) {
        CreateGame message = context.getMessage();
        BridgenetNetworkChannel channel = context.getChannel();

        // ...обработка сообщения...

        context.callback(new CreateGame.Result(gameId, activeId)); // ответ отправителю
    }

    @SubscribeMessage
    public void handle(DeleteGame message) {
        // ...обработка сообщения без контекста...
    }
}
```

### Каналы и отправка сообщений

Транспортировкой сообщений занимаются каналы, реализующие интерфейс
<br>`me.moonways.bridgenet.mtp.channel.BridgenetNetworkChannel`.
<br>Для **отправки** сообщения на подключенный канал мы можем
использовать следующий функционал:

```java
channel.send(new SendTitle(playerId, "Title", "Subtitle", 10, 40, 10));
```

Для отправки сообщения с **ожиданием ответа** мы можем
<br>использовать следующий функционал:

```java
CompletableFuture<Redirect.Result> future =
        channel.sendAwait(Redirect.Result.class, new Redirect(playerUUID, serverKey));

Redirect.Result result = future.join();
```

Также канал позволяет кешировать собственные аттрибуты:

```java
channel.setProperty(EntityServer.CHANNEL_PROPERTY, server);

Optional<EntityServer> serverOptional = channel.getProperty(EntityServer.CHANNEL_PROPERTY);
```

### Подключение клиента

Для создания уже подключенного к серверу Bridgenet клиентского канала
<br>необходимо проинжектить фабрику клиентских соединений:

```java

@Inject
private NetworkClientConnectionFactory clientConnectionFactory;
```

```java
BridgenetNetworkChannel channel = clientConnectionFactory.newRemoteClient();
```

### Поднятие сервера

Серверное и клиентское соединения (`me.moonways.bridgenet.mtp.connection.BridgenetNetworkConnection`)
<br>создаются через фабрику `BridgenetNetworkConnectionFactory`, читающую конфигурацию `mtpconfig.json`.
<br>Пример поднятия сервера из `me.moonways.endpoint.bus.BusServiceEndpoint`:

```java

@Inject
private BridgenetNetworkConnectionFactory networkConnectionFactory;
```

```java
NetworkJsonConfiguration configuration = networkConnectionFactory.getConfiguration();

EventLoopGroup parentWorker = NetworkBootstrapFactory.createEventLoopGroup(configuration.getSettings().getWorkers().getBossThreads());
EventLoopGroup childWorker = NetworkBootstrapFactory.createEventLoopGroup(configuration.getSettings().getWorkers().getChildThreads());

networkConnectionFactory.newServerBuilder()
        .setChannelFactory(NetworkBootstrapFactory.createServerChannelFactory())
        .setChannelInitializer(inboundChannelOptionsHandler)
        .setGroup(parentWorker, childWorker)
        .build()
        .bindSync();
```

---
