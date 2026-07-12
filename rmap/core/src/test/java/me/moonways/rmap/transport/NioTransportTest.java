package me.moonways.rmap.transport;

import me.moonways.rmap.codec.RmapByteReader;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameType;
import me.moonways.rmap.wire.OtherCode;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class NioTransportTest {

    private RmapConfig cfg() {
        return RmapConfig.builder()
                .access(Access.publicAccess())
                .appVersion("test-1")
                .clientName("c1")
                .build();
    }

    @Test
    void frame_sent_by_client_arrives_at_server() throws Exception {
        CountDownLatch got = new CountDownLatch(1);
        AtomicReference<Frame> received = new AtomicReference<>();

        // сервер эхо-логику не делает — просто фиксирует первый кадр
        ConnectionListener serverListener = new ConnectionListener() {
            public void onOpened(RmapConnection c) { }
            public void onFrame(RmapConnection c, Frame f) { received.set(f); got.countDown(); }
            public void onClosed(RmapConnection c, Throwable t) { }
        };
        NioTransport server = NioTransport.startServer(new InetSocketAddress("127.0.0.1", 0), cfg(), serverListener);
        int port = server.boundPort();

        ConnectionListener clientListener = new ConnectionListener() {
            public void onOpened(RmapConnection c) {
                c.send(new Frame(FrameType.PING, 7L, new byte[]{1, 2, 3}));
            }
            public void onFrame(RmapConnection c, Frame f) { }
            public void onClosed(RmapConnection c, Throwable t) { }
        };
        NioTransport clientTransport = NioTransport.clientTransport(cfg());
        clientTransport.connect("127.0.0.1", port, cfg(), clientListener);

        assertThat(got.await(5, TimeUnit.SECONDS)).as("кадр дошёл").isTrue();
        assertThat(received.get().getType()).isEqualTo(FrameType.PING);
        assertThat(received.get().getCallId()).isEqualTo(7L);
        assertThat(received.get().getPayload()).containsExactly(1, 2, 3);

        clientTransport.stop();
        server.stop();
    }

    @Test
    void oversized_frame_is_rejected_and_connection_closed() throws Exception {
        // сервер с крошечным frameLimit; клиент шлёт кадр больше лимита → сервер шлёт
        // OTHER(FRAME_TOO_LARGE) и закрывает. Клиент ДОЛЖЕН получить OTHER до/на закрытии
        // (прямой лок Critical-фикса close-after-flush).
        RmapConfig tiny = RmapConfig.builder().access(Access.publicAccess())
                .appVersion("t").clientName("c").frameLimit(64).preAuthFrameLimit(64).build();
        CountDownLatch serverClosed = new CountDownLatch(1);
        ConnectionListener serverListener = new ConnectionListener() {
            public void onOpened(RmapConnection c) { }
            public void onFrame(RmapConnection c, Frame f) { }
            public void onClosed(RmapConnection c, Throwable t) { serverClosed.countDown(); }
        };
        NioTransport server = NioTransport.startServer(new InetSocketAddress("127.0.0.1", 0), tiny, serverListener);

        CountDownLatch otherReceived = new CountDownLatch(1);
        CountDownLatch clientClosed = new CountDownLatch(1);
        AtomicInteger otherCode = new AtomicInteger(-1);
        AtomicReference<Boolean> otherSeenAtClose = new AtomicReference<>();
        NioTransport clientTransport = NioTransport.clientTransport(tiny);
        clientTransport.connect("127.0.0.1", server.boundPort(), tiny, new ConnectionListener() {
            public void onOpened(RmapConnection c) {
                c.send(new Frame(FrameType.PING, 0L, new byte[200])); // > 64
            }
            public void onFrame(RmapConnection c, Frame f) {
                if (f.getType() == FrameType.OTHER) {
                    otherCode.set(new RmapByteReader(f.getPayload(), 0, f.getPayload().length).readInt());
                    otherReceived.countDown();
                }
            }
            public void onClosed(RmapConnection c, Throwable t) {
                // §9: onClosed может гоняться с in-flight onFrame — ждём кадр (грейс),
                // как это делает строгая state-машина хендшейка. В успехе вернётся сразу.
                try { otherSeenAtClose.set(otherReceived.await(3, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                clientClosed.countDown();
            }
        });

        assertThat(otherReceived.await(10, TimeUnit.SECONDS))
                .as("клиент получил OTHER-кадр от сервера").isTrue();
        assertThat(otherCode.get()).as("код OTHER = FRAME_TOO_LARGE").isEqualTo(OtherCode.FRAME_TOO_LARGE);
        assertThat(clientClosed.await(10, TimeUnit.SECONDS)).as("клиент закрыт").isTrue();
        assertThat(otherSeenAtClose.get()).as("OTHER доставлен ДО onClosed").isTrue();
        assertThat(serverClosed.await(10, TimeUnit.SECONDS)).as("сервер закрыл соединение").isTrue();

        clientTransport.stop();
        server.stop();
    }

    @Test
    void malformed_short_frame_length_is_rejected_with_protocol_error() throws Exception {
        // §6: len в диапазоне 0..8 (< HEADER_AFTER_LEN=9) раньше проходил проверку и вёл к
        // new byte[len-9] = NegativeArraySizeException на selector-потоке. Теперь сервер шлёт
        // OTHER(PROTOCOL_ERROR) и закрывает. Сырой java.net.Socket — минуя фасад/state-машину.
        ConnectionListener serverListener = new ConnectionListener() {
            public void onOpened(RmapConnection c) { }
            public void onFrame(RmapConnection c, Frame f) { }
            public void onClosed(RmapConnection c, Throwable t) { }
        };
        NioTransport server = NioTransport.startServer(new InetSocketAddress("127.0.0.1", 0), cfg(), serverListener);
        try (java.net.Socket sock = new java.net.Socket("127.0.0.1", server.boundPort())) {
            // [int32 len=3][мусор] — big-endian длина 3 меньше заголовка (9) ⇒ малформ.
            sock.getOutputStream().write(new byte[]{0, 0, 0, 3, (byte) 0xAA, (byte) 0xBB, (byte) 0xCC});
            sock.getOutputStream().flush();

            java.io.DataInputStream in = new java.io.DataInputStream(sock.getInputStream());
            int len = in.readInt(); // big-endian длина ответного кадра
            assertThat(len).isGreaterThanOrEqualTo(me.moonways.rmap.wire.FrameCodec.HEADER_AFTER_LEN);
            byte[] rest = new byte[len];
            in.readFully(rest);
            int type = rest[0] & 0xFF;
            assertThat(type).as("ответный кадр — OTHER").isEqualTo(FrameType.OTHER.code());
            byte[] payload = java.util.Arrays.copyOfRange(rest,
                    me.moonways.rmap.wire.FrameCodec.HEADER_AFTER_LEN, len);
            int code = new RmapByteReader(payload, 0, payload.length).readInt();
            assertThat(code).as("код OTHER = PROTOCOL_ERROR").isEqualTo(OtherCode.PROTOCOL_ERROR);
        } finally {
            server.stop();
        }
    }

    @Test
    void double_close_delivers_onClosed_exactly_once() throws Exception {
        // Два вызова close() (два queued doClose-task'а) → doClose идемпотентен → onClosed ровно раз.
        AtomicInteger onClosedCount = new AtomicInteger(0);
        CountDownLatch closedOnce = new CountDownLatch(1);
        ConnectionListener serverListener = new ConnectionListener() {
            public void onOpened(RmapConnection c) { }
            public void onFrame(RmapConnection c, Frame f) { }
            public void onClosed(RmapConnection c, Throwable t) { }
        };
        NioTransport server = NioTransport.startServer(new InetSocketAddress("127.0.0.1", 0), cfg(), serverListener);

        NioTransport clientTransport = NioTransport.clientTransport(cfg());
        clientTransport.connect("127.0.0.1", server.boundPort(), cfg(), new ConnectionListener() {
            public void onOpened(RmapConnection c) {
                c.close();
                c.close(); // повторный close: второй doClose должен быть no-op
            }
            public void onFrame(RmapConnection c, Frame f) { }
            public void onClosed(RmapConnection c, Throwable t) {
                onClosedCount.incrementAndGet();
                closedOnce.countDown();
            }
        });

        assertThat(closedOnce.await(10, TimeUnit.SECONDS)).as("клиент получил onClosed").isTrue();
        Thread.sleep(400); // дать возможному второму onClosed шанс прилететь
        assertThat(onClosedCount.get()).as("onClosed ровно один раз").isEqualTo(1);

        clientTransport.stop();
        server.stop();
    }
}
