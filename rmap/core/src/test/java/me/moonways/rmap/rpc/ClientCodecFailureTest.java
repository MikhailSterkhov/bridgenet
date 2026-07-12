package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapClient;
import me.moonways.rmap.api.RmapNet;
import me.moonways.rmap.api.RmapSerializable;
import me.moonways.rmap.api.RmapServer;
import me.moonways.rmap.codec.RmapCodecException;
import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.RmapConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Клиентские кодек-сбои (финревью B): C2 — decode-сбой DONE (серверный @RmapSerializable подтип вне
 * клиентского whitelist) даёт CODEC_ERROR + close вместо тихого рассинхрона интернера; I3 — сбой
 * encode аргумента (не-serializable подтип) отдаёт вызывающему причину, не таймаут по дедлайну.
 * Реальный loopback-стек (образец — ClientProxyTest).
 */
class ClientCodecFailureTest {

    @RmapSerializable
    public static class Tag {
        String label;
        public Tag() { }
        public Tag(String label) { this.label = label; }
    }

    /** Серверный @RmapSerializable подтип объявленного Tag: сервер закодирует его (encode не фильтрует
     *  по whitelist), клиентский whitelist знает только базовый Tag → readClassRef бросает ДО
     *  интернирования (C2). */
    @RmapSerializable
    public static class EvilTag extends Tag {
        String extra;
        public EvilTag() { }
        public EvilTag(String label, String extra) { super(label); this.extra = extra; }
    }

    /** НЕ-serializable подтип Tag: клиентский encode аргумента бросит RmapCodecException (I3). */
    public static class RogueTag extends Tag {
        public RogueTag(String label) { super(label); }
    }

    public interface Svc {
        Tag poison();       // impl вернёт EvilTag (не в client whitelist) → C2
        int tagLen(Tag t);  // клиент передаст RogueTag → encode аргумента бросит (I3)
        int ping();         // чистый метод для проверки живучести/восстановления
    }

    public static class SvcImpl implements Svc {
        public Tag poison() { return new EvilTag("x", "y"); }
        public int tagLen(Tag t) { return t.label.length(); }
        public int ping() { return 7; }
    }

    private RmapServer server;
    private RmapClient client;

    private RmapConfig cfg() {
        return RmapConfig.builder().access(Access.privateKey("codecfail-key"))
                .appVersion("b2cf").clientName("codecfail-test").build();
    }

    private Svc connect() throws Exception {
        RmapNet net = RmapNet.create();
        server = net.newServer(cfg());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("Services").export("Svc", Svc.class, new SvcImpl());
        server.start();
        client = net.newClient(cfg());
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        return client.lookup("Services/Svc", Svc.class);
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    private static void await(BooleanSupplier cond) {
        long deadline = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) return;
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        throw new AssertionError("condition not met within timeout");
    }

    @Test
    void done_decode_failure_closes_connection_and_reconnect_recovers() throws Exception {
        Svc svc = connect();
        ClientSession s1 = client.liveSession();
        assertThat(s1).isNotNull();
        int g1 = s1.generation();

        // poison(): сервер кодирует EvilTag, клиентский whitelist знает только Tag → decode DONE бросает
        // ДО интернирования → C2: CODEC_ERROR + close (НЕ молчаливый рассинхрон интернера).
        assertThatThrownBy(svc::poison).isInstanceOf(RuntimeException.class);

        // клиент авто-реконнектится → новая сессия с чистыми интернерами (иной generation).
        await(() -> {
            ClientSession s = client.liveSession();
            return s != null && s.generation() != g1;
        });

        // следующая сессия работает end-to-end (интернеры не смещены) — доказательство отсутствия
        // тихого рассинхрона.
        assertThat(svc.ping()).isEqualTo(7);
    }

    @Test
    void arg_encode_failure_reports_cause_not_timeout() throws Exception {
        Svc svc = connect();
        long start = System.currentTimeMillis();

        // RogueTag не @RmapSerializable → клиентский encode аргумента бросает RmapCodecException. I3:
        // вызывающий получает ПРИЧИНУ немедленно, НЕ RmapTimeoutException по дедлайну (5000мс дефолт).
        assertThatThrownBy(() -> svc.tagLen(new RogueTag("boom")))
                .isInstanceOf(RmapCodecException.class);
        assertThat(System.currentTimeMillis() - start)
                .as("причина отдана сразу, не по дедлайну").isLessThan(2000);

        // кадр не ушёл, write-интернер откачен → соединение чисто: обычный вызов работает
        // (pending/таймер сняты, поздний CANCEL несуществующего вызова не шлётся).
        assertThat(svc.ping()).isEqualTo(7);
        assertThat(svc.tagLen(new Tag("ok"))).isEqualTo(2);
    }
}
