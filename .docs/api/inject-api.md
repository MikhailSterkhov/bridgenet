# Bridgenet / API / Dependency Injection

Dependency Injection - собственная реализация технологии внедрения зависимостей
<br>системы Bridgenet, расположенная в модуле `api` (пакет `me.moonways.bridgenet.api.inject`).
<br>Отвечает за сканирование проекта, создание, хранение и инжекцию бинов,
<br>управление их жизненным циклом и декорирование методов через прокси.

---

## API

Центральной точкой управления системой бинов является сервис
<br>`me.moonways.bridgenet.api.inject.bean.service.BeansService` - в нем реализовано
<br>сканирование проекта по пакейджам, чтение конфигурации, бинд, удаление и инжекция бинов.
<br>Инициализацию производит модуль `bootstrap`: класс `AppBootstrap` создает экземпляр
<br>`BeansService` и вызывает `beansService.start()` в процессе запуска приложения,
<br>после чего все обнаруженные бины становятся доступны для инжекции.

Основные аннотации пакета `me.moonways.bridgenet.api.inject`:

| Аннотация          | Цель               | Описание                                                                     |
|--------------------|--------------------|------------------------------------------------------------------------------|
| `@Inject`          | поле / конструктор | Инжекция забинденного бина в компонент                                       |
| `@Autobind`        | класс              | Автоматическая регистрация класса как бина при старте системы                |
| `@BeanFactory`     | статический метод  | Фабричный метод создания бина (совместно с `FactoryType.METHOD`)             |
| `@PostConstruct`   | метод              | Вызов метода сразу после конструирования и инжекции бина                     |
| `@PreConstruct`    | метод              | Вызов метода перед конструированием бина                                     |
| `@Property`        | поле               | Инжекция значения системного проперти по указанному ключу                    |
| `@IgnoredRegistry` | класс              | Исключение класса из автоматической регистрации                              |

Способ создания экземпляра бина определяется параметром `provider` аннотации `@Autobind`
<br>и перечислением `me.moonways.bridgenet.api.inject.bean.factory.FactoryType`:

- `CONSTRUCTOR` - создание через конструктор (значение по умолчанию);
- `METHOD` - создание через статический метод, помеченный аннотацией `@BeanFactory`;
- `UNSAFE` - создание без вызова конструктора.

Фабрика по умолчанию может быть переопределена системным пропертей `beans.factory.default`
<br>(см. `me.moonways.bridgenet.assembly.OverridenProperty.BEANS_FACTORY_DEFAULT`).

Помимо аннотаций, модуль предоставляет обертки для ленивой инициализации и хранения объектов:

- `Lazy<T>` - ленивая инициализация объекта при первом обращении к `get()`;
- `Singleton<T>` - потокобезопасное хранение единственного экземпляра;
- `Prototype<T>` - хранение поставщика объекта с возможностью его подмены через `set(...)`;
- `Weak<T>` - хранение объекта под слабой ссылкой (`WeakReference`);
- `WrappedProperty` - типизированный доступ к системным пропертям (`getAsString()`, `getAsInt()`, `getAsBoolean()` и т.д.).

### DECORATORS

Аннотация `me.moonways.bridgenet.api.inject.decorator.EnableDecorators` включает для бина
<br>проксирование методов, помеченных аннотациями-декораторами из пакета
<br>`me.moonways.bridgenet.api.inject.decorator.persistence`:

| Декоратор          | Описание                                                                        |
|--------------------|---------------------------------------------------------------------------------|
| `@Async`           | Выполнение метода в асинхронном потоке (cached thread-pool)                     |
| `@ParallelAsync`   | Выполнение метода в параллельном пуле (work-stealing pool)                      |
| `@LateExecution`   | Отложенное выполнение метода на `delay()` в единицах `unit()`                   |
| `@KeepTime`        | Замер и логирование времени выполнения метода                                   |
| `@Singleton`       | Кеширование первого результата метода и возврат его при повторных вызовах       |
| `@RequiredNotNull` | Выброс `NullPointerException` с сообщением `message()`, если метод вернул null  |

Список декораторов, их обработчиков (`me.moonways.bridgenet.api.inject.decorator.persistence.handler`),
<br>конфликтов и наследований описывается в конфигурации `decorators.xml` из директории 'etc'
<br>модуля `assembly` (при сборке проекта попадает в 'etc' директории '.build') и читается
<br>сканером `me.moonways.bridgenet.api.inject.decorator.DecoratedMethodScanner`:

```xml
<methodHandler>
    <name>Async</name>
    <annotation>me.moonways.bridgenet.api.inject.decorator.persistence.Async</annotation>
    <path>me.moonways.bridgenet.api.inject.decorator.persistence.handler.AsyncMethodHandler</path>
</methodHandler>
```

---

## USAGE

Для управления бинами напрямую необходимо проинжектить основной сервис этого модуля:

```java

@Inject
private BeansService beansService;
```

Однако в большинстве случаев прямое обращение к `BeansService` не требуется -
<br>достаточно объявить класс бином через аннотацию `@Autobind`, и система сама
<br>зарегистрирует его при старте, выполнив инжекцию всех полей `@Inject` и вызвав
<br>методы `@PostConstruct`. Реальный пример из эндпоинта `mojang`
<br>(`me.moonways.endpoint.mojang.MojangApi`):

```java
@Autobind
public final class MojangApi {

    @Inject
    private Gson gson;

    private MojangRestApi mojangRestApi;

    @PostConstruct
    private void init() {
        mojangRestApi = new MojangRestApi(gson);
        mojangRestApi.mappingEndpoints();
    }
}
```

Если конструирование бина требует дополнительной логики, используется фабричный
<br>метод: аннотация `@Autobind(provider = FactoryType.METHOD)` на классе и статический
<br>метод с аннотацией `@BeanFactory`, параметры которого система заполнит уже
<br>забинденными бинами (пример - `me.moonways.bridgenet.mtp.connection.BridgenetNetworkConnectionFactory`):

```java
@Autobind(provider = FactoryType.METHOD)
public class BridgenetNetworkConnectionFactory {

    @BeanFactory
    private static BridgenetNetworkConnectionFactory newInstance(BeansService beansService, ResourcesAssembly assembly) {
        // ... чтение конфигурации и создание экземпляра
        return new BridgenetNetworkConnectionFactory(configuration, socketAddress);
    }
}
```

Для **ручной регистрации** экземпляра как бина мы можем использовать следующий функционал:

```java
beansService.bind(new AnyObjectWithDecorators());
```

Для **получения экземпляра** уже забинденного бина по его классу
<br>мы можем использовать следующий функционал:

```java
Optional<ResourcesAssembly> assemblyOptional = beansService.getInstance(ResourcesAssembly.class);
```

Для **инжекции зависимостей** в объект, созданный вручную (без регистрации его как бина),
<br>мы можем использовать следующий функционал:

```java
beansService.inject(bridgenetNetworkClient);
```

Для **подписки на бинд** бина, который еще не был зарегистрирован системой,
<br>мы можем использовать следующий функционал:

```java
beansService.subscribeOn(ResourcesAssembly.class, assembly -> {
    // вызовется сразу после регистрации бина
});
```

Для **удаления** бина из кеша системы мы можем использовать следующий функционал
<br>(пример - `me.moonways.bridgenet.bootstrap.restart.RestartService`):

```java
@Inject
private BeansStore beansStore;

// ...

new ArrayList<>(beansStore.getTotalBeans()).forEach(beansService::unbind);
```

Для применения **декораторов** достаточно пометить класс бина аннотацией
<br>`@EnableDecorators` и расставить аннотации-декораторы на методы. Реальный пример
<br>из `me.moonways.bridgenet.rest.server.WrappedHttpServer`:

```java
@Autobind
@EnableDecorators
public class WrappedHttpServer {

    @Async
    @KeepTime
    public void bind() {
        bindSync();
    }
}
```

Метод `bind()` будет выполнен асинхронно, а время его выполнения - замерено
<br>и записано в лог. Больше примеров работы декораторов - в юнит-тесте
<br>`me.moonways.bridgenet.test.api.inject.DecoratedDependencyTest` (модуль `testing/units`).
