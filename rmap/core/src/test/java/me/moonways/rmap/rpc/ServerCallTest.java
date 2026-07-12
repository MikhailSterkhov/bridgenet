package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapNet;
import me.moonways.rmap.api.RmapServer;
import me.moonways.rmap.codec.ClassInterner;
import me.moonways.rmap.codec.CodecContext;
import me.moonways.rmap.codec.ExceptionData;
import me.moonways.rmap.codec.RmapByteReader;
import me.moonways.rmap.codec.RmapByteWriter;
import me.moonways.rmap.codec.RmapCodec;
import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.ConnectionListener;
import me.moonways.rmap.transport.NioTransport;
import me.moonways.rmap.transport.RmapConfig;
import me.moonways.rmap.transport.RmapConnection;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameType;
import me.moonways.rmap.wire.OtherCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wire-уровневые тесты серверного call-слоя БЕЗ клиентского прокси (он — задача 4):
 * сырой клиент делает handshake (publicAccess) и вручную шлёт LOOKUP/RGET-кадры.
 * Хелпер RawRpcClient — в этом же файле: подключиться, дождаться AUTH_OK,
 * отправить кадр, получать кадры в BlockingQueue.
 */
class ServerCallTest {

    public interface Calculator {
        int add(int a, int b);
        String fail(String message);
        void ping();
    }

    public static class CalculatorImpl implements Calculator {
        public int add(int a, int b) { return a + b; }
        public String fail(String message) { throw new IllegalStateException(message); }
        public void ping() { }
    }

    private RmapServer server;
    private NioTransport rawClient;

    private RmapConfig cfg() {
        return RmapConfig.builder().access(Access.publicAccess())
                .appVersion("b2-test").clientName("raw").build();
    }

    // --- хелпер сырого клиента: см. примечание после теста; методы:
    // RawRpcClient connect(int port)  — handshake publicAccess до AUTH_OK
    // void send(FrameType t, long callId, byte[] payload)
    // Frame await(FrameType t)        — ждать кадр типа t ≤5с (пропуская PING/PONG)

    @AfterEach
    void tearDown() {
        if (rawClient != null) rawClient.stop();
        if (server != null) server.stop();
    }

    private static long mid(String name) throws Exception {
        for (Method m : Calculator.class.getMethods()) {
            if (m.getName().equals(name)) return MethodIds.methodId(m);
        }
        throw new AssertionError(name);
    }

    @Test
    void lookup_then_rget_happy_path_and_exception_over_wire() throws Exception {
        RmapNet net = RmapNet.create();
        server = net.newServer(cfg());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("Services").export("Calc", Calculator.class, new CalculatorImpl());
        server.start();

        RawRpcClient raw = RawRpcClient.connect(server.boundPort(), cfg());
        this.rawClient = raw.transport();
        RmapCodec codec = new RmapCodec();
        ClassInterner wi = new ClassInterner();
        CodecContext wctx = CodecContext.of(wi, RmapCodec.ACCEPT_ALL_CLASSES);

        // LOOKUP с верным digest → LOOKUP_ACK(subjectId=0)
        RmapByteWriter lp = new RmapByteWriter();
        CallWire.encodeLookup(lp, "Services/Calc", MethodIds.interfaceDigest(Calculator.class));
        raw.send(FrameType.LOOKUP, 1L, lp.toByteArray());
        Frame ack = raw.await(FrameType.LOOKUP_ACK);
        assertThat(ack.getCallId()).isEqualTo(1L);
        int subjectId = CallWire.decodeLookupAck(new RmapByteReader(ack.getPayload(), 0, ack.getPayload().length));
        assertThat(subjectId).isGreaterThanOrEqualTo(0);

        // RGET add(2,3) → DONE(INT 5), эхо callId
        RmapByteWriter rp = new RmapByteWriter();
        CallWire.encodeRgetHeader(rp, subjectId, 0L, mid("add"), 5000, 2);
        codec.encode(rp, 2, wctx);
        codec.encode(rp, 3, wctx);
        raw.send(FrameType.RGET, 2L, rp.toByteArray());
        Frame done = raw.await(FrameType.DONE);
        assertThat(done.getCallId()).isEqualTo(2L);
        Object result = codec.decode(
                new RmapByteReader(done.getPayload(), 0, done.getPayload().length),
                CodecContext.of(new ClassInterner(), RmapCodec.ACCEPT_ALL_CLASSES));
        assertThat(result).isEqualTo(5);

        // RGET fail("kaboom") → OTHER(INTERNAL_ERROR) + EXCEPTION-TLV
        RmapByteWriter fp = new RmapByteWriter();
        CallWire.encodeRgetHeader(fp, subjectId, 0L, mid("fail"), 5000, 1);
        codec.encode(fp, "kaboom", wctx);
        raw.send(FrameType.RGET, 3L, fp.toByteArray());
        Frame other = raw.await(FrameType.OTHER);
        assertThat(other.getCallId()).isEqualTo(3L);
        CallWire.Other decoded = CallWire.decodeOther(
                new RmapByteReader(other.getPayload(), 0, other.getPayload().length),
                codec, CodecContext.of(new ClassInterner(), RmapCodec.ACCEPT_ALL_CLASSES));
        assertThat(decoded.getCode()).isEqualTo(OtherCode.INTERNAL_ERROR);
        assertThat(decoded.getException()).isNotNull();
        assertThat(decoded.getException().getClassName()).isEqualTo("java.lang.IllegalStateException");
        assertThat(decoded.getException().getMessage()).isEqualTo("kaboom");

        // void: RGET ping() → DONE(NULL)
        RmapByteWriter vp = new RmapByteWriter();
        CallWire.encodeRgetHeader(vp, subjectId, 0L, mid("ping"), 5000, 0);
        raw.send(FrameType.RGET, 4L, vp.toByteArray());
        Frame voidDone = raw.await(FrameType.DONE);
        Object voidResult = codec.decode(
                new RmapByteReader(voidDone.getPayload(), 0, voidDone.getPayload().length),
                CodecContext.of(new ClassInterner(), RmapCodec.ACCEPT_ALL_CLASSES));
        assertThat(voidResult).isNull();
    }

    @Test
    void lookup_negative_paths() throws Exception {
        RmapNet net = RmapNet.create();
        server = net.newServer(cfg());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("Services").export("Calc", Calculator.class, new CalculatorImpl());
        server.start();

        RawRpcClient raw = RawRpcClient.connect(server.boundPort(), cfg());
        this.rawClient = raw.transport();

        // неизвестный path → SUBJECT_UNDEFINED
        RmapByteWriter p1 = new RmapByteWriter();
        CallWire.encodeLookup(p1, "Services/Nope", 42L);
        raw.send(FrameType.LOOKUP, 10L, p1.toByteArray());
        Frame o1 = raw.await(FrameType.OTHER);
        assertThat(o1.getCallId()).isEqualTo(10L);
        assertThat(new RmapByteReader(o1.getPayload(), 0, o1.getPayload().length).readInt())
                .isEqualTo(OtherCode.SUBJECT_UNDEFINED);

        // неверный digest → DIGEST_MISMATCH
        RmapByteWriter p2 = new RmapByteWriter();
        CallWire.encodeLookup(p2, "Services/Calc", 0xDEADBEEFL);
        raw.send(FrameType.LOOKUP, 11L, p2.toByteArray());
        Frame o2 = raw.await(FrameType.OTHER);
        assertThat(new RmapByteReader(o2.getPayload(), 0, o2.getPayload().length).readInt())
                .isEqualTo(OtherCode.DIGEST_MISMATCH);
    }

    @Test
    void unknown_method_and_bad_arg_count() throws Exception {
        RmapNet net = RmapNet.create();
        server = net.newServer(cfg());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("Services").export("Calc", Calculator.class, new CalculatorImpl());
        server.start();

        RawRpcClient raw = RawRpcClient.connect(server.boundPort(), cfg());
        this.rawClient = raw.transport();
        RmapCodec codec = new RmapCodec();
        CodecContext wctx = CodecContext.of(new ClassInterner(), RmapCodec.ACCEPT_ALL_CLASSES);

        // methodId неизвестен → INVALID_SIGNATURE
        RmapByteWriter p1 = new RmapByteWriter();
        CallWire.encodeRgetHeader(p1, 0, 0L, 0x1234567890ABCDEFL, 5000, 0);
        raw.send(FrameType.RGET, 20L, p1.toByteArray());
        Frame o1 = raw.await(FrameType.OTHER);
        assertThat(new RmapByteReader(o1.getPayload(), 0, o1.getPayload().length).readInt())
                .isEqualTo(OtherCode.INVALID_SIGNATURE);

        // argCount ≠ арности → PROTOCOL_ERROR + разрыв соединения
        CountDownLatch closed = raw.closedLatch();
        RmapByteWriter p2 = new RmapByteWriter();
        CallWire.encodeRgetHeader(p2, 0, 0L, mid("add"), 5000, 1);
        codec.encode(p2, 2, wctx);
        raw.send(FrameType.RGET, 21L, p2.toByteArray());
        Frame o2 = raw.await(FrameType.OTHER);
        assertThat(new RmapByteReader(o2.getPayload(), 0, o2.getPayload().length).readInt())
                .isEqualTo(OtherCode.PROTOCOL_ERROR);
        assertThat(closed.await(5, TimeUnit.SECONDS)).as("соединение закрыто").isTrue();
    }

    @Test
    void expired_deadline_in_queue_gives_timed_out() throws Exception {
        // сервер с serialDispatch: первый вызов спит, второй протухает в очереди
        RmapNet net = RmapNet.create();
        server = net.newServer(cfg());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        SlowImpl slow = new SlowImpl();
        server.put("Services").export("Slow", SlowCalc.class, slow,
                ExportOptions.builder().serialDispatch(true).build());
        server.start();

        RawRpcClient raw = RawRpcClient.connect(server.boundPort(), cfg());
        this.rawClient = raw.transport();

        long slowMid = 0;
        for (Method m : SlowCalc.class.getMethods()) {
            if (m.getName().equals("slow")) slowMid = MethodIds.methodId(m);
        }
        RmapByteWriter p1 = new RmapByteWriter();
        CallWire.encodeRgetHeader(p1, 0, 0L, slowMid, 5000, 0);
        raw.send(FrameType.RGET, 30L, p1.toByteArray());   // займёт executor на ~700мс

        RmapByteWriter p2 = new RmapByteWriter();
        CallWire.encodeRgetHeader(p2, 0, 0L, slowMid, 100, 0); // deadline 100мс — протухнет в очереди
        raw.send(FrameType.RGET, 31L, p2.toByteArray());

        Frame timedOut = raw.awaitOtherWithCallId(31L);
        assertThat(new RmapByteReader(timedOut.getPayload(), 0, timedOut.getPayload().length).readInt())
                .isEqualTo(OtherCode.TIMED_OUT);
    }

    public interface SlowCalc { String slow(); }
    public static class SlowImpl implements SlowCalc {
        public String slow() {
            try { Thread.sleep(700); } catch (InterruptedException ignored) { }
            return "done";
        }
    }
}
