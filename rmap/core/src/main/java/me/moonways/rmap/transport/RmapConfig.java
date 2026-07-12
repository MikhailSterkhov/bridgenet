package me.moonways.rmap.transport;

import lombok.Builder;
import lombok.Value;
import me.moonways.rmap.codec.CodecRegistry;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Конфиг транспорта RMAP (спека §9, §11). Дефолты — в builder'е. */
@Value
@Builder
public class RmapConfig {

    Access access;
    String appVersion;
    String clientName;

    @Builder.Default Duration keepAliveInterval = Duration.ofSeconds(15);
    @Builder.Default Duration idleTimeout = Duration.ofSeconds(45);
    @Builder.Default Duration handshakeTimeout = Duration.ofSeconds(10);
    @Builder.Default Duration callTimeout = Duration.ofSeconds(5);
    @Builder.Default int frameLimit = 8 * 1024 * 1024;
    @Builder.Default int preAuthFrameLimit = 4 * 1024;
    @Builder.Default int maxConcurrentHandshakes = 256;
    @Builder.Default int maxConnectionsPerRemote = 32;
    @Builder.Default int maxInFlightRequests = 256;
    @Builder.Default long outboundLimitBytes = 64L * 1024 * 1024;

    /** Конфигуратор кодека (§5.3, §11): {@code c -> c.register(new XCodec()).serializable(Y.class)}.
     *  Вызывается фасадом РОВНО ОДИН РАЗ на создание per-endpoint {@code CodecRegistry}, ДО первого
     *  {@code export}/{@code lookup}. Дефолт — no-op (только 4 встроенных платформенных кодека). */
    @Builder.Default Consumer<CodecRegistry> codec = registry -> { };

    /** Пул завершения клиентских future (§9): блокирующий continuation юзера не стопорит decode.
     *  {@code null} (дефолт) → фасад создаёт свой 2-поточный daemon-пул и владеет его lifecycle
     *  (закрывает в {@code close()}); явно переданный — lifecycle остаётся за вызывающим. */
    Executor callbackExecutor;

    /** Лимит class-интернирования на соединение (§5.2a) — анти-DoS от спама уникальными FQN. */
    @Builder.Default int maxInternedClasses = 4096;

    /** Порог lease remote-ref'ов без обращений (§10) — по истечении сервер эвиктит запись
     *  ObjectTable, обращение к протухшему ref → {@code OTHER(STALE_REF)}. */
    @Builder.Default Duration refLeaseTimeout = Duration.ofMinutes(10);

    /** Жёсткий дедлайн close-after-flush (§4): hostile-пир, переставший читать, не держит
     *  соединение вечно — по истечении close форсируется, даже если outbound не слился. */
    @Builder.Default Duration closeFlushTimeout = Duration.ofSeconds(5);

    // v1: maxDecodeDepth — НЕ конфигурируемое поле. MAX_DEPTH=32 (спека §5.1) зашит константой
    // в RmapCodec (анти-DoS от LIST→LIST→… переполнения стека); см. PROTOCOL.md.
}
