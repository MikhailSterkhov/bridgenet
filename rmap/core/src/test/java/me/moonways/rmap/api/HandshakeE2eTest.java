package me.moonways.rmap.api;

import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.RmapConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandshakeE2eTest {

    private RmapConfig cfg(String key, String app) {
        return RmapConfig.builder()
                .access(key == null ? Access.publicAccess() : Access.privateKey(key))
                .appVersion(app).clientName("plugin-1").build();
    }

    @Test
    void mutual_auth_succeeds_and_ping_pong_works() throws Exception {
        RmapNet net = RmapNet.create();
        RmapServer server = net.newServer(cfg("s3cr3t", "app-1"));
        server.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
        server.start();

        RmapClient client = net.newClient(cfg("s3cr3t", "app-1"));
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        assertThat(client.isAuthenticated()).isTrue();

        // PING → сервер отвечает PONG; клиент считает PONG
        AtomicInteger pongs = new AtomicInteger();
        client.onPong(pongs::incrementAndGet);
        client.sendPing();
        // ждём PONG
        long deadline = System.currentTimeMillis() + 5000;
        while (pongs.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20);
        assertThat(pongs.get()).isGreaterThanOrEqualTo(1);

        client.close();
        server.stop();
    }

    @Test
    void public_access_mutual_success_and_ping_pong() throws Exception {
        // Публичный сервер шлёт HELLO_ACK и AUTH_OK back-to-back (§4.3): реальный лок
        // §9-reorder (Critical #1) в живом сценарии — с фиксом всегда успешен.
        RmapNet net = RmapNet.create();
        RmapServer server = net.newServer(cfg(null, "app-1")); // publicAccess
        server.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
        server.start();

        RmapClient client = net.newClient(cfg(null, "app-1")); // publicAccess
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        assertThat(client.isAuthenticated()).isTrue();

        AtomicInteger pongs = new AtomicInteger();
        client.onPong(pongs::incrementAndGet);
        client.sendPing();
        long deadline = System.currentTimeMillis() + 5000;
        while (pongs.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20);
        assertThat(pongs.get()).isGreaterThanOrEqualTo(1);

        client.close();
        server.stop();
    }

    @Test
    void wrong_key_is_rejected() throws Exception {
        RmapNet net = RmapNet.create();
        RmapServer server = net.newServer(cfg("right", "app-1"));
        server.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
        server.start();

        RmapClient client = net.newClient(cfg("wrong", "app-1"));
        assertThatThrownBy(() -> client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS))
                .hasMessageContaining("ACCESS_DENIED"); // либо причина в cause
        client.close();
        server.stop();
    }

    @Test
    void app_version_mismatch_is_rejected() throws Exception {
        RmapNet net = RmapNet.create();
        RmapServer server = net.newServer(cfg("k", "app-1"));
        server.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
        server.start();

        RmapClient client = net.newClient(cfg("k", "app-2")); // другая appVersion
        assertThatThrownBy(() -> client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS))
                .hasMessageContaining("VERSION");
        client.close();
        server.stop();
    }
}
