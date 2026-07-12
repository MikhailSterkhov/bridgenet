package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapClient;
import me.moonways.rmap.api.RmapNet;
import me.moonways.rmap.api.RmapRefs;
import me.moonways.rmap.api.RmapRemoteException;
import me.moonways.rmap.api.RmapSerializable;
import me.moonways.rmap.api.RmapServer;
import me.moonways.rmap.api.RmapStaleRefException;
import me.moonways.rmap.api.Snapshot;
import me.moonways.rmap.codec.CodecRegistry;
import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.RmapConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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
        int noteLength(Note note); // Note встречается ТОЛЬКО в графе wrapped-интерфейса (I4)
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

    /** DTO, используемый ТОЛЬКО в сигнатурах wrapped-интерфейса Counter, но НЕ в CounterHub (I4):
     *  его whitelist должен прийти в манифест subject'а из аудита графа wrapped-возврата. */
    @RmapSerializable
    public static class Note {
        private String text;
        public Note() { }
        public Note(String text) { this.text = text; }
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
        public int noteLength(Note note) { return note.text.length(); }
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

    // ---- §10: значение, реализующее 2 wrap-интерфейса активного набора → ambiguity -------------

    public interface Alpha { int a(); }
    public interface Beta { int b(); }

    /** Реализует ОБА wrap-интерфейса — при encode ответа remoteInterfaceFor найдёт 2 совпадения. */
    public static class Ambidextrous implements Alpha, Beta {
        public int a() { return 1; }
        public int b() { return 2; }
    }

    public static class PureBeta implements Beta {
        public int b() { return 7; }
    }

    /** Оба wrap-интерфейса встречаются в позициях возврата → оба попадают в манифестный wrap-набор
     *  (активный на encode). {@code ambiguous()} отдаёт объект в ОБОИХ → remoteInterfaceFor неоднозначен. */
    public interface AmbiHub {
        Alpha ambiguous();  // impl реализует и Alpha, и Beta → ambiguity на encode
        Beta other();       // затягивает Beta в манифестный wrap-набор
        int ping();         // healthcheck: соединение живо после отклонённого вызова
    }

    public static class AmbiHubImpl implements AmbiHub {
        public Alpha ambiguous() { return new Ambidextrous(); }
        public Beta other() { return new PureBeta(); }
        public int ping() { return 42; }
    }

    // ---- инфраструктура ---------------------------------------------------------------------

    private RmapServer server;
    private RmapClient client;

    private RmapConfig cfg(Duration refLeaseTimeout, Consumer<CodecRegistry> codecConfigurer) {
        return RmapConfig.builder().access(Access.privateKey("ref-key"))
                .appVersion("b2ref").clientName("ref-test")
                .refLeaseTimeout(refLeaseTimeout)
                .codec(codecConfigurer)
                .build();
    }

    private RmapConfig cfg() {
        return cfg(Duration.ofMinutes(10), c -> { });
    }

    /** Задача 6: refLeaseTimeout — конфиг-путь (заменяет прежний package-private
     *  {@code ObjectTable.setLeaseTimeoutMillis} test-seam), а не post-hoc мутация живого ObjectTable. */
    private CounterHub connect(Duration refLeaseTimeout, Consumer<CodecRegistry> codecConfigurer) throws Exception {
        RmapNet net = RmapNet.create();
        server = net.newServer(cfg(refLeaseTimeout, codecConfigurer));
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("Services").export("CounterHub", CounterHub.class, new CounterHubImpl(),
                ExportOptions.builder().wrapReturnAsRemote(Counter.class).build());
        server.start();
        client = net.newClient(cfg(refLeaseTimeout, codecConfigurer));
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        return client.lookup("Services/CounterHub", CounterHub.class);
    }

    private CounterHub connect(Duration refLeaseTimeout) throws Exception {
        return connect(refLeaseTimeout, c -> { });
    }

    private CounterHub connect() throws Exception {
        return connect(Duration.ofMinutes(10));
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
        // Задача 6: короткий lease — конфиг-путь (RmapConfig.refLeaseTimeout), а не test-seam
        // ObjectTable.setLeaseTimeoutMillis (удалён).
        CounterHub hub = connect(Duration.ofMillis(200));
        Counter c = hub.counter("x");
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
        // Долг-фикс задачи 5→6: @Snapshot кодирует ЗНАЧЕНИЕМ конкретный класс (CounterImpl), не
        // видимый статическому графу сигнатур (там только wrap-интерфейс Counter) — явная
        // .codec(c -> c.serializable(CounterImpl.class)) на клиенте авто-покрывает decode-whitelist
        // (§5.1: "манифесты... плюс явные регистрации"), БЕЗ ручного connCodec().addWhitelist(...).
        CounterHub hub = connect(Duration.ofMinutes(10), c -> c.serializable(CounterImpl.class));
        Counter c = hub.counter("x");
        c.increment();
        c.increment(); // серверный count = 2
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
        // того же класса не резолвится и рвёт соединение. Lease=1мс — конфиг-путь (задача 6).
        CounterHub hub = connect(Duration.ofMillis(1));
        Counter c = hub.counter("x");
        sleep(60); // ref протух

        // ref-форма RGET с объектным аргументом Tag (первое вхождение → CLASSREF_DEF на write-стороне
        // клиента), сервер: get==null → STALE_REF, но обязан слить Tag-аргумент (продвинуть интернер).
        assertThatThrownBy(() -> c.addTag(new Tag("first")))
                .isInstanceOf(RmapStaleRefException.class);

        // легитимный subject-вызов с Tag (write-сторона клиента уже интернировала Tag → CLASSREF_USE):
        // резолвится ТОЛЬКО если сервер продвинул read-интернер на отклонённом кадре.
        assertThat(hub.tagSubject(new Tag("second"))).isEqualTo("second".length());
    }

    @Test
    void wrapped_interface_dto_not_in_subject_signatures_works() throws Exception {
        // I4: Note встречается ТОЛЬКО в графе wrapped-интерфейса Counter (не в CounterHub-сигнатурах).
        // Ref-вызов с Note-аргументом резолвится на сервере ТОЛЬКО если export влил whitelist Counter
        // в манифест subject'а (unionWhitelist) — иначе сервер отверг бы легальный вызов CODEC_ERROR+close.
        // Явную .serializable(Note) НЕ регистрируем: проверяем именно вывод whitelist из графа wrapped.
        CounterHub hub = connect();
        Counter c = hub.counter("x");
        assertThat(c.noteLength(new Note("hello"))).isEqualTo(5);
        // соединение живо и после ref-вызова с ранее «невидимым» DTO.
        assertThat(hub.counter("y").increment()).isEqualTo(1);
    }

    @Test
    void with_options_on_ref_proxy_throws_clear_error() throws Exception {
        // Минор: withOptions на ref-прокси прежде терял ref-режим → subject-вызов path=null → NPE.
        // Теперь — понятный IllegalArgumentException.
        CounterHub hub = connect();
        Counter c = hub.counter("x"); // ref-прокси
        assertThatThrownBy(() -> client.withOptions(c, RmapCallOptions.deadline(Duration.ofSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remote-ref");
    }

    @Test
    void reconnect_publishes_fresh_live_session_and_calls_work() throws Exception {
        // I2 (наблюдаемая гарантия): после reconnect this.session = живая сессия ТЕКУЩЕГО соединения,
        // а не затёртая поздним кадром мёртвого conn1 и не залипшая в null при здоровом conn2.
        CounterHub hub = connect();
        ClientSession s1 = client.liveSession();
        assertThat(s1).isNotNull();
        int g1 = s1.generation();
        s1.connection().close(); // рвём → авто-reconnect (новая сессия, новый generation)
        await(() -> {
            ClientSession s = client.liveSession();
            return s != null && s.generation() != g1;
        });
        ClientSession s2 = client.liveSession();
        assertThat(s2).as("published session — живое текущее соединение").isNotNull();
        assertThat(s2.connection().isClosed()).as("не затёрта мёртвым conn1").isFalse();
        // subject-вызов на свежей сессии проходит (session не null и не подменена).
        assertThat(hub.counter("fresh").increment()).isEqualTo(1);
    }

    @Test
    void value_implementing_two_wrapped_interfaces_yields_remote_error_and_keeps_connection() throws Exception {
        // §10 / RefContextImpl.remoteInterfaceFor: если возвращаемый объект реализует ДВА интерфейса
        // активного wrap-набора — encode ответа бросает RmapCodecException «ambiguous remote interface».
        // Путь на реальном loopback: encode DONE падает → sendResult ловит RuntimeException →
        // OTHER(INTERNAL_ERROR) с EXCEPTION-TLV (БЕЗ close) → клиент видит RmapRemoteException, соединение живо.
        RmapNet net = RmapNet.create();
        server = net.newServer(cfg());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("Services").export("AmbiHub", AmbiHub.class, new AmbiHubImpl(),
                ExportOptions.builder().wrapReturnAsRemote(Alpha.class).wrapReturnAsRemote(Beta.class).build());
        server.start();
        client = net.newClient(cfg());
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        AmbiHub hub = client.lookup("Services/AmbiHub", AmbiHub.class);

        assertThatThrownBy(hub::ambiguous)
                .as("объект в 2 wrap-интерфейсах → ambiguous remote interface на сервере")
                .isInstanceOf(RmapRemoteException.class)
                .hasMessageContaining("ambiguous");
        // соединение живо: последующий вызов на том же соединении проходит.
        assertThat(hub.ping()).isEqualTo(42);
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
