# RMAP — wire-протокол v1

Нормативное описание wire-уровня RMAP. Публичная выжимка для внешнего репозитория (источник истины —
внутренняя спека проектирования, не коммитится сюда). Big-endian везде, если не указано иное.

## 1. Кадр

```
[int32 length][uint8 frameType][int64 callId][payload…]
```

- `length` — байты ПОСЛЕ поля `length` (`frameType` + `callId` + `payload`).
- Лимит кадра — `frameLimit` (дефолт 8 MiB, ПОСЛЕ `AUTH_OK`; `preAuthFrameLimit` 4 KiB — до).
  Превышение → `OTHER(FRAME_TOO_LARGE)` + немедленный `close()`.
- `callId` — 64-битный счётчик стороны-инициатора (с 1), у каждой стороны свой (пространства
  независимы); для кадров без корреляции (`PING`/`PONG`/`REF_RELEASE`) `callId = 0`.

**`str`** — бестеговая строка `int32 len + UTF-8 (len байт)`. Применяется и в заголовках кадров
(HELLO, LOOKUP…), и как позиционное поле внутри составных TLV-тегов (`ENUM.name`, `EXCEPTION.*`, FQN
в `classRef`). `str` НЕ участвует в identity-map/`BACK_REF` и class-интернировании. `len ≥ 0`;
отрицательный или превышающий остаток буфера `len` → малформ (`PROTOCOL_ERROR` в handshake-кадре,
`CODEC_ERROR` в TLV) + `close`.

## 2. Типы кадров

| Код | Кадр | Направление | Payload |
|---|---|---|---|
| 0x01 | HELLO | client→server | `int32 protocolVersion`, `str appVersion`, `int32 codecSchemaVersion`, `str clientName`, `bytes32 clientNonce` |
| 0x02 | HELLO_ACK | server→client | `int32 protocolVersion`, `str appVersion`, `uint8 authRequired`, `bytes32 challenge` (нули, если auth не требуется) |
| 0x03 | AUTH_RESPONSE | client→server | `bytes32 clientMac` |
| 0x04 | AUTH_OK | server→client | `bytes32 serverMac` (нули при `authRequired=0`) |
| 0x05 | LOOKUP | client→server | `str path`, `int64 interfaceDigest` |
| 0x06 | LOOKUP_ACK | server→client | `int32 subjectId` (сервер выдаёт строго `≥ 0`; `-1` зарезервировано под ref-форму RGET; отрицательный subjectId → `PROTOCOL_ERROR`) |
| 0x10 | RGET | initiator→executor | см. §3 (грамматика) |
| 0x11 | DONE | executor→initiator | `TLV value` (void → TLV NULL) |
| 0x12 | OTHER | любое | `int32 code`, `str message`, `uint8 hasException`; при `hasException=1` — далее ровно один TLV с тегом `EXCEPTION` (0x15), цепочка causes внутри него; при `hasException=0` — доп. байтов нет |
| 0x13 | CANCEL | initiator→executor | пусто (`callId` — отменяемого вызова, `≠0`) |
| 0x20 | PING | любое | `int64 timestampMillis` (`callId=0`) |
| 0x21 | PONG | любое | `int64 timestampMillis` — побайтовое эхо PING (`callId=0`) |
| 0x30 | REF_RELEASE | client→server | `int32 count`, `int64 refId…` (batch; `callId=0`, без ответа) |

### 2a. Корреляция по `callId`

| Кадр | `callId` |
|---|---|
| RGET, LOOKUP | новый `callId` инициатора |
| DONE, LOOKUP_ACK, `OTHER`-ответ на конкретный вызов | эхо `callId` запроса |
| CANCEL | `callId` отменяемого вызова |
| HELLO, HELLO_ACK, AUTH_RESPONSE, AUTH_OK, handshake-`OTHER` | 0 |
| PING, PONG, REF_RELEASE | 0 |

- `OTHER` с `callId≠0` — ответ на конкретный исходящий вызов.
- `OTHER` с `callId=0` — connection-level ошибка (`VERSION_MISMATCH`, `ACCESS_DENIED` и т.п.), после
  неё инициатор ошибки закрывает соединение.
- Ответ (DONE/LOOKUP_ACK/OTHER) с неизвестным/уже завершённым `callId` — молча отбрасывается (поздний
  ответ отменённого/протухшего вызова), `RmapMetrics.lateAnswerDropped()`.

## 3. Handshake (взаимный HMAC)

```
client                                server
  │── HELLO(…, clientNonce) ───────────▶│  protocolVersion → mismatch: OTHER(VERSION_MISMATCH)+close
  │                                     │  appVersion (equality) → OTHER(APP_VERSION_MISMATCH)+close
  │                                     │  codecSchemaVersion → OTHER(CODEC_MISMATCH)+close
  │◀────────────────────── HELLO_ACK ──│  authRequired=1 если Access.privateKey; challenge=32 rnd байт
  │── AUTH_RESPONSE(clientMac) ────────▶│  clientMac != expected → OTHER(ACCESS_DENIED)+close
  │◀────────────── AUTH_OK(serverMac) ──│  authRequired=0: serverMac=нули, AUTH_RESPONSE пропущен
  │  проверка serverMac (клиент с ключом)│
  │  … сессия установлена: LOOKUP/RGET/PING разрешены …
```

- До `AUTH_OK` сервер принимает ТОЛЬКО HELLO/AUTH_RESPONSE; любой другой кадр →
  `OTHER(PROTOCOL_ERROR)` + close. Таймаут — `handshakeTimeout` (дефолт 10 с) → close.
- **Единственный арбитр** version/app/codec-mismatch — сервер (проверка по HELLO ДО HELLO_ACK).
- **MAC**: `MAC(data) = HMAC-SHA256(accessKey, data)`, канонизация длинно-префиксная (границы полей
  однозначны):
  ```
  authInput = challenge(32) ‖ clientNonce(32)
            ‖ int32BE protocolVersion ‖ int32BE codecSchemaVersion
            ‖ int32BE len(appVersion)  ‖ appVersion(UTF-8)
            ‖ int32BE len(clientName)  ‖ clientName(UTF-8)
  clientMac = MAC("C" ‖ authInput)   // клиент → AUTH_RESPONSE, сервер проверяет
  serverMac = MAC("S" ‖ authInput)   // сервер → AUTH_OK, клиент проверяет
  ```
  Префиксы `"C"`/`"S"` исключают reflection-атаку. `challenge` — 32 случайных байта сервера
  (анти-replay), `clientNonce` — 32 случайных байта клиента (свежесть). Ключ по сети не ходит.
- **Анти-downgrade**: клиент с ключом обязан разорвать соединение, если `HELLO_ACK` пришёл с
  `authRequired=0`; клиент без ключа при `authRequired=1` — тоже разрыв.
- **Граница модели угроз (без TLS в v1)**: взаимный HMAC доказывает владение ключом и защищает от
  rogue-эндпоинта/replay, но НЕ даёт конфиденциальности и НЕ защищает от активного on-path MITM,
  способного подменять байты уже установленной сессии. Защита от враждебных TLV — независимо от auth.
- **Pre-auth лимиты (анти-DoS)**: до `AUTH_OK` — жёсткий `preAuthFrameLimit` (дефолт 4 KiB) вместо
  полного `frameLimit`; сервер держит `maxConcurrentHandshakes` (дефолт 256) и
  `maxConnectionsPerRemote` (дефолт 32) с отбойником на `accept()`.

### Keep-alive и разрыв

PING каждые `keepAliveInterval` (дефолт 15 с) при отсутствии трафика; нет PONG/трафика за
`idleTimeout` (дефолт 45 с) → close. Любой close/TCP-разрыв: все pending-футуры соединения немедленно
завершаются `RmapConnectionException`; `ObjectTable` соединения очищается; клиентский reconnect —
экспоненциальный backoff (1с → ×2 → cap 30с); после reconnect lookup-прокси живы (LOOKUP повторяется
лениво), **ref-прокси мертвы навсегда** (`STALE_REF` при вызове).

## 4. TLV-кодек

### 4.1. Принципы

- **Whitelist на ОБЕИХ сторонах.** Encode: только встроенные типы, boxed-примитивы, классы с
  `@RmapSerializable` или явно зарегистрированные (`.codec(c -> c.serializable(...))`),
  зарегистрированные `ValueCodec<T>`, enum'ы; иначе — ошибка export-time audit. **Decode**:
  приходящий `classRef` с FQN резолвится в `Class` ТОЛЬКО если FQN входит в whitelist, выведенный из
  манифестов экспортированных/залукапленных интерфейсов ПЛЮС явные `.codec(...)`-регистрации; FQN вне
  whitelist → `CODEC_ERROR` + `close` ДО любого `Class.forName`.
- **Детерминированный порядок полей.** Схема класса = не-`static`, не-`transient` поля всей иерархии
  (Object → … → конкретный класс), внутри каждого класса — отсортированы по имени поля. Количество
  полей в wire НЕ пишется (схема известна обеим сторонам — lockstep + `codecSchemaVersion` +
  `interfaceDigest`); несовпадение тега поля с ожидаемым типом → `CODEC_ERROR` + close.
- **Лимит глубины.** Единый `maxDecodeDepth = 32` (константа v1, НЕ конфигурируется) на весь граф TLV
  (OBJECT/LIST/SET/MAP/ARRAY/EXCEPTION); превышение → `CODEC_ERROR` + close. Для EXCEPTION
  дополнительно: causes ≤ 8, stackDepth ≤ 64.
- **Валидация всех размеров.** Каждое `int32` size/len/count: `≥ 0`, ограничено остатком буфера
  кадра, декодер НИКОГДА не пре-аллоцирует по объявленному size (защита от OOM).
  (Ревизия v1.1) STRING (0x0A) в identity-map НЕ участвует — строки всегда кодируются значением.
- **Identity-map per-message** (message = payload одного кадра). Регистрируются: OBJECT, LIST, SET,
  MAP, ARRAY (pre-order, единый счётчик с 0); `BACK_REF(index)`: `0 ≤ index <` текущий размер таблицы.
- **Class-интернирование per-connection** (см. §4.3 ниже). Лимит `maxInternedClasses` (дефолт 4096).

### 4.2. Таблица тегов (uint8)

| Тег | Тип | Payload |
|---|---|---|
| 0x00 | NULL | — |
| 0x01 | TRUE | — |
| 0x02 | FALSE | — |
| 0x03 | BYTE | 1 байт |
| 0x04 | SHORT | 2 |
| 0x05 | INT | 4 |
| 0x06 | LONG | 8 |
| 0x07 | FLOAT | 4 |
| 0x08 | DOUBLE | 8 |
| 0x09 | CHAR | 2 |
| 0x0A | STRING | `int32 len + UTF-8` |
| 0x0B | UUID | 2×int64 |
| 0x0C | ENUM | `classRef + str name` |
| 0x0D | LIST | `int32 size + TLV…` (→ `ArrayList`) |
| 0x0E | SET | `int32 size + TLV…` (→ `LinkedHashSet`) |
| 0x0F | MAP | `int32 size + (TLV key, TLV value)…` (→ `LinkedHashMap`) |
| 0x10 | ARRAY | `classRef componentType + int32 len + TLV…` |
| 0x11 | OBJECT | `classRef + TLV поля по схеме класса` |
| 0x12 | BACK_REF | `int32 index` |
| 0x13 | VALUE_CODEC | `classRef + int32 len + len байт ValueCodec` |
| 0x14 | REMOTE_REF | `int64 refId + classRef интерфейса` |
| 0x15 | EXCEPTION | `str class FQN + str message + int32 stackDepth + (str class, str method, str file, int32 line)… + TLV cause (EXCEPTION\|NULL)` |
| 0x16 | BYTES | `int32 len + len raw байт` (специализация `byte[]`, без per-element тегов) |

- Boxed-обёртки кодируются тегами соответствующих примитивов; `null`-boxed → NULL.
- `byte[]` — тег BYTES, НЕ `ARRAY`-of-BYTE.
- `Optional<T>`/`CompletableFuture<T>` — НЕ кодек-типы; прокси/агент распаковывают по сигнатуре
  метода (§5). В полях DTO запрещены audit'ом.
- VALUE_CODEC несёт `int32 len` защитной рамкой: после `read()` позиция обязана сдвинуться ровно на
  `len` байт, иначе `CODEC_ERROR` + close.

### 4.3. Грамматика `classRef`

`classRef` — позиционное поле внутри составных тегов (ENUM, ARRAY, OBJECT, VALUE_CODEC,
REMOTE_REF), НЕ отдельный TLV-тег:

```
classRef := 0x00, str FQN            // definition: первое вхождение класса в этом направлении
          | 0x01, int32 classId      // reference: повторное вхождение
```

`classId` присваивает **читатель** по порядку definition'ов писателя (не берёт число из провода как
индекс); reference на неопределённый `classId`, либо definition сверх `maxInternedClasses` →
`CODEC_ERROR` + close. Whitelist-проверка — ДО резолва FQN в `Class` (§4.1). Кадр с definition обязан
уйти в outbound РАНЬШЕ любого кадра-reference на этот `classId` (интернирование + enqueue — атомарно
под локом).

## 5. Call-слой

- `client.lookup(path, iface)` возвращает JDK-прокси немедленно; `LOOKUP` уходит лениво при первом
  вызове (и повторно после reconnect); `subjectId` кэшируется на сессию.
- Синхронный метод: RGET → блокировка на future с deadline; `DONE` → значение, `OTHER` →
  исключение, таймаут → `RmapTimeoutException` + best-effort `CANCEL`.
- `CompletableFuture<T>`: future сразу; `cancel(true)` → `CANCEL`. Сервер: CANCEL best-effort (снимает
  из очереди, если не начал исполняться; исполняющийся — не прерывается).
- `Optional<T>`: `DONE(NULL)` → `empty()`, иначе `of(value)`. `void` → `DONE(NULL)`.
- `@RmapExcluded`-метод: прокси НЕ шлёт RGET, локальный `UnsupportedOperationException`.
- deadline едет в RGET (`deadlineMillis`, дефолт `RmapConfig.callTimeout` = 5000 мс); протухло в
  очереди → `OTHER(TIMED_OUT)` без исполнения.

### 5.1. Грамматика RGET

```
RGET.payload := int32 subjectId;
                if subjectId == -1 → int64 refId;   // ref-форма: вызов по remote-ref
                int64 methodId;
                int32 deadlineMillis;                 // ≥ 0; верхний кламп 300000 мс (§7.2)
                uint8 argCount;
                argCount × TLV                        // аргументы
```

`subjectId` читается безусловно; `-1` — сентинел ref-формы (вставное поле `refId` сразу за ним).
`subjectId ≥ 0` → subject-вызов. Отрицательный `subjectId ≠ -1` → `PROTOCOL_ERROR`. `LOOKUP_ACK`
выдаёт только `subjectId ≥ 0`. `deadlineMillis < 0` → `PROTOCOL_ERROR`; значение выше верхнего клампа
`300000` мс (§7.2) молча зажимается исполнителем до клампа (защита от переполнения дедлайн-бюджета).

### 5.2. Исключения через сеть

Исполнение упало → `OTHER(INTERNAL_ERROR)` + `EXCEPTION`-TLV. Клиент бросает `RmapRemoteException`
(unchecked) с message оригинала и синтетическим стектрейсом remote-кадров + локальным хвостом;
оригинальный класс исключения НЕ инстанцируется (никакой десериализации `Throwable`).

### 5.3. Коды `OTHER`

| Код | Значение |
|---|---|
| 1 | VERSION_MISMATCH |
| 2 | APP_VERSION_MISMATCH |
| 3 | CODEC_MISMATCH |
| 4 | ACCESS_DENIED |
| 5 | PROTOCOL_ERROR |
| 6 | SUBJECT_UNDEFINED |
| 7 | DIGEST_MISMATCH |
| 8 | INVALID_SIGNATURE (methodId неизвестен) |
| 9 | TIMED_OUT |
| 10 | INTERNAL_ERROR (+EXCEPTION) |
| 11 | STALE_REF |
| 12 | FRAME_TOO_LARGE |
| 13 | CODEC_ERROR |
| 14 | BACKPRESSURE (best-effort — см. §6) |

## 6. Транспорт

- 1 selector-поток на `RmapServer`/`RmapClient` (I/O only) + worker-pool (дефолт `max(2, cores)`) для
  decode+invoke+encode + 1 общий scheduler-поток (deadlines, lease-sweep, ping, reconnect). Юзер-код
  НИКОГДА не исполняется на selector-потоке.
- **Backpressure (исходящий)**: `outboundLimitBytes` (дефолт 64 MiB). *Ревизия B1*: `OTHER(BACKPRESSURE)`
  — best-effort; при переполненном outbound кадр физически недоставим, транспорт бросает
  `RmapTransportException` отправителю и закрывает без OTHER.
- **Flow-control (входящий)**: `maxInFlightRequests` (дефолт 256) — при достижении снимается
  `OP_READ`, возвращается по завершении вызовов.
- **Гарантии для endpoint-кода**: вызовы `impl` исполняются на воркерах КОНКУРЕНТНО; per-connection
  ordering НЕ гарантируется — impl обязан быть thread-safe. Opt-in `ExportOptions.serialDispatch()` —
  строго последовательная диспетчеризация одного subject.
- `closeFlushTimeout` (дефолт 5 с) — жёсткий дедлайн close-after-flush: hostile-пир, переставший
  читать, не держит соединение вечно.

## 7. Remote-refs (ObjectTable)

Per-connection `ObjectTable`: `refId(int64, счётчик с 1) → {strong ref, interface class,
lastAccessMillis}` + обратная identity-map (повторный объект → тот же `refId`). Выдача: возврат типа
из `wrapReturnAsRemote` (не под `@Snapshot`) → регистрация + `REMOTE_REF`-TLV; ref-тип как элемент
коллекции — per-element REMOTE_REF. Lease — `refLeaseTimeout` (дефолт 10 мин) без обращений → сервер
удаляет; вызов по умершему ref → `OTHER(STALE_REF)` → `RmapStaleRefException`. `REF_RELEASE` с
неизвестным `refId` (гонка release↔lease-expiry) — молча игнорируется + метрика. Разрыв соединения →
таблица очищается целиком; ref-прокси после reconnect — `STALE_REF` навсегда.

## 8. Method-id, interface digest

- `methodId (int64)` = первые 8 байт `SHA-256("<methodName><jvmDescriptor>")`.
- `interfaceDigest (int64)` = первые 8 байт `SHA-256` конкатенации отсортированных
  `"<methodName><descriptor>"` не-исключённых (`@RmapExcluded`) методов интерфейса; неравенство на
  `LOOKUP` → `OTHER(DIGEST_MISMATCH)`.

## 9. Лимиты и дефолты (сводка)

| Параметр | Дефолт |
|---|---|
| `keepAliveInterval` | 15 с |
| `idleTimeout` | 45 с |
| `handshakeTimeout` | 10 с |
| `callTimeout` | 5 с |
| `frameLimit` | 8 MiB |
| `preAuthFrameLimit` | 4 KiB |
| `maxConcurrentHandshakes` | 256 |
| `maxConnectionsPerRemote` | 32 |
| `maxInFlightRequests` | 256 |
| `outboundLimitBytes` | 64 MiB |
| `maxInternedClasses` | 4096 |
| `refLeaseTimeout` | 10 мин |
| `closeFlushTimeout` | 5 с |
| `maxDecodeDepth` | 32 (константа, не конфигурируется) |

## 10. Ревизии относительно первоначального проекта

- **STRING вне identity-map** (v1.1): дедупликация длинных строк редка, регистрация строк усложняла
  бы `BACK_REF`-индексацию — строки всегда кодируются значением.
- **`OTHER(BACKPRESSURE)` — best-effort**, не гарантированная доставка: при физически недоставимом
  кадре (переполненный outbound) транспорт закрывает соединение без попытки отправить OTHER.
- **`maxDecodeDepth` — константа v1** (32), НЕ поле `RmapConfig`: анти-DoS-дефолт зашит в кодек,
  конфигурируемость отложена (нет практической потребности сузить/расширить в v1).
