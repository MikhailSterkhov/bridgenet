# Bridgenet / API / Scheduler

Scheduler - Внутреннее API системы Bridgenet для планирования задач.
<br>Позволяет выполнять произвольную логику единоразово с заданной задержкой
<br>либо циклично с определенным периодом между повторениями - как асинхронно
<br>в общем пуле потоков, так и синхронно с блокирующим ожиданием.

---

## API

Исходный код API расположен в maven-модуле `api`, в пакете
<br>`me.moonways.bridgenet.api.scheduler`, и после сборки проекта скриптом `./bridgenet`
<br>попадает в общий скомпилированный файл `bridgenet-server.jar` в директории '.build'.

Основные классы и интерфейсы:

- `me.moonways.bridgenet.api.scheduler.Scheduler` - основной сервис планирования задач;
  <br>помечен аннотацией `@Autobind`, благодаря чему автоматически создается и регистрируется
  <br>как бин в Dependency Injection контейнере системы;
- `me.moonways.bridgenet.api.scheduler.ScheduledTime` - обертка над значением времени
  <br>(задержка + `java.util.concurrent.TimeUnit`), создается через статические фабричные методы;
- `me.moonways.bridgenet.api.scheduler.task.TaskFuture` - результат планирования задачи;
  <br>через него подписываются обработчики выполнения и читается состояние процесса;
- `me.moonways.bridgenet.api.scheduler.task.TaskProcess` - функциональный интерфейс
  <br>обработчика процесса запланированной задачи (метод `doProcess(ScheduledTask)`);
- `me.moonways.bridgenet.api.scheduler.task.ScheduledTask` - макет запланированной задачи:
  <br>идентификатор, задержка, период, флаги `isAsynchronous()` / `isCancelled()`,
  <br>а также управление остановкой - `shutdown()` и `forceShutdown()`;
- `me.moonways.bridgenet.api.scheduler.ScheduleException` - исключение, которым
  <br>API сигнализирует об ошибках планирования и валидации.

Сервис `Scheduler` предоставляет четыре точки входа, каждая из которых
<br>возвращает `TaskFuture`:

```java
TaskFuture schedule(ScheduledTime delay);                            // единоразовая, асинхронная
TaskFuture schedule(ScheduledTime delay, ScheduledTime period);      // периодическая, асинхронная
TaskFuture scheduleSynchronized(ScheduledTime delay);                // единоразовая, синхронная
TaskFuture scheduleSynchronized(ScheduledTime delay, ScheduledTime period); // периодическая, синхронная
```

Асинхронные задачи планируются через внутренний `ScheduledExecutorService`
<br>на 4 потока (создается утилитой `me.moonways.bridgenet.api.util.thread.Threads`),
<br>синхронные - выполняют ожидание задержки и периода блокирующим `wait()`,
<br>занимая поток на все время жизни задачи.
<br>
<br>Под капотом единоразовые задачи представлены классом
<br>`me.moonways.bridgenet.api.scheduler.task.LateDelayedTask`, а периодические -
<br>`me.moonways.bridgenet.api.scheduler.task.InfinityDelayedTask`; периодическая
<br>задача выполняется до тех пор, пока не будет отменена через `shutdown()`.

---

## USAGE

Для того чтобы планировать задачи, необходимо проинжектить
<br>основной сервис этого API:

```java

@Inject
private Scheduler scheduler;
```

Значения задержек и периодов описываются классом `ScheduledTime`
<br>через статические фабричные методы:

```java
ScheduledTime zero = ScheduledTime.zero();
ScheduledTime halfSecond = ScheduledTime.ofMillis(500);
ScheduledTime tenSeconds = ScheduledTime.ofSeconds(10);
ScheduledTime fiveMinutes = ScheduledTime.of(5, TimeUnit.MINUTES);
```

Для **единоразового отложенного выполнения** логики мы можем
<br>использовать следующий функционал:

```java
scheduler.schedule(ScheduledTime.ofSeconds(5))
        .follow(scheduledTask -> {
            // executes once after 5 seconds.
        });
```

Для **периодического выполнения** логики мы можем использовать
<br>следующий функционал (первый параметр - задержка перед первым
<br>выполнением, второй - период между повторениями):

```java
scheduler.schedule(ScheduledTime.zero(), ScheduledTime.ofSeconds(1))
        .follow(scheduledTask -> {
            // executes every second.
        });
```

Обработчик `follow(...)` принимает интерфейс `TaskProcess` и может
<br>вызываться несколько раз - обработчики объединяются в цепочку и
<br>выполняются последовательно.

Для **остановки периодической задачи** используется `ScheduledTask`,
<br>который передается в обработчик процесса; количество уже выполненных
<br>циклов доступно из `TaskFuture`:

```java
TaskFuture taskFuture = scheduler.schedule(ScheduledTime.zero(), ScheduledTime.ofSeconds(1));

taskFuture.follow(scheduledTask -> {
    if (taskFuture.getProcessedLoopsCount() >= 10) {
        scheduledTask.shutdown(); // stop after 10 loops.
    }
});
```

Для **блокирующего ожидания ближайшего выполнения** задачи мы можем
<br>использовать следующий функционал:

```java
taskFuture.join();
```

Обработчик процесса не обязательно объявлять лямбдой - `TaskProcess`
<br>можно реализовать отдельным классом. Именно так это API используется
<br>внутри системы: сервис авто-раннеров `me.moonways.bridgenet.api.autorun.ScheduledRunnersService`
<br>запускает единый глобальный таймер, обработчиком которого выступает
<br>класс `me.moonways.bridgenet.api.autorun.ScheduledRunnersInvocationTask`:

```java
public class ScheduledRunnersInvocationTask implements TaskProcess {

    @Override
    public void doProcess(@NotNull ScheduledTask scheduledTask) {
        // invoke all registered auto-runner units.
    }
}
```

```java
scheduler.schedule(ScheduledTime.ofSeconds(1), ScheduledTime.ofMillis(100))
        .follow(new ScheduledRunnersInvocationTask(unitsMap));
```

В тестовом движке (модуль `testing/engine`) пакет планировщика подключается
<br>в контейнер тестов модулем `me.moonways.bridgenet.test.engine.component.module.impl.SchedulersModule`,
<br>от которого, к примеру, зависит модуль авто-раннеров `AutorunnersModule`.
