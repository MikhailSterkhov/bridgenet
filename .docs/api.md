# Bridgenet / API

API - Центральный модуль системы Bridgenet, предоставляющий разработчику
<br>базовый набор инструментов: внедрение зависимостей, события, пользовательские
<br>команды, планирование и автозапуск задач, перехват вызовов методов через прокси,
<br>а также общие вспомогательные утилиты.

---

## BUILD

API является maven-модулем `api` из корневого `pom.xml` проекта;
<br>версия модуля управляется property `<api.version>` корневого `pom.xml`.
<br>
<br>Модуль зависит от модулей `assembly` (чтение унифицированных ресурсов
<br>и XML/JSON конфигураций) и `profiler` (метрики и профилирование системы).

В процессе полной сборки проекта (`./bridgenet build` или `./bridgenet jar`,
<br>скрипт `.scripts/project_build.sh`) модуль устанавливается в локальный
<br>maven-репозиторий третьим в очереди сборки — сразу после `assembly` и `profiler`.
<br>
<br>Отдельного jar-файла в директории сборки '.build' модуль не имеет:
<br>его классы включаются в итоговый исполняемый файл `bridgenet-server.jar`
<br>(модуль `bootstrap`, плагин `maven-shade-plugin`), который скрипт сборки
<br>копирует в '.build'.
<br>
<br>Практически все остальные модули системы (`mtp`, `jdbc`, `rmi`, `rest`,
<br>`services`, `bootstrap`, `testing`) подключают `api` как compile-зависимость.

---

## USAGE

Функционал модуля разделен на несколько под-API, каждое из которых
<br>имеет отдельную детальную документацию:

- [API / Отложенные задачи (Delayed Runnables)](api/autorun-api.md) — пакет `autorun`, сервис `ScheduledRunnersService`;
- [API / Пользовательские команды (User Commands)](api/commands-api.md) — пакет `command`, сервисы `CommandRegistry` и `CommandExecutor`;
- [API / Подписка на события (Events Subscribing)](api/events-api.md) — пакет `event`, сервис `EventService`;
- [API / Внедрение зависимостей (Dependency Injection)](api/inject-api.md) — пакет `inject`, сервис `BeansService`;
- [API / Парсинг XML (JAXB)](api/jaxb-api.md) — чтение XML-конфигураций через `ResourcesAssembly.readXmlAtEntity(...)`
  <br>и `me.moonways.bridgenet.assembly.jaxb.XmlJaxbParser` модуля `assembly`;
- [API / Перехват методов (Method Intercepting)](api/proxy-api.md) — пакет `proxy`, сервис `AnnotationInterceptor`;
- [API / Планирование задач (Scheduling Tasks)](api/scheduler-api.md) — пакет `scheduler`, сервис `Scheduler`.

Дополнительно модуль содержит пакет `minecraft` с общими игровыми
<br>утилитами (`ChatColor`) и пакет `util` с вспомогательными инструментами
<br>(`ReflectionUtils`, `Threads`, `Pair` и т.д.).

Основные сервисы модуля являются bean-компонентами (аннотация `@Autobind`)
<br>и доступны из любого другого бина через аннотацию `@Inject`
(пакет `me.moonways.bridgenet.api.inject`):

```java

@Inject
private EventService eventService;
@Inject
private Scheduler scheduler;
@Inject
private CommandRegistry commandRegistry;
@Inject
private AnnotationInterceptor interceptor;
@Inject
private BeansService beansService;
```

Приведем короткие примеры.

Для **вызова события** и подписки на результат его обработки
<br>мы можем использовать следующий функционал:

```java
eventService.fireEvent(new ExampleUserEvent(user))
        .follow(event -> {
            // event has been handled.
        });
```

Слушатель событий объявляется аннотациями `@InboundEventListener` и `@SubscribeEvent`
<br>и регистрируется системой автоматически:

```java
@InboundEventListener
public class ExampleEventListener {

    @SubscribeEvent
    public void handle(ExampleUserEvent event) {
        log.debug(event);
    }
}
```

Для **планирования периодической задачи** мы можем использовать
<br>следующий функционал:

```java
scheduler.schedule(ScheduledTime.ofSeconds(1), ScheduledTime.ofSeconds(30))
        .follow(scheduledTask -> {
            // do something every 30 seconds.
        });
```

Для **автозапуска задач по расписанию** достаточно объявить класс с аннотациями
<br>`@AutoRunner` и `@RunUnit` (пакет `me.moonways.bridgenet.api.autorun.persistence`) —
<br>сервис `ScheduledRunnersService` обнаружит и запустит его автоматически:

```java
@AutoRunner
@DelayedPeriod(period = 30, unit = TimeUnit.SECONDS)
public class MemoryDataLoggingAutorunner {

    @RunUnit
    public void run() {
        // runs every 30 seconds.
    }
}
```

Для **создания пользовательской команды** используются аннотации
<br>пакета `me.moonways.bridgenet.api.command.annotation`:

```java
@Alias("mem")
@Command("memory")
public class RuntimeMemoryCommand {

    @MentorExecutor
    public void defaultCommand(CommandSession session) {
        EntityCommandSender sender = session.getSender();
        sender.sendMessage("Hello!");
    }
}
```

Для **перехвата вызовов методов** через прокси мы можем использовать
<br>следующий функционал:

```java
ExampleGreeting greeting = interceptor.createProxyChecked(ExampleGreeting.class, new ExampleGreetingProxy());
```

Для **ручного доступа к бинам** и внедрения зависимостей в сторонние
<br>объекты мы можем использовать следующий функционал:

```java
Optional<Scheduler> schedulerOptional = beansService.getInstance(Scheduler.class);
```

```java
beansService.inject(listener);
```

---
