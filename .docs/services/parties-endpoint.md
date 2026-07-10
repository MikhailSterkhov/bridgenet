# BridgeNet / Services / Parties

Parties - Внутренний сервис, отвечающий за создание и управление
<br>игровыми группами (пати): создание группы с лидером и участниками,
<br>регистрация активных групп, поиск группы по имени игрока и проверка
<br>членства игроков в группах.

---

## MODEL

Для использования сервиса необходимо использовать модельный
<br>интерфейс `me.moonways.bridgenet.model.service.parties.PartiesServiceModel`:

```java

@Inject
private PartiesServiceModel serviceModel;
```

Данный интерфейс предоставляет возможность создавать и регистрировать
<br>игровые группы в виде remote-объектов `me.moonways.bridgenet.model.service.parties.Party`.
<br>Приведем примеры.

Для **создания новой группы** мы можем использовать следующий
<br>функционал (только с лидером или сразу с первыми участниками):

```java
Party party = serviceModel.createParty("Notch");
```

```java
Party party = serviceModel.createParty("Notch", "Herobrine", "jeb_");
```

Для **регистрации** созданной группы в общем реестре сервиса
<br>(и последующего **снятия с регистрации**) мы можем использовать
<br>следующий функционал:

```java
serviceModel.registerParty(party);
```

```java
serviceModel.unregisterParty(party);
```

Для получения **зарегистрированной группы игрока** по его никнейму
<br>мы можем использовать следующий функционал:

```java
Party party = serviceModel.getRegisteredParty("Notch");

if (party == null) {
    // player has no party.
}
```

Для **проверки наличия группы** у игрока и **проверки членства**
<br>игрока в конкретной группе мы можем использовать следующий функционал:

```java
boolean hasParty = serviceModel.hasParty("Notch");
```

```java
boolean isMember = serviceModel.isMemberOf(party, "Herobrine");
```

Для работы с **составом группы** используется контейнер участников
<br>`me.moonways.bridgenet.model.service.parties.PartyMembersContainer`
<br>(расширяет `List<PartyMember>`), полученный из объекта группы:

```java
PartyMembersContainer members = party.getMembersContainer();

PartyMember joined = members.addMember("Herobrine");
PartyMember leaved = members.removeMember("Herobrine");

boolean hasMember = members.hasMemberByName("Herobrine");
PartyMember member = members.getMemberByName("Herobrine");
```

Для работы с **лидером группы** и ее общими параметрами мы можем
<br>использовать следующий функционал:

```java
PartyOwner owner = party.getOwner();

party.setOwner(new PartyOwner("Herobrine", party));

long createdAtSeconds = party.getTimeOfCreated(TimeUnit.SECONDS);
int totalMembersCount = party.getTotalMembersCount();
```

Сервис публикует события жизненного цикла групп -
<br>`me.moonways.bridgenet.model.event.PartyCreateEvent`,
`me.moonways.bridgenet.model.event.PartyRegisterEvent`,
<br>`me.moonways.bridgenet.model.event.PartyUnregisterEvent`,
`me.moonways.bridgenet.model.event.PartyPlayerJoinEvent`
<br>и `me.moonways.bridgenet.model.event.PartyPlayerQuitEvent`,
на которые можно подписаться через `@SubscribeEvent`.

---

## ENDPOINT

Конфигурационные данные, на которых базируется запуск сервиса
<br>под имплементаций эндпоинта:

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7008</bindPort>
    <!-- Service direction name -->
    <name>parties</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.parties.PartiesServiceModel</modelPath>
</service>
```

- Реализация эндпоинта лежит в модуле `services/endpoint/parties`;
- Имплементацией основного модельного интерфейса сервиса
  <br>является `me.moonways.endpoint.parties.PartiesServiceEndpoint`;
- Зарегистрированные группы хранятся только в оперативной памяти процесса -
  <br>в синхронизированном `Set<Party>`, без записи в базу данных;
- Имплементацией remote-объекта группы является `me.moonways.endpoint.parties.PartyStub`,
  <br>а контейнера участников - `me.moonways.endpoint.parties.PartyMembersContainerStub`
  <br>(наследуется от `ArrayList<PartyMember>`);
- При смене лидера через `Party.setOwner(...)` предыдущий лидер
  <br>автоматически переводится в список обычных участников группы;
- Проверка членства `isMemberOf(...)` сравнивает никнеймы без учета регистра
  <br>и учитывает как лидера, так и обычных участников группы;
- При создании, регистрации и снятии группы с регистрации реализация публикует
  <br>события `PartyCreateEvent`, `PartyRegisterEvent` и `PartyUnregisterEvent`
  <br>через `EventService`, а при входе и выходе участников -
  <br>`PartyPlayerJoinEvent` и `PartyPlayerQuitEvent`;
- Для получения объекта игрока при публикации событий входа и выхода
  <br>реализация использует сервис `PlayersServiceModel`.
