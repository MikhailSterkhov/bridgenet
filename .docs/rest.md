# Bridgenet / REST

REST - Вложенный модуль системы Bridgenet, реализующий HTTP/REST сервер
<br>и клиент на базе библиотек Apache HttpComponents. Состоит из трех
<br>maven-сабмодулей:

- `rest-api` - общие сущности обмена: хосты, заголовки, сообщения, ответы и тела запросов;
- `rest-server` - встроенный HTTP-сервер с контроллерами и верификацией запросов;
- `rest-client` - HTTP-клиент и декларативные REST-репозитории на аннотациях.

---

## BUILD

Модуль `rest` объявлен как maven-модуль в корневом `pom.xml` проекта
<br>и агрегирует сабмодули `rest-api`, `rest-client` и `rest-server`;
<br>версия всех сабмодулей задается property `rest.version` в корневом `pom.xml`.
<br>
<br>Модуль зависит от внутреннего модуля `api`, а также от внешних библиотек
<br>`commons-codec`, `httpclient` и `httpcore` (org.apache.httpcomponents).
<br>Сабмодули `rest-client` и `rest-server` дополнительно зависят от `rest-api`.

Скрипты сборки проекта (`./bridgenet jar` или `./bridgenet build`,
<br>см. `.scripts/project_build.sh`) устанавливают модуль `rest` в локальный
<br>maven-репозиторий в общей очереди модулей (после `rmi`, перед `services`).
<br>Отдельного файла в директории сборки `.build` модуль не создает:
<br>сабмодуль `rest-server` подключен зависимостью к модулю `bootstrap`
<br>и упаковывается вместе с ним в единый исполняемый файл `bridgenet-server.jar`.
<br>
<br>Конфигурация HTTP-сервера `rest_server.xml` находится в директории 'etc'
<br>модуля `assembly` и при сборке копируется в директорию `.build/etc`.

Запуск HTTP-сервера при старте системы производится хуком
<br>`me.moonways.bridgenet.bootstrap.hook.type.BindHttpServerHook`,
<br>объявленным в конфигурации `bootstrap.xml` (displayName `HttpServer`,
<br>priority `RUNNER`).

---

## API

### Сервер

Основной точкой входа серверной части является сервис
<br>`me.moonways.bridgenet.rest.server.WrappedHttpServer`:

```java

@Inject
private WrappedHttpServer httpServer;
```

Для запуска сервера мы можем использовать следующий функционал:

```java
httpServer.bind(); // асинхронный запуск (используется в BindHttpServerHook)
```

```java
httpServer.bindSync(); // синхронный запуск
```

Конфигурация сервера читается из ресурса `ResourcesTypes.REST_SERVER_XML`
<br>(файл `rest_server.xml` в 'etc' модуля `assembly`): адрес и порт бинда,
<br>данные аутентификации и список зарегистрированных контроллеров:

```xml

<serverContext>
    <printExceptions>false</printExceptions>
    <connection>
        <host>127.0.0.1</host>
        <port>4590</port>
        <authentication>
            <username>bridgenet_admin</username>
            <password>$threadPool_10056</password>
        </authentication>
    </connection>
    <controllers>
        <controller>
            <method>GET</method>
            <pattern>/users</pattern>
            <handler>me.moonways.bridgenet.rest.server.handler.GetUserInfo</handler>
        </controller>
    </controllers>
</serverContext>
```

Обработчик из тега `handler` должен реализовывать интерфейс
<br>`me.moonways.bridgenet.rest.server.controller.HttpController`:

```java
public class GetUserInfo implements HttpController {

    @Override
    public void process(HttpRequest request, VerificationConfig verificationConfig) {
        // обработка входящего запроса.
    }

    @Override
    public void processCallback(HttpResponse response, VerificationConfig verificationConfig) {
        // наполнение исходящего ответа.
    }
}
```

Если контроллеру нужна только одна из двух сторон обмена, можно унаследовать
<br>абстрактные классы `OnlyRequestHttpController` или `OnlyResponseHttpController`
<br>из пакета `me.moonways.bridgenet.rest.server.controller`.
<br>
<br>Верификация запросов производится по заголовкам, имена которых объявлены
<br>константами в `me.moonways.bridgenet.rest.api.StandardHeaders.Key` -
<br>`BRIDGENET_USERNAME` ("username"), `BRIDGENET_PASSWORD` ("password")
<br>и `BRIDGENET_APIKEY` ("api-key"); значения логина и пароля сверяются
<br>с блоком `authentication` конфигурации `rest_server.xml`.

### Клиент

Клиентская часть строится вокруг класса
<br>`me.moonways.bridgenet.rest.client.WrappedHttpClient`, экземпляр которого
<br>создается по адресу целевого сервера:

```java
HttpHost httpHost = HttpHost.create("127.0.0.1", 4590);
WrappedHttpClient httpClient = WrappedHttpClient.create(httpHost);
```

Для исполнения запросов мы можем использовать следующий функционал:

```java
RestResponse response = httpClient.executeSync(RestMessageType.GET);

int statusCode = response.getStatusCode();
String content = response.getResponseContent();
```

Также запрос можно описать полностью через билдер
<br>`me.moonways.bridgenet.rest.api.exchange.message.RestMessageBuilder`
<br>и исполнить асинхронно:

```java
RestMessage message = RestMessageBuilder.create()
        .setType(RestMessageType.GET)
        .setContext("/users")
        .addHeader(StandardHeaders.Key.BRIDGENET_USERNAME, "bridgenet_admin")
        .addParameter("id", "1")
        .build();

CompletableFuture<RestResponse> future = httpClient.execute(message);
```

Тела запросов (`ExchangeableEntity`) создаются фабрикой
<br>`me.moonways.bridgenet.rest.api.exchange.entity.RestEntityFactory`:

```java

@Inject
private RestEntityFactory entityFactory;
```

```java
ExchangeableEntity text = entityFactory.createTextEntity("Hello, world!");
ExchangeableEntity json = entityFactory.createJsonEntity(userDto);
ExchangeableEntity file = entityFactory.createFileEntity(new File("report.txt"));
```

### Декларативные REST-репозитории

Сабмодуль `rest-client` позволяет описывать клиентов декларативно -
<br>интерфейсом с аннотациями из пакета
<br>`me.moonways.bridgenet.rest.client.repository.persistence` и его подпакетов:

```java

@RestClient(host = "127.0.0.1:4590")
public interface RestClientRepository {

    @RestHeaders({
            @Header(key = StandardHeaders.Key.BRIDGENET_USERNAME, value = "bridgenet_admin"),
            @Header(key = StandardHeaders.Key.BRIDGENET_PASSWORD, value = "$threadPool_10056"),
    })
    @GetMapping("/users")
    RestResponse tryVerifiedPrivateGet();

    @PostMapping("/adduser")
    RestResponse tryUnverifiedPublicPost();

    @DeleteMapping("/deleteuser")
    RestResponse tryUnverifiedPublicDelete();
}
```

Доступны аннотации методов `@GetMapping`, `@PostMapping`, `@PutMapping`,
<br>`@DeleteMapping` и `@RestJsonEntity` (десериализация JSON-ответа в указанный тип),
<br>а также аннотации параметров `@RestAttribute` (URL-параметр запроса)
<br>и `@RestEntity` (тело запроса).
<br>
<br>Рабочий экземпляр репозитория создается проксированием интерфейса
<br>через `me.moonways.bridgenet.api.proxy.AnnotationInterceptor`
<br>с перехватчиком `me.moonways.bridgenet.rest.client.RestClientProxy`:

```java

@Inject
private AnnotationInterceptor annotationInterceptor;
@Inject
private Gson gson;
```

```java
RestRepositoryHelper helper = new RestRepositoryHelper(gson);
HttpHost httpHost = helper.lookupHost(RestClientRepository.class);

RestClientRepository repository = (RestClientRepository) annotationInterceptor.createProxy(
        RestClientRepository.class,
        new RestClientProxy(WrappedHttpClient.create(httpHost), helper));
```

После чего вызовы методов интерфейса исполняют реальные HTTP-запросы:

```java
RestResponse response = repository.tryVerifiedPrivateGet();

assert response.getMethod().equals("GET");
assert response.getStatusCode() == 200;
```

Живые примеры использования клиентской части находятся в тестах
<br>`me.moonways.bridgenet.test.rest.RestClientPublicApiTest`
<br>и `me.moonways.bridgenet.test.rest.RestClientPrivateApiTest`
<br>модуля `testing/units`.

---
