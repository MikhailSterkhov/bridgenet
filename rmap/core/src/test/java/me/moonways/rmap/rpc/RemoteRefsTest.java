package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapClient;
import me.moonways.rmap.api.RmapNet;
import me.moonways.rmap.api.RmapRefs;
import me.moonways.rmap.api.RmapSerializable;
import me.moonways.rmap.api.RmapServer;
import me.moonways.rmap.api.RmapStaleRefException;
import me.moonways.rmap.api.Snapshot;
import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.RmapConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Remote-refs (§10, §10.1): выдача wrapped-возврата рефом, identity-map, per-element рефы,
 * ref-форма RGET, явный/lease/разрыв release, @Snapshot by-value, REF_RELEASE неизвестного refId,
 * generation-гейт после reconnect, а также долг-фикс задачи 3 (продвижение read-интернера на
 * отклонённом кадре с объектным аргументом). Реальный loopback-стек (образец — ClientProxyTest).
 */
class RemoteRefsTest {

    // ---- контракт (из брифа) ----------------------------------------------------------------

    public interface Counter {
        int increment();
        int value();
        int addTag(Tag tag); // объектный аргумент → несёт classRef (долг-фикс задачи 3)
    }

    public interface CounterHub {
        Counter counter(String name);
        List<Counter> all();
        @Snapshot Counter snapshot(String name);
        int tagSubject(Tag tag); // subject-уровневый метод с объектным аргументом
    }

    @RmapSerializable
    public static class Tag {
        private String label;
        public Tag() { }
        public Tag(String label) { this.label = label; }
    }

    @RmapSerializable
    public static class CounterImpl implements Counter {
        private String name;
        private int count;
        public CounterImpl() { }
        public CounterImpl(String name) { this.name = name; }
        public synchronized int increment() { return ++count; }
        public synchronized int value() { return count; }
        public int addTag(Tag tag) { return tag.label.length(); }
    }

    public static class CounterHubImpl implements CounterHub {
        private final Map<String, CounterImpl> counters = new LinkedHashMap<>();
        public synchronized Counter counter(String name) {
            return counters.computeIfAbsent(name, CounterImpl::new);
        }
        public synchronized List<Counter> all() {
            return new ArrayList<>(counters.values());
        }
        @Snapshot public synchronized Counter snapshot(String name) {
            return counters.computeIfAbsent(name, CounterImpl::new);
        }
        public int tagSubject(Tag tag) { return tag.label.length(); }
    }

    // ---- инфраструктура ---------------------------------------------------------------------

    private RmapServer server;
    private RmapClient client;

    private RmapConfig cfg() {
        return RmapConfig.builder().access(Access.privateKey("ref-key"))
                .appVersion("b2ref").clientName("ref-test").build();
    }

    private CounterHub connect() throws Exception {
        RmapNet net = RmapNet.create();
        server = net.newServer(cfg());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("Services").export("CounterHub", CounterHub.class, new CounterHubImpl(),
                ExportOptions.builder().wrapReturnAsRemote(Counter.class).build());
        server.start();
        client = net.newClient(cfg());
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        return client.lookup("Services/CounterHub", CounterHub.class);
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    private RmapAgent oneAgent() {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            Collection<RmapAgent> agents = server.activeAgents();
            if (agents.size() == 1) return agents.iterator().next();
            sleep(10);
        }
        throw new AssertionError("expected exactly one active agent");
    }

    private static void await(BooleanSupplier cond) {
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) return;
            sleep(10);
        }
        throw new AssertionError("condition not met within timeout");
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ---- сценарии брифа ---------------------------------------------------------------------

    @Test
    void wrapped_return_arrives_as_proxy_not_value() throws Exception {
        CounterHub hub = connect();
        Counter c = hub.counter("x");
        assertThat(Proxy.isProxyClass(c.getClass()))
                .as("wrap-возврат приходит remote-ref прокси, не значением").isTrue();
        assertThat(c.increment()).isEqualTo(1);
        assertThat(c.value()).isEqualTo(1);
    }

    @Test
    void two_lookups_of_same_object_share_one_refId() throws Exception {
        CounterHub hub = connect();
        Counter a = hub.counter("x");
        Counter b = hub.counter("x");
        // identity-map: тот же impl на сервере → одна запись в ObjectTable (один refId).
        assertThat(oneAgent().objectTable().size()).isEqualTo(1);
        // и оба прокси адресуют один impl: суммарный инкремент виден через оба.
        a.increment();
        b.increment();
        assertThat(a.value()).isEqualTo(2);
        assertThat(b.value()).isEqualTo(2);
    }

    @Test
    void list_elements_are_per_element_refs() throws Exception {
        CounterHub hub = connect();
        hub.counter("a");
        hub.counter("b");
        List<Counter> all = hub.all();
        assertThat(all).hasSize(2);
        for (Counter el : all) {
            assertThat(Proxy.isProxyClass(el.getClass())).as("каждый элемент — remote-ref прокси").isTrue();
        }
        all.get(0).increment();
        assertThat(all.get(0).value()).isEqualTo(1);
        assertThat(all.get(1).value()).as("рефы per-element адресуют РАЗНЫЕ impl").isEqualTo(0);
    }

    @Test
    void ref_form_rget_roundtrip_works() throws Exception {
        CounterHub hub = connect();
        Counter c = hub.counter("x");
        assertThat(c.increment()).isEqualTo(1);
        assertThat(c.increment()).isEqualTo(2);
        assertThat(c.value()).isEqualTo(2);
    }

    @Test
    void explicit_release_shrinks_object_table() throws Exception {
        CounterHub hub = connect();
        Counter c = hub.counter("x");
        RmapAgent agent = oneAgent();
        assertThat(agent.objectTable().size()).isEqualTo(1);
        RmapRefs.release(c);
        await(() -> agent.objectTable().size() == 0);
        assertThat(agent.objectTable().size()).isEqualTo(0);
    }

    @Test
    void lease_expiry_makes_ref_stale_but_keeps_connection() throws Exception {
        CounterHub hub = connect();
        Counter c = hub.counter("x");
        RmapAgent agent = oneAgent();
        agent.objectTable().setLeaseTimeoutMillis(200); // тестовый порог lease
        sleep(400);                                     // без обращений дольше lease
        assertThatThrownBy(c::increment)
                .as("вызов по протухшему ref → RmapStaleRefException").isInstanceOf(RmapStaleRefException.class);
        // соединение живо: свежий ref продолжает работать
        Counter d = hub.counter("y");
        assertThat(d.increment()).isEqualTo(1);
    }

    @Test
    void reconnect_invalidates_old_ref_locally() throws Exception {
        CounterHub hub = connect();
        Counter c = hub.counter("x");
        ClientSession s1 = client.liveSession();
        assertThat(s1).isNotNull();
        int g1 = s1.generation();
        // рвём соединение → клиент авто-реконнектится к тому же серверу (новая сессия, новый generation).
        s1.connection().close();
        await(() -> {
            ClientSession s = client.liveSession();
            return s != null && s.generation() != g1;
        });
        // старый ref-прокси: generation != текущего → RmapStaleRefException ЛОКАЛЬНО (без сети).
        assertThatThrownBy(c::increment).isInstanceOf(RmapStaleRefException.class);
        // свежий ref на новой сессии работает.
        Counter d = hub.counter("z");
        assertThat(d.increment()).isEqualTo(1);
    }

    @Test
    void snapshot_method_returns_value_not_proxy() throws Exception {
        CounterHub hub = connect();
        Counter c = hub.counter("x");
        c.increment();
        c.increment(); // серверный count = 2
        // @Snapshot кодирует ЗНАЧЕНИЕМ (CounterImpl) — клиент должен уметь его декодировать по FQN.
        client.liveSession().connCodec().addWhitelist(Collections.singleton(CounterImpl.class.getName()));
        Counter snap = hub.snapshot("x");
        assertThat(Proxy.isProxyClass(snap.getClass())).as("@Snapshot — значение, не прокси").isFalse();
        assertThat(snap).isInstanceOf(CounterImpl.class);
        assertThat(snap.value()).isEqualTo(2); // by-value копия на момент снимка
        // мутация снимка локальна, сервер не затронут.
        snap.increment();
        assertThat(c.value()).isEqualTo(2);
    }

    @Test
    void unknown_ref_release_does_not_break_connection() throws Exception {
        CounterHub hub = connect();
        Counter c = hub.counter("x");
        RmapAgent agent = oneAgent();
        long unknownBefore = agent.objectTable().unknownReleaseCount();
        RmapRefs.release(c);                       // валидный release
        await(() -> agent.objectTable().size() == 0);
        RmapRefs.release(c);                       // refId уже неизвестен → молча + метрика
        await(() -> agent.objectTable().unknownReleaseCount() > unknownBefore);
        // соединение живо.
        assertThat(hub.counter("y").increment()).isEqualTo(1);
    }

    @Test
    void rejected_ref_call_with_object_arg_advances_read_interner() throws Exception {
        // Долг-фикс задачи 3: STALE_REF (non-closing) на кадре с объектным аргументом ДОЛЖЕН слить
        // argCount×TLV, продвинув read-интернер, иначе следующий легитимный кадр с CLASSREF_USE
        // того же класса не резолвится и рвёт соединение.
        CounterHub hub = connect();
        Counter c = hub.counter("x");
        RmapAgent agent = oneAgent();
        agent.objectTable().setLeaseTimeoutMillis(1);
        sleep(60); // ref протух

        // ref-форма RGET с объектным аргументом Tag (первое вхождение → CLASSREF_DEF на write-стороне
        // клиента), сервер: get==null → STALE_REF, но обязан слить Tag-аргумент (продвинуть интернер).
        assertThatThrownBy(() -> c.addTag(new Tag("first")))
                .isInstanceOf(RmapStaleRefException.class);

        // легитимный subject-вызов с Tag (write-сторона клиента уже интернировала Tag → CLASSREF_USE):
        // резолвится ТОЛЬКО если сервер продвинул read-интернер на отклонённом кадре.
        assertThat(hub.tagSubject(new Tag("second"))).isEqualTo("second".length());
    }

    // ---- прямые unit-проверки ObjectTable (self-review a/б) ----------------------------------

    @Test
    void object_table_identity_lease_sweep_and_unknown_release() throws Exception {
        ObjectTable table = new ObjectTable(10 * 60_000L);
        Object impl = new Object();
        long id1 = table.register(impl, Counter.class, ExportOptions.defaults());
        long id2 = table.register(impl, Counter.class, ExportOptions.defaults());
        assertThat(id2).as("повторный объект → тот же refId").isEqualTo(id1);
        assertThat(table.size()).isEqualTo(1);

        // get обновляет lastAccess → sweep сразу после НЕ эвиктит недавно использованный.
        assertThat(table.get(id1)).isNotNull();
        table.sweepExpired(10_000L);
        assertThat(table.size()).as("недавно использованный ref не эвиктится").isEqualTo(1);

        sleep(30);
        table.sweepExpired(10L); // теперь старше порога
        assertThat(table.size()).isEqualTo(0);

        long before = table.unknownReleaseCount();
        table.release(999999L); // неизвестный refId — молча + метрика, без исключения
        assertThat(table.unknownReleaseCount()).isEqualTo(before + 1);

        table.clear();
        assertThat(table.size()).isEqualTo(0);
    }
}
