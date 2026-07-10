# Bridgenet / Client

Client - Вложенный модуль системы Bridgenet, реализующий клиентские подключения
<br>игровых серверов (Spigot) и прокси-серверов (Velocity) к единому серверу Bridgenet.
<br>Модуль предоставляет абстракцию коннектора, исполняющего рукопожатие с сервером,
<br>синхронизацию игроков, игр и команд через протокол MTP, а также готовые плагины-обертки
<br>для платформ Spigot и Velocity.

---

## BUILD

Модуль `client` подключается как maven-модуль в корневом `pom.xml` проекта
<br>(`<module>client</module>`) и наследует его версию (`1.2`). Версии вложенных
<br>сабмодулей задаются в properties корневого `pom.xml`:

```xml
<client.api.version>1.2</client.api.version>
<client.api.minecraft.version>1.2</client.api.minecraft.version>
<client.spigot.version>1.2</client.spigot.version>
<client.velocity.version>1.2</client.velocity.version>
```

Внутренняя структура модуля:

- `client-api` - общая клиентская библиотека:
    - `root` (артефакт `client-api-root`) - ядро коннектора: `BridgenetClient`, `BridgenetServerSync`, `BridgenetGamesSync`, DTO-модели;
    - `minecraft/cloudnet` (артефакт `client-api-cloudnet-wrapper`) - обертка над CloudNet для получения данных текущего устройства;
    - `minecraft/server` (артефакт `client-api-minecraft-server`) - коннектор игрового сервера `MinecraftServerConnector`;
    - `minecraft/proxy` (артефакт `client-api-minecraft-proxy`) - коннектор прокси-сервера;
- `spigot` (артефакт `spigot-wrapper`) - готовый плагин для Spigot (`plugin.yml`: `BridgeNetSync`);
- `velocity` (артефакт `velocity-wrapper`) - готовый плагин для Velocity (`@Plugin(id = "velocity-connector")`).

Модуль зависит от внутренних модулей системы Bridgenet, объявленных
<br>в его `pom.xml`: `mtp`, `model`, `rmi`, `jdbc-provider` и `profiler` -
<br>версии подтягиваются из properties корневого `pom.xml`.

В общей сборке проекта модуль участвует через скрипт `.scripts/project_build.sh`
<br>(запускается командами `./bridgenet jar` или `./bridgenet build`), где `client`
<br>стоит в очереди сборки после модуля `bootstrap`.
<br>
<br>В корневую директорию сборки '.build' попадают только `bridgenet-server.jar` и ресурсы 'etc' -
<br>готовые же jar-файлы плагинов выгружаются в директорию 'client/.build' (настройка
<br>`outputDirectory` у `maven-jar-plugin`) под именами из `<finalName>`:
<br>`SpigotBridgenetSyncPlugin` и `VelocityBridgenetSyncPlugin`.
<br>При этом `maven-shade-plugin` упаковывает все compile-зависимости внутрь jar,
<br>а зависимости самих платформ (`spigot-api`, `velocity-api`) объявлены как `provided`.
<br>Итоговые jar устанавливаются в директорию 'plugins' игрового сервера или прокси.

---

## API

Основной точкой входа модуля является абстрактный класс
<br>`me.moonways.bridgenet.client.api.BridgenetClient`. Для его использования
<br>достаточно реализовать единственный абстрактный метод `createClientInfo()`,
<br>описывающий текущее подключаемое устройство:

```java
public final class MyServerConnector extends BridgenetClient {

    @Override
    protected ClientDto createClientInfo() {
        return ClientDto.builder()
                .name("game-1")
                .host("127.0.0.1")
                .port(25565)
                .build();
    }
}
```

Подключение к единому серверу Bridgenet и полное отключение от него
<br>исполняются методами `start()` и `shutdown()`:

```java
connector.start(); // подключение + инициализация инжектора.
```

```java
connector.shutdown(); // разрыв соединения + деинициализация.
```

Коннектор предоставляет переопределяемые функции обратного вызова -
<br>`onConnected(BridgenetNetworkChannel)`, `onHandshake(Handshake.Result)`
<br>и `onConnectionClosed()`. Например, обработка результата рукопожатия:

```java
@Override
public void onHandshake(Handshake.Result result) {
    result.onSuccess(() -> log.info("Registered with id {}", result.getKey()));
    result.onFailure(() -> log.info("Handshake failed"));
}
```

Готовыми реализациями коннектора являются
<br>`me.moonways.bridgenet.client.api.minecraft.server.MinecraftServerConnector`
<br>(используется плагином `me.moonways.bridgenet.client.spigot.BridgenetSpigotPlugin`)
<br>и `me.moonways.bridgenet.client.velocity.BridgenetVelocityPlugin`.

После успешного запуска коннектора его сервисы синхронизации биндятся
<br>в систему инжектов Bridgenet, и получить их можно через аннотацию
<br>`me.moonways.bridgenet.api.inject.Inject`:

```java

@Inject
private BridgenetServerSync bridgenet;
```

Для **отправки текстового сообщения** пользователю через сеть единого
<br>сервера Bridgenet мы можем использовать следующий функционал:

```java
bridgenet.exportUserMessageSend(SendMessage.ChatType.CHAT, playerId, "Hello!");
```

```java
bridgenet.exportUserTitleSend(playerId, TitleDto.builder()
        .title("Victory!")
        .subtitle("You won the game")
        .fadeIn(10).stay(60).fadeOut(10)
        .build());
```

Для **переподключения игрока** на другой подключенный сервер (или на текущий)
<br>мы можем использовать следующий функционал:

```java
bridgenet.exportUserRedirect(playerId, serverId);
```

```java
boolean success = bridgenet.exportUserRedirectWithResult(playerId, serverId);
```

```java
bridgenet.exportUserRedirectToHere(playerId);
```

Для **делегирования исполнения команды** на единый сервер Bridgenet и
<br>получения списка зарегистрированных в нем команд мы можем использовать
<br>следующий функционал:

```java
boolean isExecuted = bridgenet.exportCommandSend(userDto, "friend add Notch");
```

```java
List<String> commandsList = bridgenet.lookupServerCommandsList();
```

Для **синхронизации игр** (арен) с единым сервером Bridgenet используется
<br>отдельный сервис `me.moonways.bridgenet.client.api.BridgenetGamesSync`:

```java

@Inject
private BridgenetGamesSync gamesSync;
```

Создание активной игры (повторное создание без удаления
<br>предыдущей вызовет `IllegalStateException`):

```java
gamesSync.gameCreate(GameDto.builder()
        .name("BedWars")
        .map("Aquarium")
        .maxPlayers(16)
        .playersInTeam(2)
        .build());
```

Обновление состояния текущей активной игры исполняется функцией
<br>обновления над кешированным `GameStateDto`:

```java
gamesSync.gameUpdate(state -> state.toBuilder()
        .status(GameStatus.PROCESSING)
        .players(14)
        .spectators(2)
        .build());
```

Удаление и финализация текущей активной игры:

```java
gamesSync.gameDelete();
```

---
