package me.moonways.rmap.chaos;

import me.moonways.rmap.api.RmapNet;
import me.moonways.rmap.api.RmapServer;
import me.moonways.rmap.codec.RmapByteReader;
import me.moonways.rmap.codec.RmapByteWriter;
import me.moonways.rmap.codec.Tags;
import me.moonways.rmap.rpc.CallWire;
import me.moonways.rmap.rpc.MethodIds;
import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.RmapConfig;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameType;
import me.moonways.rmap.wire.OtherCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adversarial-декод (§12.4, задача 7). Каждый враждебный кадр приходит как реальный RGET-аргумент
 * к серверу с крошечными лимитами (maxInternedClasses=8) через сырого authenticated-клиента. КАЖДЫЙ
 * обязан дать OTHER(CODEC_ERROR) + close, НЕ зависание/OOM/падение selector-потока; после каждого —
 * НОВОЕ соединение работает (сервер жив). Тесты ПРОВЕРЯЮТ существующую кодек-механику (maxDecodeDepth,
 * negative/2^31-1 size без преаллокации, back-ref-диапазон, classRef whitelist/limit, enum, exception
 * лимиты), реализованную планами A/B2-1 — не добавляют её.
 */
class AdversarialDecodeTest {

    // Экспортируемый контракт: один Base-параметр (принимает любой конкретный подтип по type-check),
    // один enum-параметр (заносит Color в whitelist). Object в сигнатуре запрещён аудитом (§8).
    public static class Base { }
    public static class K0 extends Base { }
    public static class K1 extends Base { }
    public static class K2 extends Base { }
    public static class K3 extends Base { }
    public static class K4 extends Base { }
    public static class K5 extends Base { }
    public static class K6 extends Base { }
    public static class K7 extends Base { }
    public static class K8 extends Base { }

    public enum Color { RED, GREEN, BLUE }

    public interface Target {
        void takeBase(Base b);
        void takeColor(Color c);
    }

    public static class TargetImpl implements Target {
        public void takeBase(Base b) { }
        public void takeColor(Color c) { }
    }

    private static final Class<?>[] SPAM_CLASSES = {
            K0.class, K1.class, K2.class, K3.class, K4.class, K5.class, K6.class, K7.class, K8.class
    };

    private RmapServer server;
    private final List<ChaosRawClient> clients = new ArrayList<>();

    private RmapConfig cfg() {
        return RmapConfig.builder()
                .access(Access.publicAccess())
                .appVersion("chaos-adv")
                .clientName("c")
                .maxInternedClasses(8) // крошечный лимит интернирования для сценария 6
                .codec(c -> {
                    c.serializable(Base.class);
                    c.serializable(SPAM_CLASSES);
                    c.serializable(Color.class);
                })
                .build();
    }

    private int startServer() {
        RmapNet net = RmapNet.create();
        server = net.newServer(cfg());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("T").export("Target", Target.class, new TargetImpl());
        server.start();
        return server.boundPort();
    }

    private ChaosRawClient connect(int port) throws Exception {
        ChaosRawClient c = ChaosRawClient.connect(port, cfg());
        clients.add(c);
        return c;
    }

    @AfterEach
    void tearDown() {
        for (ChaosRawClient c : clients) {
            try { c.stop(); } catch (RuntimeException ignored) { }
        }
        if (server != null) server.stop();
    }

    // ---- сценарии ----

    /** 1. LIST-вложенность 100 уровней (maxDecodeDepth=32) → CODEC_ERROR. */
    @Test
    void nested_lists_beyond_max_depth() throws Exception {
        int port = startServer();
        ChaosRawClient c = connect(port);
        sendRget(c, "takeBase", arg -> {
            for (int i = 0; i < 100; i++) {
                arg.writeByte(Tags.LIST);
                arg.writeInt(1);
            }
            arg.writeByte(Tags.NULL);
        });
        assertCodecErrorAndClosed(c);
        assertServerAlive(port);
    }

    /** 2. Отрицательный size коллекции; size=2^31-1 (без OOM — преаллокации нет). */
    @Test
    void negative_and_maxint_collection_size() throws Exception {
        int port = startServer();

        ChaosRawClient neg = connect(port);
        sendRget(neg, "takeBase", arg -> { arg.writeByte(Tags.LIST); arg.writeInt(-1); });
        assertCodecErrorAndClosed(neg);

        ChaosRawClient huge = connect(port);
        sendRget(huge, "takeBase", arg -> { arg.writeByte(Tags.LIST); arg.writeInt(Integer.MAX_VALUE); });
        assertCodecErrorAndClosed(huge); // буфер-underflow на первом элементе, БЕЗ преаллокации 2^31

        assertServerAlive(port);
    }

    /** 3. BACK_REF на несуществующий индекс. */
    @Test
    void back_ref_out_of_range() throws Exception {
        int port = startServer();
        ChaosRawClient c = connect(port);
        sendRget(c, "takeBase", arg -> { arg.writeByte(Tags.BACK_REF); arg.writeInt(5); });
        assertCodecErrorAndClosed(c);
        assertServerAlive(port);
    }

    /** 4. classRef-reference на неопределённый classId. */
    @Test
    void classref_use_of_undefined_id() throws Exception {
        int port = startServer();
        ChaosRawClient c = connect(port);
        sendRget(c, "takeBase", arg -> {
            arg.writeByte(Tags.OBJECT);
            arg.writeByte(Tags.CLASSREF_USE);
            arg.writeInt(99); // ни один classId ещё не определён на этом соединении
        });
        assertCodecErrorAndClosed(c);
        assertServerAlive(port);
    }

    /** 5. FQN вне whitelist → CODEC_ERROR ДО загрузки класса (канарейка «evil.NoSuchClass»). */
    @Test
    void fqn_not_in_whitelist_rejected_before_class_load() throws Exception {
        int port = startServer();
        ChaosRawClient c = connect(port);
        sendRget(c, "takeBase", arg -> {
            arg.writeByte(Tags.OBJECT);
            arg.writeByte(Tags.CLASSREF_DEF);
            arg.writeStr("evil.NoSuchClass");
        });
        Frame other = c.await(FrameType.OTHER);
        assertThat(otherCode(other)).isEqualTo(OtherCode.CODEC_ERROR);
        // канарейка: отказ по whitelist, НЕ ClassNotFoundException (значит Class.forName не звался).
        assertThat(otherMessage(other)).contains("whitelist");
        assertThat(otherMessage(other)).doesNotContain("class not found");
        assertThat(c.closedLatch().await(5, TimeUnit.SECONDS)).isTrue();
        assertServerAlive(port);
    }

    /** 6. Спам уникальными FQN сверх maxInternedClasses (тестовый лимит 8). */
    @Test
    void interned_classes_limit_exceeded() throws Exception {
        int port = startServer();
        ChaosRawClient c = connect(port);
        // 9 кадров, каждый определяет НОВЫЙ класс (0 полей): первые 8 успешны (DONE), 9-й превышает лимит.
        for (int i = 0; i < 8; i++) {
            final int idx = i;
            sendRget(c, "takeBase", arg -> {
                arg.writeByte(Tags.OBJECT);
                arg.writeByte(Tags.CLASSREF_DEF);
                arg.writeStr(SPAM_CLASSES[idx].getName());
                // 0 полей у Ki → тело объекта пусто
            }, 100L + i);
            Frame done = c.await(FrameType.DONE); // успешный decode+invoke продвигает read-интернер
            assertThat(done.getCallId()).isEqualTo(100L + idx);
        }
        // 9-й уникальный FQN → interned classes limit exceeded ДО forName.
        sendRget(c, "takeBase", arg -> {
            arg.writeByte(Tags.OBJECT);
            arg.writeByte(Tags.CLASSREF_DEF);
            arg.writeStr(SPAM_CLASSES[8].getName());
        }, 999L);
        assertCodecErrorAndClosed(c);
        assertServerAlive(port);
    }

    /** 7. Невалидное имя ENUM-константы. */
    @Test
    void invalid_enum_constant_name() throws Exception {
        int port = startServer();
        ChaosRawClient c = connect(port);
        sendRget(c, "takeColor", arg -> {
            arg.writeByte(Tags.ENUM);
            arg.writeByte(Tags.CLASSREF_DEF);
            arg.writeStr(Color.class.getName());
            arg.writeStr("NOT_A_CONSTANT");
        });
        assertCodecErrorAndClosed(c);
        assertServerAlive(port);
    }

    /** 8. EXCEPTION со stackDepth=1000; cause-цепочка глубже 8. */
    @Test
    void exception_stack_depth_and_cause_chain_limits() throws Exception {
        int port = startServer();

        ChaosRawClient deep = connect(port);
        sendRget(deep, "takeBase", arg -> {
            arg.writeByte(Tags.EXCEPTION);
            arg.writeStr("java.lang.RuntimeException");
            arg.writeStr("");
            arg.writeInt(1000); // stackDepth > 64
        });
        assertCodecErrorAndClosed(deep);

        ChaosRawClient chain = connect(port);
        sendRget(chain, "takeBase", arg -> {
            arg.writeByte(Tags.EXCEPTION);
            for (int i = 0; i < 10; i++) { // > 8 вложенных cause
                arg.writeStr("java.lang.RuntimeException");
                arg.writeStr("");
                arg.writeInt(0);           // 0 фреймов
                arg.writeByte(Tags.EXCEPTION); // causeTag → следующий уровень
            }
        });
        assertCodecErrorAndClosed(chain);

        assertServerAlive(port);
    }

    // ---- helpers ----

    private interface ArgBuilder {
        void build(RmapByteWriter arg);
    }

    private void sendRget(ChaosRawClient c, String method, ArgBuilder argBuilder) {
        sendRget(c, method, argBuilder, 1L);
    }

    private void sendRget(ChaosRawClient c, String method, ArgBuilder argBuilder, long callId) {
        RmapByteWriter p = new RmapByteWriter();
        CallWire.encodeRgetHeader(p, 0, 0L, methodId(method), 5000, 1); // subjectId=0 (первый экспорт), argCount=1
        argBuilder.build(p);
        c.send(FrameType.RGET, callId, p.toByteArray());
    }

    private void assertCodecErrorAndClosed(ChaosRawClient c) throws Exception {
        Frame other = c.await(FrameType.OTHER);
        assertThat(otherCode(other)).isEqualTo(OtherCode.CODEC_ERROR);
        assertThat(c.closedLatch().await(5, TimeUnit.SECONDS)).as("соединение закрыто").isTrue();
    }

    /** Открыть НОВОЕ соединение и провести доброкачественный вызов → DONE: доказывает, что сервер жив. */
    private void assertServerAlive(int port) throws Exception {
        ChaosRawClient c = connect(port);
        RmapByteWriter p = new RmapByteWriter();
        CallWire.encodeRgetHeader(p, 0, 0L, methodId("takeBase"), 5000, 1);
        p.writeByte(Tags.NULL); // takeBase(null) → void → DONE(NULL)
        c.send(FrameType.RGET, 7L, p.toByteArray());
        Frame done = c.await(FrameType.DONE);
        assertThat(done.getCallId()).isEqualTo(7L);
    }

    private static int otherCode(Frame f) {
        return new RmapByteReader(f.getPayload(), 0, f.getPayload().length).readInt();
    }

    private static String otherMessage(Frame f) {
        RmapByteReader r = new RmapByteReader(f.getPayload(), 0, f.getPayload().length);
        r.readInt(); // code
        return r.readStr();
    }

    private static long methodId(String name) {
        for (Method m : Target.class.getMethods()) {
            if (m.getName().equals(name)) {
                return MethodIds.methodId(m);
            }
        }
        throw new AssertionError("no method " + name);
    }
}
