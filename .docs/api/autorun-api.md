# Bridgenet / API / AutoRun

AutoRun - Вложенный модуль API системы Bridgenet, реализующий механизм
<br>отложенных и автоматически запускаемых задач (Delayed Runnables).
<br>Позволяет объявлять периодически исполняемые процессы декларативно —
<br>при помощи аннотаций, без ручного взаимодействия с планировщиком.

---

## API

Исходный код модуля расположен в пакете `me.moonways.bridgenet.api.autorun`
<br>maven-модуля `api` и попадает в общую сборку системы вместе с исполняемым
<br>файлом `bridgenet-server.jar` в директории '.build' (сборка производится
<br>скриптом `./bridgenet build` или `./bridgenet jar`).

Ключевые аннотации объявлены в пакете `me.moonways.bridgenet.api.autorun.persistence`:

- `@AutoRunner` — маркирует класс как контейнер автозапускаемых задач.
  <br>Благодаря аннотации `@UseTypeAnnotationProcessor` такие классы автоматически
  <br>обнаруживаются сканером Dependency Injection при старте системы —
  <br>ручная регистрация не требуется;
- `@RunUnit` — маркирует метод внутри `@AutoRunner`-класса, который
  <br>будет периодически исполняться;
- `@DelayedPeriod(period = ..., unit = ...)` — задает период исполнения
  <br>(`long period` + `java.util.concurrent.TimeUnit unit`); может применяться
  <br>как ко всему классу сразу, так и к отдельному методу.

Внутреннюю работу механизма обеспечивают следующие классы пакета
<br>`me.moonways.bridgenet.api.autorun`:

- `ScheduledRunnersService` — основной сервис модуля: дожидается результатов
  <br>сканирования аннотации `@AutoRunner` (через `@AwaitAnnotationsScanning`),
  <br>после чего в `@PostConstruct` запускает единый глобальный таймер при помощи
  <br>сервиса `me.moonways.bridgenet.api.scheduler.Scheduler` (стартовая задержка —
  <br>1 секунда, проверка юнитов — каждые 100 миллисекунд);
- `RunnableUnit` — единица запуска: уникальный UUID, период (`ScheduledTime`)
  <br>и исполняемая функция (`ExceptionallyRunnable`);
- `ScheduledRunnersInvocationTask` — процесс глобального таймера
  <br>(имплементация `TaskProcess`): на каждом тике проверяет, истек ли период
  <br>каждого юнита с момента его последнего запуска, и при необходимости исполняет его.

Период для каждого `@RunUnit`-метода вычисляется по приоритету:
<br>`@DelayedPeriod` на методе → `@DelayedPeriod` на классе → значение
<br>по умолчанию (100 миллисекунд).

Исключение, выброшенное внутри задачи, перехватывается и логируется
<br>вместе с UUID юнита — глобальный таймер и остальные задачи продолжают
<br>работать в штатном режиме.

---

## USAGE

Для объявления автозапускаемой задачи достаточно создать класс,
<br>пометить его аннотацией `@AutoRunner`, а периодически исполняемый
<br>метод — аннотацией `@RunUnit`. Зависимости внутри такого класса
<br>инжектятся стандартным для системы образом — через аннотацию `@Inject`.

Реальный пример из модуля `mtp` — периодическая очистка истекших
<br>ожидающих ответа сообщений (`me.moonways.bridgenet.mtp.message.response.ResponsibleMessageCleanUpRunner`):

```java

@AutoRunner
@DelayedPeriod(period = 1, unit = TimeUnit.SECONDS)
public class ResponsibleMessageCleanUpRunner {

    @Inject
    private ResponsibleMessageService service;

    @RunUnit
    public void run() {
        if (service != null) {
            service.cleanUp();
        }
    }
}
```

Здесь аннотация `@DelayedPeriod` установлена на весь класс —
<br>метод `run()` будет исполняться каждую секунду.

Для установки индивидуального периода конкретной задаче мы можем
<br>использовать `@DelayedPeriod` непосредственно на методе — такое значение
<br>имеет приоритет над классовым:

```java

@AutoRunner
public class ExampleRunner {

    @RunUnit
    @DelayedPeriod(period = 30, unit = TimeUnit.SECONDS)
    public void everyHalfMinute() {
        // executes every 30 seconds.
    }

    @RunUnit
    public void everyTick() {
        // no period specified - executes every 100 milliseconds (default).
    }
}
```

Никакой дополнительной регистрации не требуется: при старте системы
<br>сервис `ScheduledRunnersService` автоматически найдет все `@AutoRunner`-классы,
<br>создаст для каждого `@RunUnit`-метода свой `RunnableUnit` и включит их
<br>в цикл глобального таймера.

Другие реальные применения данного API в системе:

- `me.moonways.bridgenet.bootstrap.autorun.MemoryDataLoggingAutorunner` —
  <br>каждые 30 секунд запускает сборщик мусора и логирует метрики памяти
  <br>Runtime в профайлер (`BridgenetDataLogger`);
- `me.moonways.bridgenet.test.data.management.ExampleSetPropertyRunner` —
  <br>тестовый раннер из модуля `testing/data`, каждые 500 миллисекунд
  <br>перезаписывающий системное свойство случайным значением.
