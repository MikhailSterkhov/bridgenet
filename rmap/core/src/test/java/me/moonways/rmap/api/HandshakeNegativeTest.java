package me.moonways.rmap.api;

import me.moonways.rmap.auth.HmacAuth;
import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.ConnectionListener;
import me.moonways.rmap.transport.HandshakeCodec;
import me.moonways.rmap.transport.NioTransport;
import me.moonways.rmap.transport.RmapConfig;
import me.moonways.rmap.transport.RmapConnection;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameType;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Детерминированные локи security-веток handshake (§4.3) и §9-reorder (Critical #1).
 * «Сырой/враждебный сервер» — голый {@link NioTransport} + raw {@link ConnectionListener} +
 * {@link HandshakeCodec}: шлёт кадры в произвольном порядке/содержании, минуя state-машину,
 * и фиксирует полученное. Без sleep-надежд: латчи/future.get с таймаутом.
 */
class HandshakeNegativeTest {

    private RmapConfig pub(String app) {
        return RmapConfig.builder().access(Access.publicAccess()).appVersion(app).clientName("c").build();
    }

    private RmapConfig key(String secret, String app) {
        return RmapConfig.builder().access(Access.privateKey(secret)).appVersion(app).clientName("c").build();
    }

    /** Critical #1: сырой сервер шлёт AUTH_OK(нули) РАНЬШЕ HELLO_ACK(нули) — байт-в-байт реордер
     *  на TCP-уровне. Публичный клиент ОБЯЗАН аутентифицироваться (стэш AUTH_OK в WAIT_HELLO_ACK). */
    @Test
    void reorder_authok_before_helloack_public_client_authenticates() throws Exception {
        ConnectionListener raw = new ConnectionListener() {
            public void onOpened(RmapConnection c) { }
            public void onFrame(RmapConnection c, Frame f) {
                if (f.getType() == FrameType.HELLO) {
                    // ИНВЕРТИРОВАННЫЙ порядок: AUTH_OK перед HELLO_ACK.
                    c.send(new Frame(FrameType.AUTH_OK, 0L, HandshakeCodec.encodeMac32(new byte[32])));
                    HandshakeCodec.HelloAck ack = new HandshakeCodec.HelloAck(
                            ProtocolVersion.PROTOCOL_VERSION, "app-1", false, new byte[32]);
                    c.send(new Frame(FrameType.HELLO_ACK, 0L, HandshakeCodec.encodeHelloAck(ack)));
                }
            }
            public void onClosed(RmapConnection c, Throwable t) { }
        };
        NioTransport rawServer = NioTransport.startServer(new InetSocketAddress("127.0.0.1", 0), pub("app-1"), raw);
        try {
            RmapClient client = RmapNet.create().newClient(pub("app-1"));
            try {
                client.connect("127.0.0.1", rawServer.boundPort()).get(5, TimeUnit.SECONDS);
                assertThat(client.isAuthenticated()).isTrue();
            } finally {
                client.close();
            }
        } finally {
            rawServer.stop();
        }
    }

    /** Anti-downgrade: сырой сервер объявляет authRequired=0, но клиент держит приватный ключ —
     *  клиент ОБЯЗАН разорвать; connect-future фейлится; AUTH_RESPONSE НЕ отправлен. */
    @Test
    void anti_downgrade_client_with_key_breaks_and_sends_no_auth_response() throws Exception {
        Set<FrameType> received = ConcurrentHashMap.newKeySet();
        ConnectionListener raw = new ConnectionListener() {
            public void onOpened(RmapConnection c) { }
            public void onFrame(RmapConnection c, Frame f) {
                received.add(f.getType());
                if (f.getType() == FrameType.HELLO) {
                    HandshakeCodec.HelloAck ack = new HandshakeCodec.HelloAck(
                            ProtocolVersion.PROTOCOL_VERSION, "app-1", false, new byte[32]);
                    c.send(new Frame(FrameType.HELLO_ACK, 0L, HandshakeCodec.encodeHelloAck(ack)));
                }
            }
            public void onClosed(RmapConnection c, Throwable t) { }
        };
        NioTransport rawServer = NioTransport.startServer(new InetSocketAddress("127.0.0.1", 0), pub("app-1"), raw);
        try {
            RmapClient client = RmapNet.create().newClient(key("k", "app-1"));
            try {
                assertThatThrownBy(() ->
                        client.connect("127.0.0.1", rawServer.boundPort()).get(5, TimeUnit.SECONDS))
                        .hasMessageContaining("PROTOCOL_ERROR");
                Thread.sleep(300); // окно для (не)отправки AUTH_RESPONSE
                assertThat(received).contains(FrameType.HELLO);
                assertThat(received).doesNotContain(FrameType.AUTH_RESPONSE);
            } finally {
                client.close();
            }
        } finally {
            rawServer.stop();
        }
    }

    /** Anti-downgrade наоборот: публичный клиент против реального приватного сервера —
     *  клиент рвёт (future фейлится), сервер НЕ аутентифицирует. */
    @Test
    void anti_downgrade_public_client_vs_private_server_breaks() throws Exception {
        RmapNet net = RmapNet.create();
        RmapServer server = net.newServer(key("k", "app-1"));
        AtomicBoolean serverAuthed = new AtomicBoolean(false);
        server.onAuthenticated(c -> serverAuthed.set(true));
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        try {
            RmapClient client = net.newClient(pub("app-1")); // публичный против приватного
            try {
                assertThatThrownBy(() ->
                        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS))
                        .hasMessageContaining("PROTOCOL_ERROR");
                Thread.sleep(300);
                assertThat(serverAuthed.get()).as("сервер не аутентифицировал публичного клиента").isFalse();
            } finally {
                client.close();
            }
        } finally {
            server.stop();
        }
    }

    /** Rogue-server: корректный challenge, но AUTH_OK с мусорным serverMac — клиент с ключом
     *  ОБЯЗАН разорвать (mismatch), future фейлится с ACCESS_DENIED. */
    @Test
    void rogue_server_bad_server_mac_breaks_client() throws Exception {
        ConnectionListener raw = new ConnectionListener() {
            public void onOpened(RmapConnection c) { }
            public void onFrame(RmapConnection c, Frame f) {
                if (f.getType() == FrameType.HELLO) {
                    HandshakeCodec.HelloAck ack = new HandshakeCodec.HelloAck(
                            ProtocolVersion.PROTOCOL_VERSION, "app-1", true, HmacAuth.randomBytes32());
                    c.send(new Frame(FrameType.HELLO_ACK, 0L, HandshakeCodec.encodeHelloAck(ack)));
                } else if (f.getType() == FrameType.AUTH_RESPONSE) {
                    byte[] garbage = new byte[32];
                    new Random().nextBytes(garbage);
                    c.send(new Frame(FrameType.AUTH_OK, 0L, HandshakeCodec.encodeMac32(garbage)));
                }
            }
            public void onClosed(RmapConnection c, Throwable t) { }
        };
        NioTransport rawServer = NioTransport.startServer(new InetSocketAddress("127.0.0.1", 0), pub("app-1"), raw);
        try {
            RmapClient client = RmapNet.create().newClient(key("k", "app-1"));
            try {
                assertThatThrownBy(() ->
                        client.connect("127.0.0.1", rawServer.boundPort()).get(5, TimeUnit.SECONDS))
                        .hasMessageContaining("ACCESS_DENIED");
            } finally {
                client.close();
            }
        } finally {
            rawServer.stop();
        }
    }
}
