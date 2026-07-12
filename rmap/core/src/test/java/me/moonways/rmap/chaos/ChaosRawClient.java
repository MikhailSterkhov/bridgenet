package me.moonways.rmap.chaos;

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
 * Сырой authenticated-клиент wire-уровня для chaos/adversarial-тестов. Проходит клиентскую ветку
 * publicAccess-handshake, после AUTH_OK складывает входящие кадры в очередь. Позволяет отправлять
 * ПРОИЗВОЛЬНЫЕ (в т.ч. враждебные) payload'ы поверх корректного кадрирования — так проверяется, что
 * малформ TLV даёт OTHER(CODEC_ERROR|PROTOCOL_ERROR)+close, а не зависание/OOM/падение selector'а.
 */
final class ChaosRawClient {

    private static final long AWAIT_MILLIS = 5000L;

    private final NioTransport transport;
    private final RmapConnection conn;
    private final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
    private final CountDownLatch authLatch = new CountDownLatch(1);
    private final CountDownLatch closedLatch = new CountDownLatch(1);
    private volatile boolean authenticated;

    private ChaosRawClient(NioTransport transport, RmapConnection conn) {
        this.transport = transport;
        this.conn = conn;
    }

    static ChaosRawClient connect(int port, RmapConfig cfg) throws Exception {
        NioTransport transport = NioTransport.clientTransport(cfg);
        final ChaosRawClient[] box = new ChaosRawClient[1];
        ConnectionListener listener = new ConnectionListener() {
            @Override
            public void onOpened(RmapConnection c) {
                HandshakeCodec.Hello hello = new HandshakeCodec.Hello(
                        ProtocolVersion.PROTOCOL_VERSION, cfg.getAppVersion(),
                        ProtocolVersion.CODEC_SCHEMA_VERSION, cfg.getClientName(), new byte[32]);
                c.send(new Frame(FrameType.HELLO, 0L, HandshakeCodec.encodeHello(hello)));
            }

            @Override
            public void onFrame(RmapConnection c, Frame f) {
                ChaosRawClient self = box[0];
                if (!self.authenticated) {
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
                ChaosRawClient self = box[0];
                if (self != null) {
                    self.closedLatch.countDown();
                }
            }
        };
        RmapConnection conn = transport.connect("127.0.0.1", port, cfg, listener);
        ChaosRawClient client = new ChaosRawClient(transport, conn);
        box[0] = client;
        if (!client.authLatch.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS)) {
            transport.stop();
            throw new AssertionError("chaos raw client did not authenticate within " + AWAIT_MILLIS + "ms");
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

    /** Ждать кадр типа {@code type} ≤5с, пропуская keep-alive PING/PONG. */
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
        }
    }

    /** Ждать кадр РОВНО типа {@code type} ≤5с, НЕ трактуя PING/PONG как шум (для проверки эхо-PONG). */
    Frame awaitType(FrameType type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
        while (true) {
            long left = deadline - System.currentTimeMillis();
            Frame f = left <= 0 ? null : frames.poll(left, TimeUnit.MILLISECONDS);
            if (f == null) {
                throw new AssertionError("no frame of type " + type + " within " + AWAIT_MILLIS + "ms");
            }
            if (f.getType() == type) {
                return f;
            }
        }
    }

    void stop() {
        transport.stop();
    }
}
