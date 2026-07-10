# BridgeNet / Services / Guilds

Guilds - Внутренний сервис, зарезервированный под функционал гильдий
<br>(игровых кланов) платформы BridgeNet. На текущий момент сервис
<br>представляет собой каркас: он полностью зарегистрирован в инфраструктуре
<br>(maven-модуль, RMI-порт, конфигурация эндпоинта), однако публичный API
<br>модельного интерфейса еще не опубликован и находится в разработке.

---

## MODEL

Для использования сервиса необходимо использовать модельный
<br>интерфейс `me.moonways.bridgenet.model.service.guilds.GuildsServiceModel`:

```java

@Inject
private GuildsServiceModel serviceModel;
```

Как и все модельные интерфейсы внутренних сервисов, он расширяет
<br>`me.moonways.bridgenet.rmi.service.RemoteService`, поэтому все его будущие
<br>методы будут делегироваться удаленной имплементации по протоколу RMI.

На текущий момент интерфейс **не объявляет ни одного метода** -
<br>это зарезервированная точка расширения под API гильдий:

```java
public interface GuildsServiceModel extends RemoteService {
}
```

Сценарии использования появятся в этом разделе по мере публикации
<br>методов в модельном интерфейсе. До этого момента инжекция сервиса
<br>возможна, но какого-либо прикладного функционала он не предоставляет.

---

## ENDPOINT

Конфигурационные данные, на которых базируется запуск сервиса
<br>под имплементаций эндпоинта:

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7007</bindPort>
    <!-- Service direction name -->
    <name>guilds</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.guilds.GuildsServiceModel</modelPath>
</service>
```

- Реализация эндпоинта лежит в модуле `services/endpoint/guilds`
  <br>(maven-артефакт `guilds`, версия управляется property
  <br>`service.guilds.version` корневого `pom.xml`);
- Имплементацией основного модельного интерфейса сервиса
  <br>является `me.moonways.endpoint.guilds.GuildsServiceEndpoint`,
  <br>унаследованный от `me.moonways.bridgenet.rmi.endpoint.persistance.EndpointRemoteObject`;
- Эндпоинт собирается в отдельный jar скриптом `./bridgenet endpoints`
  <br>(или в составе полной сборки `./bridgenet build`) и попадает в директорию
  <br>сборки `.build/services/guilds/`, откуда при запуске сервера его загружает
  <br>`me.moonways.bridgenet.rmi.endpoint.EndpointLoader` и биндит на RMI-порт `7007`;
- Реализация на текущий момент не содержит бизнес-логики: обращений к базе
  <br>данных, публикации событий и кеширования в ней нет - класс эндпоинта
  <br>является каркасом, ожидающим наполнения вместе с модельным API.
