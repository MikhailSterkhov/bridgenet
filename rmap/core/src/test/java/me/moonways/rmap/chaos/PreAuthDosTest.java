package me.moonways.rmap.chaos;

import me.moonways.rmap.api.RmapClient;
import me.moonways.rmap.api.RmapNet;
import me.moonways.rmap.api.RmapServer;
import me.moonways.rmap.codec.RmapByteReader;
import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.RmapConfig;
import me.moonways.rmap.wire.FrameCodec;
import me.moonways.rmap.wire.FrameType;
import me.moonways.rmap.wire.OtherCode;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pre-auth DoS-отбойник (§4.3, задача 7). Сырые {@link Socket}, минуя фасад/handshake: сервер держит
 * счётчик соединений в pre-auth (инкремент на accept, декремент на auth-успех И на close — ровно раз)
 * плюс per-remote-IP счётчик ВСЕХ живых соединений. Превышение любого лимита → принятый канал
 * закрывается НЕМЕДЛЕННО (без OTHER — отбойник на accept). Инвариант DoD: лимиты соблюдены, зомби
 * зачищаются по handshakeTimeout, легитимный клиент проходит, сервер жив.
 */
class PreAuthDosTest {

    private RmapConfig serverCfg(int maxHandshakes, int maxPerRemote, long handshakeMs) {
        return RmapConfig.builder()
                .access(Access.publicAccess())
                .appVersion("chaos-1")
                .clientName("srv")
                .maxConcurrentHandshakes(maxHandshakes)
                .maxConnectionsPerRemote(maxPerRemote)
                .handshakeTimeout(Duration.ofMillis(handshakeMs))
                .closeFlushTimeout(Duration.ofMillis(300))
                .preAuthFrameLimit(4096)
                .build();
    }

    private RmapConfig clientCfg() {
        return RmapConfig.builder()
                .access(Access.publicAccess())
                .appVersion("chaos-1")
                .clientName("cli")
                .build();
    }

    /** Сценарий 1: maxConcurrentHandshakes=4; 10 сырых коннектов без HELLO → в pre-auth ≤4, лишние
     *  закрыты немедленно; после handshakeTimeout зомби зачищены → новый ЛЕГИТИМНЫЙ клиент проходит. */
    @Test
    void concurrent_handshake_limit_rejects_excess_and_releases_after_sweep() throws Exception {
        RmapNet net = RmapNet.create();
        RmapServer server = net.newServer(serverCfg(4, 32, 800));
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        int port = server.boundPort();
        List<Socket> sockets = new ArrayList<>();
        RmapClient legit = null;
        try {
            for (int i = 0; i < 10; i++) {
                sockets.add(new Socket("127.0.0.1", port)); // подключились, HELLO не шлём
            }
            // селектор обработал ВСЕ 10 accept'ов (loopback — микросекунды); отбойник оставил 4 в pre-auth.
            Thread.sleep(500);
            assertThat(server.preAuthConnectionCount())
                    .as("в pre-auth ровно maxConcurrentHandshakes").isEqualTo(4);

            // лишние (10-4=6) закрыты сервером немедленно на accept (EOF/reset); 4 удержанных молчат.
            int rejected = 0;
            for (Socket s : sockets) {
                if (isClosedByServer(s, 150)) {
                    rejected++;
                }
            }
            assertThat(rejected).as("лишние коннекты отбиты немедленно").isGreaterThanOrEqualTo(6);

            // после handshakeTimeout зомби зачищены → pre-auth-слоты освобождены (декремент на close).
            awaitCount(server::preAuthConnectionCount, 0, 4000);

            // отбойник отпустило → легитимный клиент проходит auth (сервер жив).
            legit = net.newClient(clientCfg());
            legit.connect("127.0.0.1", port).get(5, TimeUnit.SECONDS);
            assertThat(legit.isAuthenticated()).as("легитимный клиент аутентифицирован").isTrue();
        } finally {
            if (legit != null) legit.close();
            for (Socket s : sockets) closeQuietly(s);
            server.stop();
        }
    }

    /** Сценарий 2: maxConnectionsPerRemote=2 → третий коннект с 127.0.0.1 закрывается сразу. */
    @Test
    void per_remote_limit_rejects_third_connection_immediately() throws Exception {
        RmapNet net = RmapNet.create();
        RmapServer server = net.newServer(serverCfg(256, 2, 4000));
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        int port = server.boundPort();
        InetAddress loop = InetAddress.getByName("127.0.0.1");
        List<Socket> sockets = new ArrayList<>();
        try {
            sockets.add(new Socket("127.0.0.1", port));
            sockets.add(new Socket("127.0.0.1", port));
            awaitCount(() -> server.connectionsFromRemote(loop), 2, 2000);

            // третий с того же IP — отбит немедленно.
            Socket third = new Socket("127.0.0.1", port);
            sockets.add(third);
            assertThat(isClosedByServer(third, 1500)).as("третий коннект закрыт сразу").isTrue();
            assertThat(server.connectionsFromRemote(loop))
                    .as("per-IP счётчик не превысил лимит").isEqualTo(2);

            // сервер жив: и четвёртый отбивается, счётчик стабилен.
            Socket fourth = new Socket("127.0.0.1", port);
            sockets.add(fourth);
            assertThat(isClosedByServer(fourth, 1500)).as("четвёртый тоже отбит").isTrue();
            assertThat(server.connectionsFromRemote(loop)).isEqualTo(2);
        } finally {
            for (Socket s : sockets) closeQuietly(s);
            server.stop();
        }
    }

    /** Сценарий 3: гигантский HELLO (100KiB > preAuthFrameLimit 4KiB) → FRAME_TOO_LARGE, соединение
     *  закрыто, сервер жив (новый легитимный клиент проходит). */
    @Test
    void oversized_pre_auth_frame_is_rejected_and_server_survives() throws Exception {
        RmapNet net = RmapNet.create();
        RmapServer server = net.newServer(serverCfg(256, 32, 4000));
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        int port = server.boundPort();
        RmapClient legit = null;
        try (Socket sock = new Socket("127.0.0.1", port)) {
            // length-prefix заявляет 100KiB-кадр (> preAuthFrameLimit 4096) — сервер отбивает по len,
            // не дожидаясь полного payload'а.
            byte[] header = new byte[FrameCodec.HEADER_AFTER_LEN + 4];
            int declaredLen = 100 * 1024;
            header[0] = (byte) (declaredLen >>> 24);
            header[1] = (byte) (declaredLen >>> 16);
            header[2] = (byte) (declaredLen >>> 8);
            header[3] = (byte) declaredLen;
            header[4] = (byte) FrameType.HELLO.code();
            // callId (8) + пара байт payload — нули; сервер реагирует на len раньше.
            sock.getOutputStream().write(header);
            sock.getOutputStream().flush();

            int code = readOtherCode(sock);
            assertThat(code).as("код = FRAME_TOO_LARGE").isEqualTo(OtherCode.FRAME_TOO_LARGE);
            assertThat(isClosedByServer(sock, 2000)).as("соединение закрыто после OTHER").isTrue();

            // сервер жив.
            legit = net.newClient(clientCfg());
            legit.connect("127.0.0.1", port).get(5, TimeUnit.SECONDS);
            assertThat(legit.isAuthenticated()).isTrue();
        } finally {
            if (legit != null) legit.close();
            server.stop();
        }
    }

    // ---- helpers ----

    /** true — сервер закрыл соединение (EOF или reset); false — соединение живо (read вышел по таймауту). */
    private static boolean isClosedByServer(Socket s, int soTimeoutMs) {
        try {
            s.setSoTimeout(soTimeoutMs);
            return s.getInputStream().read() == -1;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (IOException e) {
            return true; // connection reset — тоже «закрыто сервером»
        }
    }

    /** Прочитать ответный OTHER-кадр с сокета и вернуть код (§7.3). */
    private static int readOtherCode(Socket sock) throws IOException {
        sock.setSoTimeout(5000);
        DataInputStream in = new DataInputStream(sock.getInputStream());
        int len = in.readInt();
        byte[] rest = new byte[len];
        in.readFully(rest);
        assertThat(rest[0] & 0xFF).as("ответный кадр — OTHER").isEqualTo(FrameType.OTHER.code());
        byte[] payload = java.util.Arrays.copyOfRange(rest, FrameCodec.HEADER_AFTER_LEN, len);
        return new RmapByteReader(payload, 0, payload.length).readInt();
    }

    private static void awaitCount(IntSupplier c, int expected, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (c.getAsInt() == expected) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("count did not reach " + expected + " within " + timeoutMs + "ms; last=" + c.getAsInt());
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }
}
