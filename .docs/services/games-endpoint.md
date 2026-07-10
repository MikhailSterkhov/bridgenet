# BridgeNet / Services / Games

Games - Внутренний сервис, ведущий реестр мини-игр всей сети: зарегистрированные
<br>игры, игровые серверы, на которых они запущены, активные игровые арены (карты)
<br>и их текущие состояния (статус, количество игроков, наблюдателей и т.д.).
<br>Данные реестра наполняются сообщениями, которые присылают сами игровые
<br>серверы в момент создания, обновления и удаления игровых арен.

---

## MODEL

Для использования сервиса необходимо использовать модельный
<br>интерфейс `me.moonways.bridgenet.model.service.games.GamesServiceModel`:

```java

@Inject
private GamesServiceModel serviceModel;
```

Данный интерфейс предоставляет доступ к списку загруженных игр,
<br>их игровым серверам и активным аренам.
<br>Приведем примеры.

Для получения **игры** по ее названию или уникальному идентификатору
<br>мы можем использовать следующий функционал:

```java
Game game = serviceModel.getGame("BedWars");

if (game == null) {
    // game is not loaded.
}
```

```java
UUID gameId = UUID.fromString("...");
Game game = serviceModel.getGame(gameId);
```

Для получения **списка всех загруженных игр** сети мы можем
<br>использовать следующий функционал:

```java
List<Game> loadedGames = serviceModel.getLoadedGames();
```

Для проверки, является ли произвольный сервер сети **игровым сервером**,
<br>мы можем использовать следующий функционал:

```java
boolean isGame = serviceModel.isGame(server); // server: EntityServer
```

Из полученной игры `Game` мы можем получить список **активных арен** -
<br>всех сразу, по названию карты, или конкретную по идентификатору:

```java
List<ActiveGame> activeGames = game.getActiveGames();
List<ActiveGame> activeGamesOnMap = game.getActiveGamesByMap("Aquarium");

Optional<ActiveGame> activeGameOptional = game.getActiveGame(activeGameId);
```

Для выбора **наилучшей арены для входа игрока** на конкретном
<br>игровом сервере мы можем использовать следующий функционал:

```java
for (GameServer gameServer : game.getLoadedServers()) {
    Optional<ActiveGame> betterGameOptional = gameServer.getBetterGameForJoin();

    if (betterGameOptional.isPresent()) {
        // join player to this active game.
    }
}
```

Текущее **состояние арены** описывается интерфейсом `GameState` и
<br>перечислением `GameStatus` (`IDLE`, `WAIT_PLAYERS`, `PROCESSING`, `INACTIVE`):

```java
GameState state = activeGame.getState();

boolean isProcessing = state.checkStatus(GameStatus.PROCESSING);
boolean canJoin = state.getStatus().canNewPlayerJoin();

int totalPlayers = state.getTotalPlayers(); // players + spectators
```

---

## ENDPOINT

Конфигурационные данные, на которых базируется запуск сервиса
<br>под имплементаций эндпоинта:

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7006</bindPort>
    <!-- Service direction name -->
    <name>games</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.games.GamesServiceModel</modelPath>
</service>
```

- Реализация эндпоинта лежит в модуле `services/endpoint/games`;
- Имплементацией основного модельного интерфейса сервиса
  <br>является `me.moonways.endpoint.games.GamesServiceEndpoint`;
- Реестр игр хранится в памяти процесса - в контейнере
  <br>`me.moonways.endpoint.games.GamesContainer` (синхронизированная `Map` по идентификатору игры),
  <br>без использования базы данных;
- Наполнение реестра происходит через протокольные сообщения `CreateGame`, `UpdateGame`
  <br>и `DeleteGame` (пакет `me.moonways.bridgenet.model.message`), которые обрабатывает
  <br>слушатель `me.moonways.endpoint.games.handler.GamesInputMessageListener`:
  <br>создание арены регистрирует игру и игровой сервер, обновление - перезаписывает `GameState`,
  <br>удаление - каскадно вычищает опустевшие серверы и игры;
- Слушатель событий `me.moonways.endpoint.games.handler.GamesServersDownstreamListener`
  <br>подписан на событие `ServerDisconnectEvent`: при отключении игрового сервера от сети
  <br>он автоматически инициирует удаление всех его активных арен сообщением `DeleteGame`.
