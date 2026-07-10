# Bridgenet / API / Events

Events - Внутренняя шина событий системы Bridgenet, входящая в состав
<br>maven-модуля `api` (пакет `me.moonways.bridgenet.api.event`) и доступная
<br>всем модулям системы. Позволяет объявлять собственные события, вызывать
<br>их из любой точки системы и обрабатывать слушателями или одноразовыми подписками.

---

## API

Основные типы и аннотации шины событий:

- `me.moonways.bridgenet.api.event.Event` - маркерный интерфейс, реализации
  <br>которого являются вызываемым событием системы;
- `me.moonways.bridgenet.api.event.AsyncEvent` - наследник `Event`; события
  <br>данного типа автоматически обрабатываются в асинхронном потоке;
- `me.moonways.bridgenet.api.event.cancellation.Cancellable` - события,
  <br>реализующие данный интерфейс, могут быть отменены в процессе обработки
  <br>(`isCancelled()`, `makeCancelled()`, `makeNotCancelled()`);
- `me.moonways.bridgenet.api.event.InboundEventListener` - аннотация над
  <br>классом-слушателем; такие классы автоматически обнаруживаются сканером
  <br>аннотаций и регистрируются в шине при инициализации `EventService`;
- `me.moonways.bridgenet.api.event.SubscribeEvent` - аннотация над
  <br>методом-обработчиком внутри слушателя; метод обязан принимать ровно один
  <br>параметр - тип обрабатываемого события. Опциональный параметр `priority`
  <br>(enum `EventPriority`: `LOWEST`, `LOW`, `NORMAL`, `HIGH`, `HIGHEST`;
  <br>по умолчанию `NORMAL`) определяет порядок вызова обработчиков -
  <br>от `HIGHEST` к `LOWEST`;
- `me.moonways.bridgenet.api.event.EventService` - основной сервис шины:
  <br>вызов событий (`fireEvent`), ручная регистрация слушателей
  <br>(`registerListener` / `unregisterListener`) и управление подписками
  <br>(`subscribe` / `unsubscribe`);
- `me.moonways.bridgenet.api.event.EventFuture` - результат вызова `fireEvent`;
  <br>позволяет отреагировать на завершение обработки события (`follow`)
  <br>и ограничить время ожидания обработки (`setTimeout`);
- `me.moonways.bridgenet.api.event.subscribe.EventSubscribeBuilder` - билдер
  <br>программных подписок `EventSubscription`, в том числе с ограниченным
  <br>временем жизни (`expiration`).

Готовые события внутренних сервисов системы объявлены в пакете
<br>`me.moonways.bridgenet.model.event` (например, `FriendAddEvent`,
<br>`PlayerDisconnectEvent`, `PartyCreateEvent`, `ReportCreateEvent` и т.д.).

---

## USAGE

Для работы с шиной событий необходимо проинжектить основной
<br>сервис этого модуля:

```java

@Inject
private EventService eventService;
```

Для **объявления собственного события** достаточно реализовать маркерный
<br>интерфейс `Event`. Пример реального события системы -
<br>`me.moonways.bridgenet.model.event.PlayerDisconnectEvent`:

```java
@Getter
@Builder
@ToString
@EqualsAndHashCode
public class PlayerDisconnectEvent implements Event {

    private final Player player;
}
```

Если событие реализует интерфейс `AsyncEvent`, то его обработчики будут
<br>вызваны в асинхронном потоке. Асинхронное событие не может одновременно
<br>реализовывать `Cancellable` - в этом случае шина ответит
исключением `EventException`.

Для **вызова события** мы можем использовать следующий функционал
<br>(реальный пример из `me.moonways.endpoint.friend.FriendsListStub`):

```java
eventService.fireEvent(
        FriendAddEvent.builder()
                .updatedFriendsList(this)
                .player(playersModel.store().getOffline(playerUUID))
                .friend(playersModel.store().getOffline(uuid))
                .build());
```

Метод `fireEvent` возвращает `EventFuture` - с его помощью мы можем выполнить
<br>действие после того, как событие будет обработано всеми слушателями,
<br>а также ограничить время ожидания обработки:

```java
eventService.fireEvent(event)
        .setTimeout(1000L)
        .follow(completed -> log.debug("Event was completely handled: {}", completed));
```

Для **обработки событий** используется класс-слушатель, помеченный аннотацией
<br>`@InboundEventListener`, с методами-обработчиками под аннотацией `@SubscribeEvent`
<br>(реальный пример - `me.moonways.bridgenet.endpoint.servers.players.PlayerDisconnectListener`):

```java
@InboundEventListener
public class PlayerDisconnectListener {

    @Inject
    private PlayersOnServersConnectionService playersOnServersConnectionService;

    @SubscribeEvent
    public void handle(PlayerDisconnectEvent event) throws RemoteException {
        playersOnServersConnectionService.delete(event.getPlayer().getId());
    }
}
```

Такие слушатели регистрируются в шине автоматически при инициализации
<br>сервиса `EventService`, а при регистрации в них дополнительно инжектятся
<br>зависимости (поля под `@Inject`). Тип обрабатываемого события определяется
<br>по единственному параметру метода-обработчика; слушатель без единого
<br>метода под `@SubscribeEvent` зарегистрирован не будет (`EventException`).

Для управления **порядком вызова** обработчиков одного события мы можем
<br>использовать параметр `priority`:

```java
@SubscribeEvent(priority = EventPriority.HIGHEST)
public void handle(PlayerDisconnectEvent event) {
    // will be executed before NORMAL and LOWEST handlers.
}
```

Также слушателя можно зарегистрировать и снять с регистрации **вручную**:

```java
eventService.registerListener(new PlayerDisconnectListener());
```

```java
eventService.unregisterListener(PlayerDisconnectListener.class);
```

Для **программной подписки** на событие (без объявления класса-слушателя)
<br>мы можем использовать билдер `EventSubscribeBuilder`
<br>(по образцу теста `me.moonways.bridgenet.test.api.event.EventsSubscribingTest`):

```java
EventSubscription<FriendAddEvent> subscription =
        EventSubscribeBuilder.newBuilder(FriendAddEvent.class)
                .expiration(30, TimeUnit.SECONDS)
                .follow(event -> log.info("New friendship: {}", event))
                .build();

eventService.subscribe(subscription);
```

Подписка с указанным `expiration` автоматически снимается по истечении
<br>таймаута; также ее можно снять вручную:

```java
eventService.unsubscribe(subscription);
```
