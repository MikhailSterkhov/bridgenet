package me.moonways.rmap.transport;

import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameType;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
        // сервер с крошечным frameLimit; клиент шлёт кадр больше лимита → сервер закрывает
        RmapConfig tiny = RmapConfig.builder().access(Access.publicAccess())
                .appVersion("t").clientName("c").frameLimit(64).preAuthFrameLimit(64).build();
        CountDownLatch closed = new CountDownLatch(1);
        ConnectionListener serverListener = new ConnectionListener() {
            public void onOpened(RmapConnection c) { }
            public void onFrame(RmapConnection c, Frame f) { }
            public void onClosed(RmapConnection c, Throwable t) { closed.countDown(); }
        };
        NioTransport server = NioTransport.startServer(new InetSocketAddress("127.0.0.1", 0), tiny, serverListener);
        NioTransport clientTransport = NioTransport.clientTransport(tiny);
        clientTransport.connect("127.0.0.1", server.boundPort(), tiny, new ConnectionListener() {
            public void onOpened(RmapConnection c) {
                c.send(new Frame(FrameType.PING, 0L, new byte[200])); // > 64
            }
            public void onFrame(RmapConnection c, Frame f) { }
            public void onClosed(RmapConnection c, Throwable t) { }
        });
        assertThat(closed.await(5, TimeUnit.SECONDS)).as("сервер закрыл соединение").isTrue();
        clientTransport.stop();
        server.stop();
    }
}
