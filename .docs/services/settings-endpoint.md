# BridgeNet / Services / Settings

Settings - Внутриигровой сервис удаленного доступа, имеющий в себе модель
<br>и реализацию индивидуальных настроек персоны игрока.

---

## MODEL

Для использования сервиса необходимо использовать модельный
<br>интерфейс `me.moonways.bridgenet.model.service.settings.PlayerSettingsServiceModel`:

```java

@Inject
private PlayerSettingsServiceModel serviceModel;
```

### Интерфейс представляет из себя следующее описание:

```java
public interface PlayerSettingsServiceModel extends RemoteService {

    /**
     * Получить список идентификаторов всех возможных
     * пользовательских настроек
     */
    Collection<SettingID<?>> getTotalSettings() throws RemoteException;

    /**
     * Получить параметры пользовательской настройки по
     * уникальному идентификатору пользователя.
     *
     * @param playerId - уникальный идентификатор пользователя.
     * @param id - идентификатор пользовательской настройки.
     */
    <T> Setting<T> getSetting(UUID playerId, SettingID<T> id) throws RemoteException;

    /**
     * Получить параметры пользовательской настройки по
     * имени пользователя.
     *
     * @param playerName - имя пользователя.
     * @param id - идентификатор пользовательской настройки.
     */
    <T> Setting<T> getSetting(String playerName, SettingID<T> id) throws RemoteException;
}
```

---

## ENDPOINT

Конфигурационные данные, на которых базируется запуск сервиса
<br>под имплементаций эндпоинта:

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7010</bindPort>
    <!-- Service direction name -->
    <name>settings</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.settings.PlayerSettingsServiceModel</modelPath>
</service>
```

- Реализация эндпоинта лежит в модуле `services/endpoint/settings`;
- Имплементацией основного модельного интерфейса сервиса
  <br>является `me.moonways.endpoint.settings.PlayerSettingsServiceEndpoint`;

Реализация использует фреймворк базы данных от Bridgenet.
<br>Наименование используемых таблиц и сущностей

Examples: `players_settings` (`me.moonways.endpoint.settings.EntitySetting`)

| player_id                            | setting_id                           | setting_value                                                                       |
|--------------------------------------|--------------------------------------|-------------------------------------------------------------------------------------|
| 3c517d88-8b6f-445b-915c-b47af5140e76 | dc725c4f-93e7-30c5-8db3-d2cf7899657c | {"source":true,"classname":"java.lang.Boolean"}                                     |
| 3c517d88-8b6f-445b-915c-b47af5140e76 | 891cd11e-a327-3b37-9b77-5581c0567b31 | {"source":"GREEN","classname":"me.moonways.bridgenet.api.minecraft.ChatColor"} |
