package me.moonways.rmap.chaos;

import me.moonways.rmap.api.RmapClient;
import me.moonways.rmap.api.RmapNet;
import me.moonways.rmap.api.RmapServer;
import me.moonways.rmap.codec.RmapByteReader;
import me.moonways.rmap.rpc.ExportOptions;
import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.RmapConfig;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameCodec;
import me.moonways.rmap.wire.FrameType;
import me.moonways.rmap.wire.OtherCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaos на транспортном уровне (§12.4, задача 7): обрыв TCP посреди кадра, мусор до HELLO, точная
 * граница frameLimit, реконнект-цикл. Инвариант: сервер переживает любой враждебный ввод (selector-
 * поток не падает), враждебное соединение закрывается, следующий клиент работает.
 */
class ChaosTransportTest {

    public interface Calc {
        int add(int a, int b);
    }

    public static class CalcImpl implements Calc {
        public int add(int a, int b) {
            return a + b;
        }
    }

    private RmapConfig cfg(String app) {
        return RmapConfig.builder().access(Access.publicAccess()).appVersion(app).clientName("c").build();
    }

    /** Читать сокет до EOF (сервер закрыл, возможно после OTHER-кадра) ≤timeout. true — EOF/reset достигнут. */
    private static boolean drainUntilEof(Socket sock, int timeoutMs) {
        try {
            sock.setSoTimeout(timeoutMs);
            byte[] buf = new byte[256];
            while (true) {
                if (sock.getInputStream().read(buf) == -1) {
                    return true;
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            return false; // соединение осталось открытым
        } catch (IOException e) {
            return true; // reset — тоже закрытие сервером
        }
    }

    /** 1. Обрыв TCP посреди кадра: сырой Socket шлёт половину заявленного length-prefix и закрывается →
     *  сервер жив, соединение закрыто, следующий клиент работает. */
    @Test
    void tcp_break_mid_frame_leaves_server_alive() throws Exception {
        RmapNet net = RmapNet.create();
        RmapServer server = net.newServer(cfg("chaos-tx1"));
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        int port = server.boundPort();
        try {
            try (Socket sock = new Socket("127.0.0.1", port)) {
                // length-prefix заявляет 1000-байтовый кадр, но отправляем только 100 байт и рвём соединение.
                byte[] buf = new byte[104];
                int declared = 1000;
                buf[0] = (byte) (declared >>> 24);
                buf[1] = (byte) (declared >>> 16);
                buf[2] = (byte) (declared >>> 8);
                buf[3] = (byte) declared;
                sock.getOutputStream().write(buf);
                sock.getOutputStream().flush();
            } // socket закрыт → сервер видит FIN на неполном кадре → чистит соединение

            // сервер жив: новый клиент проходит handshake и делает round-trip.
            ChaosRawClient c = ChaosRawClient.connect(port, cfg("chaos-tx1"));
            try {
                c.send(FrameType.PING, 0L, new byte[]{1, 2, 3});
                Frame pong = c.awaitType(FrameType.PONG);
                assertThat(pong.getPayload()).containsExactly(1, 2, 3);
            } finally {
                c.stop();
            }
        } finally {
            server.stop();
        }
    }

    /** 2. Мусорные байты до HELLO: сырой Socket шлёт 1KiB случайного → соединение закрывается
     *  (malformed/фрейминг), сервер жив. */
    @Test
    void garbage_bytes_before_hello_are_rejected() throws Exception {
        RmapNet net = RmapNet.create();
        RmapServer server = net.newServer(cfg("chaos-tx2"));
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        int port = server.boundPort();
        try (Socket sock = new Socket("127.0.0.1", port)) {
            byte[] garbage = new byte[1024];
            new Random(0xC0FFEE).nextBytes(garbage);
            sock.getOutputStream().write(garbage);
            sock.getOutputStream().flush();

            // сервер закрывает соединение (малформ length/framing/unknown frame type). Он может сперва
            // прислать OTHER-кадр, затем FIN — дренируем поток до EOF, доказывая закрытие сервером.
            assertThat(drainUntilEof(sock, 5000)).as("сервер закрыл мусорное соединение").isTrue();

            // сервер жив.
            ChaosRawClient c = ChaosRawClient.connect(port, cfg("chaos-tx2"));
            c.stop();
        } finally {
            server.stop();
        }
    }

    /** 3. Кадр ровно frameLimit проходит, frameLimit+1 → FRAME_TOO_LARGE (крошечный лимит). */
    @Test
    void frame_at_limit_passes_over_limit_rejected() throws Exception {
        // preAuthFrameLimit большой (пропустить HELLO), полный frameLimit=64 → граница на post-auth кадрах.
        RmapConfig tiny = RmapConfig.builder().access(Access.publicAccess())
                .appVersion("chaos-tx3").clientName("c")
                .preAuthFrameLimit(4096).frameLimit(64).build();
        RmapNet net = RmapNet.create();
        RmapServer server = net.newServer(tiny);
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        int port = server.boundPort();
        ChaosRawClient c = ChaosRawClient.connect(port, tiny);
        try {
            // len = HEADER_AFTER_LEN(9) + payload; ровно 64 → payload 55 (проходит).
            c.send(FrameType.PING, 0L, new byte[64 - FrameCodec.HEADER_AFTER_LEN]);
            Frame pong = c.awaitType(FrameType.PONG);
            assertThat(pong).as("кадр ровно frameLimit прошёл (эхо-PONG)").isNotNull();

            // len = 65 (> 64) → FRAME_TOO_LARGE + close.
            c.send(FrameType.PING, 0L, new byte[64 - FrameCodec.HEADER_AFTER_LEN + 1]);
            Frame other = c.awaitType(FrameType.OTHER);
            assertThat(new RmapByteReader(other.getPayload(), 0, other.getPayload().length).readInt())
                    .isEqualTo(OtherCode.FRAME_TOO_LARGE);
            assertThat(c.closedLatch().await(5, TimeUnit.SECONDS)).as("соединение закрыто").isTrue();
        } finally {
            c.stop();
            server.stop();
        }
    }

    /** 4. Реконнект-цикл: server.stop() → рестарт на ТОМ ЖЕ порту → клиент сам переподключается →
     *  повторный вызов через тот же прокси работает (§7.1 «lookup-прокси живы»). */
    @Test
    void reconnect_after_server_restart_same_port_reuses_proxy() throws Exception {
        RmapNet net = RmapNet.create();
        RmapServer server1 = net.newServer(cfg("chaos-tx4"));
        server1.bind(new InetSocketAddress("127.0.0.1", 0));
        server1.put("S").export("Calc", Calc.class, new CalcImpl(), ExportOptions.defaults());
        server1.start();
        int port = server1.boundPort();

        RmapClient client = net.newClient(cfg("chaos-tx4"));
        RmapServer server2 = null;
        try {
            client.connect("127.0.0.1", port).get(5, TimeUnit.SECONDS);
            Calc calc = client.lookup("S/Calc", Calc.class);
            assertThat(calc.add(2, 3)).isEqualTo(5);

            server1.stop(); // обрыв установленной сессии → клиент уходит в reconnect-backoff

            // рестарт на ТОМ ЖЕ порту.
            server2 = net.newServer(cfg("chaos-tx4"));
            server2.bind(new InetSocketAddress("127.0.0.1", port));
            server2.put("S").export("Calc", Calc.class, new CalcImpl(), ExportOptions.defaults());
            server2.start();

            // клиент сам переподключается (backoff), повторный lookup через тот же прокси проходит.
            int result = -1;
            long deadline = System.currentTimeMillis() + 15_000L;
            while (System.currentTimeMillis() < deadline) {
                try {
                    result = calc.add(10, 20);
                    break;
                } catch (RuntimeException retry) {
                    Thread.sleep(200); // сессия ещё не переустановлена — ждём reconnect
                }
            }
            assertThat(result).as("вызов через тот же прокси после рестарта").isEqualTo(30);
        } finally {
            client.close();
            if (server2 != null) server2.stop();
            server1.stop();
        }
    }
}
