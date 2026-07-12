package me.moonways.rmap.rpc;

import me.moonways.rmap.api.ProtocolVersion;
import me.moonways.rmap.transport.ConnectionListener;
import me.moonways.rmap.transport.HandshakeCodec;
import me.moonways.rmap.transport.NioTransport;
import me.moonways.rmap.transport.RmapConfig;
import me.moonways.rmap.transport.RmapConnection;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameType;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Сырой клиент wire-уровня для тестов серверного call-слоя (задача 3). Проходит клиентскую
 * ветку publicAccess-handshake (шлёт HELLO, ждёт AUTH_OK — §9-reorder-устойчиво), после
 * аутентификации складывает ВСЕ входящие кадры в {@link LinkedBlockingQueue}. Клиентского
 * прокси ещё нет, поэтому RGET/LOOKUP собираются вручную через {@link CallWire}.
 */
final class RawRpcClient {

    private static final long AWAIT_MILLIS = 5000L;

    private final NioTransport transport;
    private final RmapConnection conn;
    private final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
    private final CountDownLatch authLatch = new CountDownLatch(1);
    private final CountDownLatch closedLatch = new CountDownLatch(1);
    private volatile boolean authenticated;

    private RawRpcClient(NioTransport transport, RmapConnection conn) {
        this.transport = transport;
        this.conn = conn;
    }

    static RawRpcClient connect(int port, RmapConfig cfg) throws Exception {
        NioTransport transport = NioTransport.clientTransport(cfg);
        final RawRpcClient[] box = new RawRpcClient[1];
        ConnectionListener listener = new ConnectionListener() {
            @Override
            public void onOpened(RmapConnection c) {
                // клиент шлёт HELLO (publicAccess: authRequired=0, AUTH_RESPONSE не требуется).
                HandshakeCodec.Hello hello = new HandshakeCodec.Hello(
                        ProtocolVersion.PROTOCOL_VERSION, cfg.getAppVersion(),
                        ProtocolVersion.CODEC_SCHEMA_VERSION, cfg.getClientName(), new byte[32]);
                c.send(new Frame(FrameType.HELLO, 0L, HandshakeCodec.encodeHello(hello)));
            }

            @Override
            public void onFrame(RmapConnection c, Frame f) {
                RawRpcClient self = box[0];
                if (!self.authenticated) {
                    // §9-reorder: публичная ветка сервера шлёт HELLO_ACK и AUTH_OK back-to-back;
                    // AUTH_OK достаточно (AUTH_RESPONSE публичный клиент не шлёт).
                    if (f.getType() == FrameType.AUTH_OK) {
                        self.authenticated = true;
                        c.setAuthenticated(true);
                        c.setFrameLimitFull();
                        self.authLatch.countDown();
                    }
                    return;
                }
                self.frames.add(f);
            }

            @Override
            public void onClosed(RmapConnection c, Throwable cause) {
                RawRpcClient self = box[0];
                if (self != null) {
                    self.closedLatch.countDown();
                }
            }
        };
        RmapConnection conn = transport.connect("127.0.0.1", port, cfg, listener);
        RawRpcClient client = new RawRpcClient(transport, conn);
        box[0] = client;
        if (!client.authLatch.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS)) {
            transport.stop();
            throw new AssertionError("raw client did not authenticate within " + AWAIT_MILLIS + "ms");
        }
        return client;
    }

    NioTransport transport() {
        return transport;
    }

    CountDownLatch closedLatch() {
        return closedLatch;
    }

    void send(FrameType type, long callId, byte[] payload) {
        conn.send(new Frame(type, callId, payload));
    }

    /** Ждать кадр типа {@code type} ≤5с, пропуская keep-alive PING/PONG (и любые иные кадры). */
    Frame await(FrameType type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
        while (true) {
            long left = deadline - System.currentTimeMillis();
            Frame f = left <= 0 ? null : frames.poll(left, TimeUnit.MILLISECONDS);
            if (f == null) {
                throw new AssertionError("no frame of type " + type + " within " + AWAIT_MILLIS + "ms");
            }
            if (f.getType() == FrameType.PING || f.getType() == FrameType.PONG) {
                continue;
            }
            if (f.getType() == type) {
                return f;
            }
            // иной кадр — не тот, что ждём (напр. поздний DONE перед OTHER): пропускаем.
        }
    }

    /** Ждать OTHER-кадр c конкретным {@code callId} ≤5с (пропуская всё прочее). */
    Frame awaitOtherWithCallId(long callId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
        while (true) {
            long left = deadline - System.currentTimeMillis();
            Frame f = left <= 0 ? null : frames.poll(left, TimeUnit.MILLISECONDS);
            if (f == null) {
                throw new AssertionError("no OTHER frame with callId " + callId + " within " + AWAIT_MILLIS + "ms");
            }
            if (f.getType() == FrameType.OTHER && f.getCallId() == callId) {
                return f;
            }
        }
    }
}
