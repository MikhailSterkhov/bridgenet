# Bridgenet / API / Method Intercepting

Method Intercepting - Подсистема модуля `api` (пакет `me.moonways.bridgenet.api.proxy`),
<br>реализующая перехват вызовов методов через динамические прокси.
<br>Позволяет объявлять классы-перехватчики, обрабатывающие вызовы методов,
<br>помеченных определенными аннотациями, и подменять результат их выполнения.

---

## API

Основной сервис подсистемы - `me.moonways.bridgenet.api.proxy.AnnotationInterceptor`.
<br>Он создает прокси-объекты поверх интерфейсов и классов: для интерфейсов используется
<br>стандартный `java.lang.reflect.Proxy` (обработчик `me.moonways.bridgenet.api.proxy.proxy.InterfaceProxy`),
<br>для классов - javassist `ProxyFactory` (обработчик `me.moonways.bridgenet.api.proxy.proxy.SuperclassProxy`).
<br>Выбор реализации происходит автоматически внутри `me.moonways.bridgenet.api.proxy.InterceptController`.

Ключевые аннотации и классы:

- `@MethodInterceptor` - маркирует класс как перехватчик методов;
- `@MethodHandler` - маркирует метод перехватчика как обработчик вызовов;
  <br>параметр `target` принимает список аннотаций - обрабатываться будут только
  <br>вызовы методов, помеченных одной из них (без `target` - все перехваченные вызовы);
- `@MethodPriority(int)` - порядок выполнения обработчиков: чем меньше значение,
  <br>тем раньше выполнится обработчик; результатом вызова становится значение
  <br>последнего выполненного не-void обработчика;
- `ProxiedMethod` - обертка над перехваченным методом: `call(args)`, `callEmpty()`,
  <br>`isVoid()`, `hasAnnotation(cls)`, `findAnnotation(cls)`, `getParameters()`,
  <br>`getParametersByAnnotation(cls)`, `getLastCallReturnedValue()`;
- `ProxyManager` (пакет `me.moonways.bridgenet.api.proxy.proxy`) - сопоставляет
  <br>перехваченные методы с обработчиками перехватчика и управляет их вызовом;
- `InterceptionException` - выбрасывается при нарушении контракта перехвата,
  <br>например если найдено несколько не-void обработчиков без аннотации `@MethodPriority`.

Особенности механики:

- Перехватываются только те методы целевого класса, на которых объявлена
  <br>хотя бы одна аннотация; остальные вызовы делегируются оригинальному объекту;
- Обработчик обязан иметь сигнатуру из двух параметров: `(ProxiedMethod method, Object[] args)`;
- Перед созданием прокси в перехватчик инжектятся зависимости через
  <br>`me.moonways.bridgenet.api.inject.bean.service.BeansService`, а также вызываются
  <br>его методы, помеченные аннотацией `@PostConstruct`;
- Для проксирования класса (не интерфейса) без готового экземпляра требуется
  <br>публичный конструктор без параметров.

---

## USAGE

Для использования перехвата методов необходимо проинжектить
<br>основной сервис подсистемы:

```java

@Inject
private AnnotationInterceptor interceptor;
```

Рассмотрим полный пример из тестовых данных проекта
<br>(`testing/data`, пакет `me.moonways.bridgenet.test.data`).

Сначала объявляем аннотацию, вызовы методов с которой будем перехватывать:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ExampleGreetingAnnotation {
}
```

Затем помечаем ею метод целевого интерфейса:

```java
public interface ExampleGreeting {

    @ExampleGreetingAnnotation
    String sayHello();
}
```

Для обработки перехваченных вызовов объявляем класс-перехватчик
<br>с аннотацией `@MethodInterceptor` и методом-обработчиком `@MethodHandler`:

```java
@MethodInterceptor
public class ExampleGreetingProxy {

    @MethodHandler(target = ExampleGreetingAnnotation.class)
    public Object handle(ProxiedMethod method, Object[] args) {
        return "Hello world!";
    }
}
```

Для создания прокси-объекта и применения перехватчика мы можем
<br>использовать следующий функционал:

```java
ExampleGreeting greeting = interceptor.createProxyChecked(ExampleGreeting.class, new ExampleGreetingProxy());

String message = greeting.sayHello(); // "Hello world!"
```

Если необходимо проксировать уже существующий экземпляр объекта,
<br>мы можем использовать следующий функционал:

```java
Object proxy = interceptor.createProxy(sourceObject, new ExampleGreetingProxy());
```

Именно так поступает `BeansService` при включении декораторов бина
<br>(аннотация `@EnableDecorators`): корневой экземпляр бина оборачивается
<br>в прокси с перехватчиком `me.moonways.bridgenet.api.inject.decorator.DecoratedObjectProxy`.

Если для одного вызова срабатывает несколько не-void обработчиков,
<br>их порядок необходимо задать аннотацией `@MethodPriority`. Реальный пример -
<br>перехватчик REST-репозиториев `me.moonways.bridgenet.rest.client.RestClientProxy`:

```java
@MethodPriority(1)
@MethodHandler(target = GetMapping.class)
public RestResponse handleGetMessage(ProxiedMethod method, Object[] args) {
    return this.executeAnnotatedMessageClient(
            method.findAnnotation(GetMapping.class).value(),
            RestMessageType.GET, method, args
    );
}

@MethodPriority(2)
@MethodHandler(target = RestJsonEntity.class)
public Object handleJson(ProxiedMethod method, Object[] args) {
    if (method.isVoid())
        return method.getLastCallReturnedValue();

    return helper.handleJsonEntityAnnotation(method);
}
```

Здесь обработчик HTTP-запроса выполняется первым, а обработчик
<br>десериализации JSON - вторым, и уже его результат возвращается
<br>вызывающему коду.

Другие применения подсистемы в кодовой базе Bridgenet:

- `me.moonways.bridgenet.api.inject.decorator.DecoratedObjectProxy` - реализация
  <br>декораторов бинов (`@Async`, `@KeepTime`, `@RequiredNotNull` и др.) поверх перехвата методов;
- `me.moonways.bridgenet.api.command.CommandExecutor` - вызов методов-исполнителей
  <br>команд через `interceptor.callProxiedMethod(...)`;
- Юнит-тесты `me.moonways.bridgenet.test.api.inject.ProxyClassInterceptionTest`
  <br>и `me.moonways.bridgenet.test.rest.RestClientPublicApiTest` (модуль `testing/units`).
