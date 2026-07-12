# RMAP

RMAP (Remote Method Access Protocol) — типизированный RPC поверх TCP для Java 8: `client.lookup(path,
Iface.class)` возвращает JDK-прокси, вызов метода превращается в кадр `RGET`, ответ приходит `DONE`
(успех) или `OTHER` (ошибка/отказ). Библиотека `rmap-core` собирается и работает автономно от
остального репозитория bridgenet.

## Что это

- **Zero-dep** — ноль runtime-зависимостей (Lombok — только compile-time, JUnit 5 + AssertJ — только
  test-scope). Собирается на Java 8, работает на 8–24.
- **Собственный whitelist-TLV-кодек** — типизированная сериализация (примитивы, boxed, String, UUID,
  byte[], enum, коллекции/массивы, reflective-объекты, ValueCodec SPI), decode принимает ТОЛЬКО классы
  из явного whitelist (защита от произвольной загрузки классов по FQN с провода).
- **Взаимный HMAC-аутентификация** — и клиент, и сервер доказывают владение общим ключом
  (`Access.privateKey(...)`) либо работают без аутентификации (`Access.publicAccess()`).
- **Plain-NIO транспорт** — 1 selector-поток на эндпоинт + worker-pool, свой protocol поверх TCP,
  без сторонних сетевых библиотек.
- **Remote-refs (opt-in)** — метод может вернуть объект ссылкой (`ExportOptions.wrapReturnAsRemote`),
  клиент получает прокси того же интерфейса; lease-модель освобождает забытые ссылки.
- **Полное тестовое покрытие транспортного слоя** — golden-кодек, roundtrip-property-тесты,
  loopback-интеграция, chaos/adversarial-decode — это библиотека, тесты не опция.

Пакеты: `me.moonways.rmap.api` (публичный API — единственная точка входа приложений), остальные
(`wire`, `codec`, `transport`, `rpc`, `auth`) — internal (конвенция, без модульной системы Java 8).

## Quick start

Полный рабочий пример — исполняемый тест
[`CalculatorDemoTest`](core/src/test/java/me/moonways/rmap/api/CalculatorDemoTest.java) (двойное
назначение: тест библиотеки и витрина API). Код приведён целиком:

```java
public interface Calculator {
    int add(int a, int b);
    CompletableFuture<Integer> addAsync(int a, int b);
    Optional<Integer> lastResult();
    void reset();
    int divide(int a, int b);                            // b==0 → ArithmeticException удалённо
    int recordAndAdd(HistoryEntry entry, int a, int b);   // DTO-параметр через .codec(...)
}

// Плоский DTO БЕЗ @RmapSerializable — кодируем только благодаря .codec(...) на обеих сторонах
// (§5.1: "манифесты... плюс явные регистрации").
public static class HistoryEntry {
    private String label;
    private int value;

    public HistoryEntry() { }
    public HistoryEntry(String label, int value) {
        this.label = label;
        this.value = value;
    }
    public String getLabel() { return label; }
    public int getValue() { return value; }
}

public static class CalculatorImpl implements Calculator {
    private final List<Integer> history = new CopyOnWriteArrayList<>();
    private final List<HistoryEntry> entries = new CopyOnWriteArrayList<>();

    public synchronized int add(int a, int b) {
        int r = a + b;
        history.add(r);
        return r;
    }
    public CompletableFuture<Integer> addAsync(int a, int b) {
        return CompletableFuture.completedFuture(add(a, b));
    }
    public synchronized Optional<Integer> lastResult() {
        return history.isEmpty() ? Optional.empty() : Optional.of(history.get(history.size() - 1));
    }
    public synchronized void reset() {
        history.clear();
    }
    public int divide(int a, int b) {
        return a / b; // b==0 → ArithmeticException, летит клиенту RmapRemoteException
    }
    public synchronized int recordAndAdd(HistoryEntry entry, int a, int b) {
        entries.add(entry);
        return add(a, b);
    }
}

private RmapConfig cfg() {
    return RmapConfig.builder()
            .access(Access.privateKey("demo-key"))
            .appVersion("demo-1.0")
            .clientName("demo-client")
            .codec(c -> c.serializable(HistoryEntry.class))
            .build();
}

RmapNet net = RmapNet.create();

RmapServer server = net.newServer(cfg());
server.bind(new InetSocketAddress("127.0.0.1", 0));
server.put("Demo").export("Calculator", Calculator.class, new CalculatorImpl(),
        ExportOptions.defaults());
server.start();

RmapClient client = net.newClient(cfg());
client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);

Calculator calc = client.lookup("Demo/Calculator", Calculator.class);

// sync
calc.add(2, 3);                              // 5
// async
calc.addAsync(10, 20).get(5, TimeUnit.SECONDS); // 30
// Optional
calc.lastResult();                           // Optional[30]
// void
calc.reset();
calc.lastResult();                           // Optional.empty()
// DTO-параметр (кодируется благодаря .codec(c -> c.serializable(HistoryEntry.class)))
calc.recordAndAdd(new HistoryEntry("first", 1), 4, 5); // 9
// remote-исключение
try {
    calc.divide(1, 0);
} catch (RmapRemoteException e) {
    // e.getMessage() содержит "java.lang.ArithmeticException"
}

client.close();
server.stop();
```

Ключевые моменты примера:

- `RmapNet.create()` — единая точка входа; `newServer`/`newClient` берут `RmapConfig`.
- `Access.privateKey(...)` — взаимный HMAC; `Access.publicAccess()` — без аутентификации.
- `.codec(c -> c.register(new XCodec()).serializable(Y.class, Z.class))` — подключить
  пользовательский `ValueCodec` и/или разрешить reflective-кодирование класса без правки его модуля
  (эквивалент аннотации `@RmapSerializable`, но не требует доступа к исходнику класса). Вызывается
  **на обеих сторонах одинаково** — рассинхрон ловит `codecSchemaVersion`/`interfaceDigest`/whitelist.
- `server.put(topic).export(name, iface, impl, opts)` — все `export` ДО `server.start()`
  (`start()` = bind + export-time audit; после `start()` export бросает `IllegalStateException`).
- `client.lookup(path, iface)` возвращает JDK-прокси немедленно; сетевой `LOOKUP` уходит лениво при
  первом вызове.
- Методы `CompletableFuture<T>` — асинхронные (`future.cancel(true)` шлёт `CANCEL`); `Optional<T>` —
  `DONE(NULL)` → `Optional.empty()`; `void` — подтверждение доставки (`DONE(NULL)`), fire-and-forget
  в v1 нет; синхронные методы блокируются на deadline (`RmapConfig.callTimeout`, per-call override —
  `client.withOptions(proxy, RmapCallOptions.deadline(...))`).
- Исключение реализации на сервере долетает клиенту как `RmapRemoteException` (unchecked, message +
  синтетический стектрейс remote-кадров; оригинальный класс исключения НЕ десериализуется).

## Конфигурация (`RmapConfig`)

Всё — через `RmapConfig.builder()...build()`; один и тот же тип конфига используется и для сервера,
и для клиента (специфичные для роли поля у другой стороны просто не читаются).

| Поле | Дефолт | Назначение |
|---|---|---|
| `access` | — (обязательное) | `Access.privateKey(secret)` (взаимный HMAC) либо `Access.publicAccess()` |
| `appVersion` | — (обязательное) | Версия поставки; equality-сверка в handshake (lockstep-гарантия) |
| `clientName` | — | Имя клиента в HELLO (диагностика; серверу не влияет на логику) |
| `keepAliveInterval` | 15 с | Период PING при отсутствии трафика |
| `idleTimeout` | 45 с | Отсутствие PONG/трафика дольше — close |
| `handshakeTimeout` | 10 с | Таймаут установления сессии до `AUTH_OK` |
| `callTimeout` | 5 с | Дефолтный deadline вызова (per-call override — `RmapCallOptions.deadline(...)`) |
| `frameLimit` | 8 MiB | Лимит размера кадра ПОСЛЕ `AUTH_OK` |
| `preAuthFrameLimit` | 4 KiB | Жёсткий лимит ДО `AUTH_OK` (анти-DoS) |
| `maxConcurrentHandshakes` | 256 | Потолок незавершённых handshake на сервере |
| `maxConnectionsPerRemote` | 32 | Потолок соединений с одного адреса |
| `maxInFlightRequests` | 256 | Backpressure на входящие RGET (снятие `OP_READ`) |
| `outboundLimitBytes` | 64 MiB | Backpressure на исходящую очередь соединения |
| `codec` | no-op | `Consumer<CodecRegistry>` — регистрация `ValueCodec`/`serializable()`-классов |
| `callbackExecutor` | `null` → внутренний 2-поточный daemon-пул | Где завершаются клиентские future (continuation юзера не блокирует decode) |
| `maxInternedClasses` | 4096 | Лимит class-интернирования на соединение (анти-спам уникальными FQN) |
| `refLeaseTimeout` | 10 мин | Порог lease remote-ref без обращений (§10) |
| `closeFlushTimeout` | 5 с | Жёсткий дедлайн close-after-flush (hostile-пир, переставший читать) |

`maxDecodeDepth` (лимит вложенности TLV-графа) НЕ конфигурируется в v1 — зашита константа 32
(анти-DoS от `LIST→LIST→…`-переполнения стека); см. `PROTOCOL.md`.

Метрики и логи — SPI, дефолт no-op:

```java
RmapNet net = RmapNet.create().metrics(myMetrics); // RmapMetrics: счётчики кадров/вызовов/refs
RmapLogging.setFactory(name -> myLoggerAdapter(name)); // RmapLogger: debug/info/warn/error
```

## Границы модели угроз (v1, без TLS)

Взаимный HMAC (`Access.privateKey`) доказывает владение общим ключом обеими сторонами и защищает от
rogue-эндпоинта и replay, но **не даёт конфиденциальности и не защищает от активного on-path MITM**,
способного подменять байты уже установленной сессии. Это осознанная граница v1 — TLS (`SSLEngine`)
запланирован на v1.1. Защита от враждебных/malformed TLV-кадров (whitelist-decode, лимиты глубины и
размеров, DoS-лимиты pre-auth) действует **в любом случае**, независимо от аутентификации.

Не-цели v1 (осознанно отложено): TLS, callbacks/reverse-invocation и стриминг, симметричные exports
клиента, Netty-транспорт, эволюция схемы между версиями (lockstep: рассинхрон = отказ соединения).

## Статус

v1: полностью реализован транспорт (handshake, keep-alive, reconnect), call-слой (LOOKUP/RGET/DONE/
OTHER/CANCEL, deadline), TLV-кодек (golden + property-тесты), remote-refs, публичный API/SPI (этот
документ). Подробности wire-формата — `PROTOCOL.md`.
