package me.moonways.rmap.transport;

import me.moonways.rmap.api.ProtocolVersion;
import me.moonways.rmap.auth.HmacAuth;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameType;
import me.moonways.rmap.wire.OtherCode;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Per-connection машина взаимного HMAC-handshake (спека §4.3). Хранится в
 * {@code connection.attachment}. Строго последовательна и внутренне синхронизирована:
 * транспорт диспатчит кадры конкурентно и без гарантии порядка (§9), поэтому весь разбор
 * идёт под {@code synchronized}, а неожиданный для текущего состояния кадр рвёт соединение
 * с {@code PROTOCOL_ERROR} (§4.2a).
 *
 * <p>Сервер — единственный арбитр version/app/codec-mismatch (проверяет HELLO до HELLO_ACK).
 * Anti-downgrade — обе стороны. MAC-сравнения — только {@link HmacAuth#constantTimeEquals}.
 */
public final class HandshakeState {

    private enum State { WAIT_HELLO, WAIT_HELLO_ACK, WAIT_AUTH_RESPONSE, WAIT_AUTH_OK, AUTHENTICATED, FAILED }

    private static final byte[] ZERO_32 = new byte[32];

    private final RmapConnection connection;
    private final RmapConfig config;
    private final boolean serverSide;
    private final Runnable onAuthenticated;
    private final Consumer<Throwable> onFailed;
    /** true ⇒ текущая попытка уже брошена (connect-future зафейлен по handshake-timeout, §4). */
    private final BooleanSupplier staleAttempt;

    private State state;

    // client-side
    private byte[] clientNonce;
    private byte[] expectedServerMac;
    /** §9-reorder: AUTH_OK, пришедший раньше HELLO_ACK (публичная ветка сервера шлёт их back-to-back). */
    private Frame pendingAuthOk;
    // server-side
    private HmacAuth.AuthInput serverAuthInput;

    /** §5: user-коллбек (onAuthenticated), собранный под монитором для запуска СНАРУЖИ него. */
    private Runnable pendingUserCallback;

    private volatile long lastInboundMillis = System.currentTimeMillis();

    public HandshakeState(RmapConnection connection, Runnable onAuthenticated, Consumer<Throwable> onFailed) {
        this(connection, onAuthenticated, onFailed, () -> false);
    }

    public HandshakeState(RmapConnection connection, Runnable onAuthenticated, Consumer<Throwable> onFailed,
                          BooleanSupplier staleAttempt) {
        this.connection = connection;
        this.config = connection.config();
        this.serverSide = connection.isServerSide();
        this.onAuthenticated = onAuthenticated;
        this.onFailed = onFailed;
        this.staleAttempt = staleAttempt;
    }

    /** Отметка входящей активности (для idle-close). Вызывается фасадом на каждый кадр. */
    public void touchInbound() {
        lastInboundMillis = System.currentTimeMillis();
    }

    public long lastInboundMillis() {
        return lastInboundMillis;
    }

    /** Старт handshake. Клиент шлёт HELLO; сервер ждёт HELLO. */
    public synchronized void start() {
        if (serverSide) {
            state = State.WAIT_HELLO;
        } else {
            clientNonce = HmacAuth.randomBytes32();
            state = State.WAIT_HELLO_ACK;
            HandshakeCodec.Hello hello = new HandshakeCodec.Hello(
                    ProtocolVersion.PROTOCOL_VERSION, config.getAppVersion(),
                    ProtocolVersion.CODEC_SCHEMA_VERSION, config.getClientName(), clientNonce);
            connection.send(new Frame(FrameType.HELLO, 0L, HandshakeCodec.encodeHello(hello)));
        }
    }

    /**
     * Обработка handshake-кадра. Разбор — под {@code synchronized} (строгая state-машина, §9);
     * собранный user-коллбек (onAuthenticated) выполняется СНАРУЖИ монитора (§5, deadlock-класс
     * при реентерабельном user-коде).
     */
    public void onFrame(RmapConnection conn, Frame frame) {
        Runnable post;
        synchronized (this) {
            handleFrameLocked(conn, frame);
            post = pendingUserCallback;
            pendingUserCallback = null;
        }
        if (post != null) {
            post.run();
        }
    }

    private void handleFrameLocked(RmapConnection conn, Frame frame) {
        touchInbound();
        if (state == State.AUTHENTICATED || state == State.FAILED) {
            return; // терминальные состояния — игнорируем «поздние» кадры
        }
        FrameType type = frame.getType();
        if (type == FrameType.OTHER) {
            handleOther(frame);
            return;
        }
        switch (state) {
            case WAIT_HELLO:
                if (type != FrameType.HELLO) { protocolError(type); return; }
                handleHello(frame);
                break;
            case WAIT_HELLO_ACK:
                // §9: транспорт диспатчит кадры без per-connection ordering — публичная ветка
                // сервера шлёт HELLO_ACK и AUTH_OK back-to-back, и AUTH_OK может прийти первым.
                // Это НЕ ошибка: стэшируем AUTH_OK и обработаем сразу после HELLO_ACK.
                if (type == FrameType.AUTH_OK) { pendingAuthOk = frame; return; }
                if (type != FrameType.HELLO_ACK) { protocolError(type); return; }
                handleHelloAck(frame);
                if (state == State.WAIT_AUTH_OK && pendingAuthOk != null) {
                    Frame stashed = pendingAuthOk;
                    pendingAuthOk = null;
                    handleAuthOk(stashed);
                }
                break;
            case WAIT_AUTH_RESPONSE:
                if (type != FrameType.AUTH_RESPONSE) { protocolError(type); return; }
                handleAuthResponse(frame);
                break;
            case WAIT_AUTH_OK:
                if (type != FrameType.AUTH_OK) { protocolError(type); return; }
                handleAuthOk(frame);
                break;
            default:
                protocolError(type);
        }
    }

    // ---- сервер ----

    private void handleHello(Frame frame) {
        HandshakeCodec.Hello h = HandshakeCodec.decodeHello(frame.getPayload());
        // Сервер — единственный арбитр version/app/codec (проверка ДО HELLO_ACK).
        if (h.getProtocolVersion() != ProtocolVersion.PROTOCOL_VERSION) {
            fatalServer(OtherCode.VERSION_MISMATCH, "protocol version " + h.getProtocolVersion());
            return;
        }
        if (!config.getAppVersion().equals(h.getAppVersion())) {
            fatalServer(OtherCode.APP_VERSION_MISMATCH, "app version " + h.getAppVersion());
            return;
        }
        if (h.getCodecSchemaVersion() != ProtocolVersion.CODEC_SCHEMA_VERSION) {
            fatalServer(OtherCode.CODEC_MISMATCH, "codec schema " + h.getCodecSchemaVersion());
            return;
        }
        Access access = config.getAccess();
        if (access.isPrivate()) {
            byte[] challenge = HmacAuth.randomBytes32();
            // AuthInput канонизируется одинаково обеими сторонами (§4.3): значения — из HELLO
            // (после валидации совпадают с ожидаемыми), challenge — наш.
            serverAuthInput = new HmacAuth.AuthInput(challenge, h.getClientNonce(),
                    h.getProtocolVersion(), h.getCodecSchemaVersion(), h.getAppVersion(), h.getClientName());
            HandshakeCodec.HelloAck ack = new HandshakeCodec.HelloAck(
                    ProtocolVersion.PROTOCOL_VERSION, config.getAppVersion(), true, challenge);
            connection.send(new Frame(FrameType.HELLO_ACK, 0L, HandshakeCodec.encodeHelloAck(ack)));
            state = State.WAIT_AUTH_RESPONSE;
        } else {
            // публичный доступ: HELLO_ACK(authRequired=0, нули) + сразу AUTH_OK(нули).
            HandshakeCodec.HelloAck ack = new HandshakeCodec.HelloAck(
                    ProtocolVersion.PROTOCOL_VERSION, config.getAppVersion(), false, ZERO_32);
            connection.send(new Frame(FrameType.HELLO_ACK, 0L, HandshakeCodec.encodeHelloAck(ack)));
            connection.send(new Frame(FrameType.AUTH_OK, 0L, HandshakeCodec.encodeMac32(ZERO_32)));
            markAuthenticated();
        }
    }

    private void handleAuthResponse(Frame frame) {
        byte[] clientMac = HandshakeCodec.decodeMac32(frame.getPayload());
        byte[] expected = HmacAuth.clientMac(config.getAccess().key(), serverAuthInput);
        if (!HmacAuth.constantTimeEquals(clientMac, expected)) {
            fatalServer(OtherCode.ACCESS_DENIED, "client mac mismatch");
            return;
        }
        byte[] serverMac = HmacAuth.serverMac(config.getAccess().key(), serverAuthInput);
        connection.send(new Frame(FrameType.AUTH_OK, 0L, HandshakeCodec.encodeMac32(serverMac)));
        markAuthenticated();
    }

    // ---- клиент ----

    private void handleHelloAck(Frame frame) {
        HandshakeCodec.HelloAck a = HandshakeCodec.decodeHelloAck(frame.getPayload());
        Access access = config.getAccess();
        // anti-downgrade (обе стороны, §4.3).
        if (!a.isAuthRequired() && access.isPrivate()) {
            failClient(OtherCode.PROTOCOL_ERROR,
                    "anti-downgrade: server requires no auth but client holds a private key");
            return;
        }
        if (a.isAuthRequired() && !access.isPrivate()) {
            failClient(OtherCode.PROTOCOL_ERROR,
                    "anti-downgrade: server requires auth but client is public");
            return;
        }
        if (access.isPrivate()) {
            HmacAuth.AuthInput in = new HmacAuth.AuthInput(a.getChallenge(), clientNonce,
                    ProtocolVersion.PROTOCOL_VERSION, ProtocolVersion.CODEC_SCHEMA_VERSION,
                    config.getAppVersion(), config.getClientName());
            byte[] clientMac = HmacAuth.clientMac(access.key(), in);
            expectedServerMac = HmacAuth.serverMac(access.key(), in);
            connection.send(new Frame(FrameType.AUTH_RESPONSE, 0L, HandshakeCodec.encodeMac32(clientMac)));
            state = State.WAIT_AUTH_OK;
        } else {
            // публичный доступ: AUTH_RESPONSE не шлём, ждём AUTH_OK(нули).
            state = State.WAIT_AUTH_OK;
        }
    }

    private void handleAuthOk(Frame frame) {
        if (config.getAccess().isPrivate()) {
            byte[] serverMac = HandshakeCodec.decodeMac32(frame.getPayload());
            if (!HmacAuth.constantTimeEquals(serverMac, expectedServerMac)) {
                // rogue-server: сервер не знает ключ → неверный serverMac.
                failClient(OtherCode.ACCESS_DENIED, "server mac mismatch (rogue server)");
                return;
            }
        }
        markAuthenticated();
    }

    // ---- общее ----

    private void markAuthenticated() {
        // §4: поздний AUTH_OK не «оживляет» протухшее соединение. Если попытка уже брошена
        // (connect-future зафейлен по handshake-timeout) — не помечаем authenticated и не
        // стартуем keep-alive, а закрываем сокет.
        if (staleAttempt.getAsBoolean()) {
            state = State.FAILED;
            connection.close();
            return;
        }
        state = State.AUTHENTICATED;
        connection.setAuthenticated(true);
        connection.setFrameLimitFull();
        // §5: user-коллбек выполняем СНАРУЖИ монитора (см. onFrame).
        pendingUserCallback = onAuthenticated;
    }

    /** Клиент получил OTHER (connection-level ошибка, §4.2a) — фейлит connect-future именем кода. */
    private void handleOther(Frame frame) {
        int code = OtherCode.PROTOCOL_ERROR;
        String message = "";
        try {
            me.moonways.rmap.codec.RmapByteReader r =
                    new me.moonways.rmap.codec.RmapByteReader(frame.getPayload(), 0, frame.getPayload().length);
            code = r.readInt();
            message = r.readStr();
            // uint8 hasException — в B1 игнорируем
        } catch (RuntimeException ignored) {
            // повреждённый OTHER — трактуем как PROTOCOL_ERROR
        }
        state = State.FAILED;
        if (!serverSide) {
            onFailed.accept(new RmapTransportException(OtherCode.name(code) + ": " + message));
        }
        connection.close(); // сервер уже закрывается после отправки OTHER — просто чистим локально
    }

    /** Сервер отвергает соединение: OTHER(code) + close (§4.3). Future нет. */
    private void fatalServer(int code, String message) {
        state = State.FAILED;
        connection.close(code, message);
    }

    /** Клиент детектировал нарушение: фейлит future и рвёт соединение. */
    private void failClient(int code, String message) {
        state = State.FAILED;
        onFailed.accept(new RmapTransportException(OtherCode.name(code) + ": " + message));
        connection.close(code, message);
    }

    private void protocolError(FrameType unexpected) {
        String message = "unexpected frame " + unexpected + " during handshake";
        state = State.FAILED;
        if (!serverSide) {
            onFailed.accept(new RmapTransportException(OtherCode.name(OtherCode.PROTOCOL_ERROR) + ": " + message));
        }
        connection.close(OtherCode.PROTOCOL_ERROR, message);
    }
}
