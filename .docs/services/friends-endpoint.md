# BridgeNet / Services / Friends

Friends - Внутренний сервис, отвечающий за хранение и управление
<br>списками друзей игроков: добавление и удаление друзей, проверка
<br>наличия дружбы и получение полного списка друзей игрока.

---

## MODEL

Для использования сервиса необходимо использовать модельный
<br>интерфейс `me.moonways.bridgenet.model.service.friends.FriendsServiceModel`:

```java

@Inject
private FriendsServiceModel serviceModel;
```

Данный интерфейс предоставляет доступ к спискам друзей игроков
<br>в виде обособленного remote-объекта `me.moonways.bridgenet.model.service.friends.FriendsList`.
<br>Приведем примеры.

Для получения **списка друзей игрока** мы можем использовать
<br>следующий функционал (по идентификатору или по никнейму):

```java
FriendsList friendsList = serviceModel.getFriends(playerUUID);
```

```java
FriendsList friendsList = serviceModel.getFriends("Notch");
```

Для **добавления нового друга** в полученный список мы можем
<br>использовать следующий функционал:

```java
boolean added = friendsList.addFriend(friendUUID);

if (!added) {
    // player is already a friend.
}
```

```java
boolean added = friendsList.addFriend("Notch");
```

Для **удаления друга** из списка мы можем использовать
<br>следующий функционал:

```java
boolean removed = friendsList.removeFriend(friendUUID);

if (!removed) {
    // player was not a friend.
}
```

```java
boolean removed = friendsList.removeFriend("Notch");
```

Для **проверки наличия дружбы** между игроками мы можем
<br>использовать следующий функционал:

```java
boolean hasFriend = friendsList.hasFriend(friendUUID);
```

```java
boolean hasFriend = friendsList.hasFriend("Notch");
```

Для получения **полного перечня друзей** игрока мы можем
<br>использовать следующий функционал:

```java
Set<UUID> friendsIDs = friendsList.getFriendsIDs();
```

```java
Set<String> friendsNames = friendsList.getFriendsNames();
```

При каждом успешном добавлении или удалении друга сервис публикует
<br>события `me.moonways.bridgenet.model.event.FriendAddEvent` и
`me.moonways.bridgenet.model.event.FriendRemoveEvent`,
<br>на которые можно подписаться через `@SubscribeEvent`.

---

## ENDPOINT

Конфигурационные данные, на которых базируется запуск сервиса
<br>под имплементаций эндпоинта:

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7005</bindPort>
    <!-- Service direction name -->
    <name>friends</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.friends.FriendsServiceModel</modelPath>
</service>
```

- Реализация эндпоинта лежит в модуле `services/endpoint/friends`;
- Имплементацией основного модельного интерфейса сервиса
  <br>является `me.moonways.endpoint.friend.FriendsServiceEndpoint`;
- Списки друзей хранятся в базе данных при помощи модуля `jdbc`:
  <br>сущность `me.moonways.endpoint.friend.EntityFriend` (таблица `friends`,
  <br>колонки `player_id` и `friend_id`), доступ к которой осуществляется
  <br>через `EntityRepository`, полученный из `EntityRepositoryFactory`;
- Каждая пара друзей записывается в базу данных в двух направлениях
  <br>(прямая и обратная запись), поэтому дружба всегда взаимна;
- Загруженные списки друзей кешируются в Guava `Cache` по идентификатору
  <br>игрока со сбросом спустя 1 час после последнего обращения;
- Для преобразования никнеймов игроков в идентификаторы и обратно
  <br>реализация использует сервис `PlayersServiceModel`;
- При добавлении и удалении друга реализация публикует события
  <br>`FriendAddEvent` и `FriendRemoveEvent` через `EventService`.
