# BridgeNet / Services / Permissions

Permissions - Внутриигровой сервис удаленного доступа, имеющий в себе модель
<br>и реализацию индивидуальных прав и групп прав относительно игроков.

---

## MODEL

Для использования сервиса необходимо использовать модельный
<br>интерфейс `me.moonways.bridgenet.model.service.permissions.PermissionsServiceModel`:

```java

@Inject
private PermissionsServiceModel serviceModel;
```

С помощью данного интерфейса можно управлять как индивидуальными
<br>правами пользователей по имени или идентификатору самого пользователя,
<br>либо группами прав:

```java
PermissionsManager permissions = serviceModel.getPermissions();
```

```java
GroupsManager groups = serviceModel.getGroups();
```

---

## ENDPOINT

Конфигурационные данные, на которых базируется запуск сервиса
<br>под имплементаций эндпоинта:

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7011</bindPort>
    <!-- Service direction name -->
    <name>permissions</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.permissions.PermissionsServiceModel</modelPath>
</service>
```

- Реализация эндпоинта лежит в модуле `services/endpoint/permissions`;
- Имплементацией основного модельного интерфейса сервиса
  <br>является `me.moonways.endpoint.permissions.PermissionsServiceEndpoint`;

Реализация использует фреймворк базы данных от Bridgenet.
<br>Наименование используемых таблиц и сущностей

<br>

Examples: `player_permissions` (`me.moonways.endpoint.permissions.entity.EntityPermission`)

| player_id                            | permission      | expired_in    |
|--------------------------------------|-----------------|---------------|
| 756d7e30-d173-4c85-8827-67aad3c258aa | bridgenet.test  | 1715952719209 |
| 04d15ffd-066e-4332-96ad-64670b7c56dd | leveling.max.30 | 0             |

<br>

Examples: `player_groups` (`me.moonways.endpoint.permissions.entity.EntityGroup`)

| player_id                            | group_id | expired_in    |
|--------------------------------------|----------|---------------|
| b9c57b6f-e91f-4e6f-acbe-d876c893a415 | 31       | 0             |
| 9371b0b0-39f4-4a4c-bcb9-5b632118be48 | 5        | 1718458512378 |
