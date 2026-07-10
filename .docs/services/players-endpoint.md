# BridgeNet / Services / Players

Players - Внутриигровой сервис удаленного доступа, имеющий в себе модель
<br>поведения игровых пользователей в системе, а также модель управления
<br>их функциональностью и системой игрового уровня.

---

## MODEL

Для использования сервиса необходимо использовать модельный
<br>интерфейс `me.moonways.bridgenet.model.service.players.PlayersServiceModel`:

```java

@Inject
private PlayersServiceModel serviceModel;
```

Модельный сервис позволяет управлять математическими вычислениями
<br>пользовательского уровня и игрового опыта:

```java
PlayerLeveling playerLeveling = serviceModel.leveling();

int level = 2;
int totalExperience = playerLeveling.totalExperience(level); // 10.000
int experienceToNext = playerLeveling.experienceToNextLevel(level); // 12.500

// etc.
```

А также управлять _OFFLINE_ и _ONLINE_ реализациями игроков:

```java
PlayerStore store = serviceModel.store();

Optional<Player> onlinePlayer = store.get("bridgenet_user");
OfflinePlayer offlinePlayer = store.getOffline("bridgenet_user");
```

---

## ENDPOINT

Конфигурационные данные, на которых базируется запуск сервиса
<br>под имплементаций эндпоинта:

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7003</bindPort>
    <!-- Service direction name -->
    <name>players</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.players.PlayersServiceModel</modelPath>
</service>
```

- Реализация эндпоинта лежит в модуле `services/endpoint/players`;
- Имплементацией основного модельного интерфейса сервиса
  <br>является `me.moonways.endpoint.players.PlayersServiceEndpoint`;

Реализация использует фреймворк базы данных от Bridgenet.
<br>Наименование используемых таблиц и сущностей

<br>

Examples: `player_namespaces` (`me.moonways.endpoint.players.database.EntityNamespace`)

| id | uuid                                 | name           |
|----|--------------------------------------|----------------|
| 1  | 756d7e30-d173-4c85-8827-67aad3c258aa | itzstonlex     |
| 2  | 04d15ffd-066e-4332-96ad-64670b7c56dd | bridgenet_user |

<br>

Examples: `players` (`me.moonways.endpoint.players.database.EntityPlayer`)

| namespace | description                                                                                                                                                                                         |
|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1         | {"namespace":{"uuid":"756d7e30-d173-4c85-8827-67aad3c258aa","name":"itzstonlex"},"lastLoggedProxyId":"1d13b8eb-9489-4ac5-8218-9d72ab926cd5","lastLoggedTime":1716395677477,"totalExperience":0}     |
| 2         | {"namespace":{"uuid":"04d15ffd-066e-4332-96ad-64670b7c56dd","name":"bridgenet_user"},"lastLoggedProxyId":"1d13b8eb-9489-4ac5-8218-9d72ab926cd5","lastLoggedTime":1716395755504,"totalExperience":0} |
