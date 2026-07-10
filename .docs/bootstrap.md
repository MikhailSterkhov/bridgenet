# Bridgenet / Bootstrap

Bootstrap - Вложенный модуль системы Bridgenet, являющийся точкой входа
<br>единого серверного приложения. Отвечает за инициализацию системных
<br>настроек и логгеров, запуск Dependency Injection контейнера, а также за
<br>поэтапное исполнение хуков запуска и остановки системы, описанных
<br>в конфигурации `bootstrap.xml`.

---

## BUILD

Модуль `bootstrap` объявлен как maven-модуль в корневом `pom.xml` проекта,
<br>его версия задается property `bootstrap.version` в корневом `pom.xml`.
<br>
<br>Модуль зависит от внутренних модулей системы Bridgenet: `api`, `mtp`,
<br>`jdbc-provider`, `rmi`, `model` и `rest-server`, а также от внешних библиотек
<br>логирования `log4j-core` и `terminalconsoleappender`.

В процессе сборки модуль при помощи `maven-shade-plugin` упаковывается
<br>в единый исполняемый файл `bridgenet-server.jar`, главным классом которого
<br>в манифесте указан `me.moonways.bridgenet.bootstrap.AppStarter`.

Скрипты сборки проекта (`./bridgenet jar` или `./bridgenet build`,
<br>см. `.scripts/project_build.sh`) копируют собранный файл
<br>`bootstrap/target/bridgenet-server.jar` в корень директории сборки `.build`
<br>вместе с ресурсами из директории 'etc' модуля `assembly`.

---

## API

Запуск системы начинается с метода `main` класса
<br>`me.moonways.bridgenet.bootstrap.AppStarter`: он архивирует логи предыдущего
<br>запуска (`PreviousLogsCompressor.compressToGzip()`) и запускает процесс
<br>`me.moonways.bridgenet.bootstrap.AppBootstrap#start(String[] args)`
<br>в отдельном потоке `bridgenet-bootstrap`.

Метод `start` исполняет следующие этапы:

- `runSystems()` - перезапись системных properties из ресурса 'config.properties'
  <br>(`OverridenPropertiesManager`) и переключение debug-режима логгеров
  <br>по значению `debug.mode` (`LoggersManager`);
- `startBeansActivity(true)` - запуск DI-контейнера `BeansService`;
- `BootstrapHookContainer#bindHooks()` - чтение конфигурации хуков из ресурса
  <br>`ResourcesTypes.BOOTSTRAP_XML` (файл `assembly/etc/bootstrap.xml`);
- `processBootstrapHooks(...)` - исполнение хуков с приоритетами `RUNNER`
  <br>и `POST_RUNNER`;
- регистрация shutdown-хука JVM, который при остановке процесса исполняет
  <br>хуки с приоритетами `PRE_SHUTDOWN` и `SHUTDOWN`.

Каждый хук запуска описывается в `bootstrap.xml` следующей структурой
<br>(см. `me.moonways.bridgenet.bootstrap.xml.XMLHookDescriptor`):

```xml

<hook>
    <!-- Display name for logging -->
    <displayName>HttpServer</displayName>
    <!-- Execution order between hooks with same priority -->
    <priorityID>2</priorityID>
    <!-- Execution stage: RUNNER / POST_RUNNER / PRE_SHUTDOWN / SHUTDOWN -->
    <priority>RUNNER</priority>
    <!-- Target hook class type -->
    <executorPath>me.moonways.bridgenet.bootstrap.hook.type.BindHttpServerHook</executorPath>
</hook>
```

Список допустимых значений `priority` хранится в перечислении
<br>`me.moonways.bridgenet.bootstrap.hook.BootstrapHookPriority`
<br>(`RUNNER`, `POST_RUNNER`, `SHUTDOWN`, `PRE_SHUTDOWN`), а очередность
<br>исполнения хуков внутри одного приоритета определяется значением `priorityID`.

Сам хук реализуется наследованием абстрактного класса
<br>`me.moonways.bridgenet.bootstrap.hook.BootstrapHook`. Перед исполнением
<br>экземпляр хука проходит инжект зависимостей, поэтому внутри него доступна
<br>аннотация `@Inject`:

```java
public class BindHttpServerHook extends BootstrapHook {

    @Inject
    private WrappedHttpServer httpServer;

    @Override
    protected void process(@NotNull AppBootstrap bootstrap) {
        if (httpServer != null) {
            httpServer.bind();
        }
    }
}
```

Для управления жизненным циклом приложения из любого бина системы
<br>необходимо проинжектить основной класс этого модуля:

```java

@Inject
private AppBootstrap bootstrap;
```

Для проверки **текущего состояния** приложения мы можем
<br>использовать следующий функционал:

```java
boolean isRunning = bootstrap.isRunning();
```

Для **полной остановки** приложения (исполнение shutdown-хуков
<br>и завершение JVM-процесса) мы можем использовать следующий функционал:

```java
bootstrap.shutdownApp();
```

Для **перезапуска** приложения без завершения JVM-процесса используется
<br>отдельный сервис `me.moonways.bridgenet.bootstrap.restart.RestartService`:

```java

@Inject
private RestartService restartService;
```

```java
restartService.doRestart();
```

Оба сценария также доступны из интерактивной консоли сервера
<br>(`me.moonways.bridgenet.bootstrap.hook.type.console.BridgenetConsole`,
<br>запускается хуком `ConsoleTerminalStartHook` на стадии `POST_RUNNER`)
<br>командами `exit` и `restart`.

---
