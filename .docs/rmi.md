# Bridgenet / RMI

RMI - Вложенный модуль системы Bridgenet, реализующий внутренний протокол
<br>удаленного взаимодействия (Java RMI) между ядром Bridgenet и его внутренними
<br>сервисами (эндпоинтами), описанными в конфигурации `remote_services.xml`.

---

## BUILD

Модуль `rmi` объявлен как maven-модуль в корневом `pom.xml` проекта,
<br>его версия управляется property `rmi.version` из блока properties корневого `pom.xml`.
<br>
<br>Из внутренних модулей системы `rmi` зависит от модулей `api` и `mtp`
<br>(см. `rmi/pom.xml`).
<br>
<br>В процессе полной сборки проекта (`./bridgenet build` или `./bridgenet jar`)
<br>скрипт `.scripts/project_build.sh` устанавливает модуль в локальный maven-репозиторий
<br>в общей очереди сборки модулей (`assembly`, `profiler`, `api`, `mtp`, `jdbc`, `rmi`, ...).
<br>Отдельного артефакта в директории сборки `.build` модуль не имеет -
<br>он попадает в итоговый `bridgenet-server.jar`, так как модуль `bootstrap`
<br>подключает `rmi` как compile-зависимость.

В рантайме модуль опирается на ресурсы модуля `assembly`:

- `remote_services.xml` (константа `ResourcesTypes.REMOTE_SERVICES_XML`) -
  <br>описания удаленных сервисов и модулей протокола;
- `rmi.policy` (константа `ResourcesTypes.RMI_POLICY`) - политика безопасности,
  <br>применяемая через системное свойство `java.security.policy`;
- Скомпилированные реализации сервисов (эндпоинты) `EndpointLoader` ищет
  <br>в директории `.build/services` при локальном запуске (из IDE)
  <br>и в директории `services` при запуске на выделенной машине.

---

## API

Основной сервис модуля, управляющий жизненным циклом всех удаленных
<br>сервисов - `me.moonways.bridgenet.rmi.service.RemoteServicesManagement`:

```java

@Inject
private RemoteServicesManagement remoteServicesManagement;
```

Полный цикл запуска удаленных сервисов выполняется ядром Bridgenet
<br>в хуке `me.moonways.bridgenet.bootstrap.hook.type.RunRMIEndpointsHook`
<br>и состоит из трех шагов:

```java
remoteServicesManagement.initConfig();              // чтение и парсинг remote_services.xml
remoteServicesManagement.initEndpointsController(); // поиск эндпоинтов и инжекция модулей
remoteServicesManagement.exportEndpoints();         // экспорт сервисов по протоколу RMI
```

Описание каждого сервиса берется из конфигурации `remote_services.xml`
<br>(модуль `assembly`, директория 'etc') и превращается в дескриптор
<br>`me.moonways.bridgenet.rmi.service.ServiceInfo` (имя, порт, модельный класс):

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7013</bindPort>
    <!-- Service direction name -->
    <name>mojang</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.mojang.MojangServiceModel</modelPath>
</service>
```

Модельный класс из `modelPath` обязан быть интерфейсом и наследовать
<br>маркерный интерфейс `me.moonways.bridgenet.rmi.service.RemoteService`
<br>(который, в свою очередь, наследует `java.rmi.Remote`):

```java
public interface MojangServiceModel extends RemoteService {
    // ...
}
```

Для получения зарегистрированных **описаний сервисов** мы можем
<br>использовать следующий функционал:

```java
Map<String, ServiceInfo> servicesInfos = remoteServicesManagement.getServicesInfos();
ServiceInfo serviceInfo = servicesInfos.get("mojang");
```

Для получения **экспортированной реализации** сервиса по его дескриптору
<br>мы можем использовать следующий функционал:

```java
Optional<RemoteService> serviceOptional = remoteServicesManagement.findInstance(serviceInfo);

if (!serviceOptional.isPresent()) {
    // service is not exported yet.
}
```

Для подписки на момент, когда **все сервисы будут экспортированы**,
<br>мы можем использовать следующий функционал:

```java
remoteServicesManagement.subscribeExportedAll(() -> {
    // all remote-services are exported.
});
```

После экспорта эндпоинта его реализация также биндится как бин
<br>под модельным интерфейсом, поэтому в прикладном коде удаленный сервис
<br>достаточно просто проинжектить:

```java

@Inject
private MojangServiceModel mojangServiceModel;
```

### Модули протокола

Помимо сервисов, в `remote_services.xml` описываются модули протокола -
<br>реализации интерфейса `me.moonways.bridgenet.rmi.module.RemoteModule`
<br>(базовый класс - `me.moonways.bridgenet.rmi.module.AbstractRemoteModule`),
<br>которые инжектируются в каждый сервис через `ServiceModulesContainer`:

```xml

<modules>
    <module name="loggingModule"
            targetClass="me.moonways.bridgenet.rmi.module.logging.LoggingRemoteModule"
            configClass="me.moonways.bridgenet.rmi.module.logging.LoggingConfig">

        <!-- Logs pool size limit for logs-module -->
        <property name="poolSize" value="1024"/>
    </module>
    <module name="accessModule"
            targetClass="me.moonways.bridgenet.rmi.module.access.AccessRemoteModule"
            configClass="me.moonways.bridgenet.rmi.module.access.AccessConfig">

        <!-- RMI Protocol basically services bind host -->
        <property name="remoteHost" value="127.0.0.1"/>
    </module>
</modules>
```

- `accessModule` (`me.moonways.bridgenet.rmi.module.access.AccessRemoteModule`) -
  <br>отвечает за сам сетевой доступ: формирует URI вида `rmi://host:port/name`,
  <br>экспортирует реализацию сервиса (`exportStub(ServiceInfo)`), снимает биндинг
  <br>(`unbind(ServiceInfo)`) и выполняет удаленный поиск заглушки (`lookupStub()`);
- `loggingModule` (`me.moonways.bridgenet.rmi.module.logging.LoggingRemoteModule`) -
  <br>модуль журналирования сервисов, конфигурируется размером пула логов (`poolSize`).

Значения атрибутов `property` автоматически переносятся рефлексией в поля
<br>конфигурационного класса модуля (`configClass`, реализация интерфейса
<br>`me.moonways.bridgenet.rmi.module.ModuleConfiguration` - например,
<br>`AccessConfig.remoteHost`). Парсингом XML занимаются дескрипторы пакета
<br>`me.moonways.bridgenet.rmi.xml`: `XMLServicesConfigDescriptor`,
<br>`XmlServiceInfoDescriptor`, `XMLServiceModuleDescriptor`
<br>и `XMLServiceModulePropertyDescriptor`.

Экземпляр инжектированного модуля можно получить из контейнера модулей сервиса:

```java
ServiceModulesContainer modulesContainer = remoteServicesManagement.getModulesContainerMap().get(serviceInfo);
AccessRemoteModule accessModule = modulesContainer.getModuleInstance(AccessRemoteModule.class);
```

### Реализация эндпоинта

Реализация удаленного сервиса наследует абстрактный класс
<br>`me.moonways.bridgenet.rmi.endpoint.persistance.EndpointRemoteObject`
<br>и переопределяет метод `construct`, получая на вход контекст
<br>`me.moonways.bridgenet.rmi.endpoint.persistance.EndpointRemoteContext`:

```java
public final class MojangServiceEndpoint extends EndpointRemoteObject implements MojangServiceModel {

    public MojangServiceEndpoint() throws RemoteException {
        super();
    }

    @Override
    protected void construct(EndpointRemoteContext context) {
        // context.registerCommand(...);
        // context.registerMessageListener(...);
        // context.registerEventListener(...);
        // context.inject(...);
        // context.bind(...);
    }
}
```

Каждый эндпоинт лежит в собственной поддиректории `services/` сборки
<br>вместе с конфигурацией `endpoint.json` (модель -
<br>`me.moonways.bridgenet.rmi.endpoint.EndpointConfig`: имя jar-файла
<br>и хеш-таблица произвольных свойств, доступных внутри эндпоинта
<br>через `EndpointRemoteObject#get(String configKey)`).
<br>Поиском, валидацией и запуском эндпоинтов занимаются
<br>`EndpointController`, `EndpointLoader` и `EndpointRunner`
<br>из пакета `me.moonways.bridgenet.rmi.endpoint`.

### Клиентская сторона

Клиенты Bridgenet (модуль `client`) подключаются к уже экспортированным
<br>сервисам через `me.moonways.bridgenet.client.api.ClientEngine`, который
<br>для каждого описания сервиса из `remote_services.xml` выполняет
<br>`AccessRemoteModule#lookupStub()` и биндит полученную заглушку
<br>под модельным интерфейсом:

```java
clientEngine.connectToEndpoints(remoteServicesManagement);
```

---
