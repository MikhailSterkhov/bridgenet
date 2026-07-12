package me.moonways.rmap.api;

import me.moonways.rmap.codec.RmapInput;
import me.moonways.rmap.codec.RmapOutput;
import me.moonways.rmap.codec.ValueCodec;
import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.RmapConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Публичный конфиг §11 реально доезжает до engine: {@code .codec(...)} доходит до encode/decode,
 * {@code refLeaseTimeout} применяется к ObjectTable, {@code callbackExecutor} действительно
 * исполняет continuation клиентских future. Плюс долг-фикс задачи 5→6 (не-{@code RmapCodecException}
 * из пользовательского {@code ValueCodec.write} → {@code OTHER(INTERNAL_ERROR)}, не hang).
 */
class ConfigWiringTest {

    private RmapServer server;
    private RmapClient client;

    private RmapConfig.RmapConfigBuilder baseCfg() {
        return RmapConfig.builder().access(Access.privateKey("wiring-key"))
                .appVersion("wiring-1").clientName("wiring-client");
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    // ---- сценарий 1: .codec(...) реально доезжает до encode/decode --------------------------

    public interface Echo {
        Widget echo(Widget w);
    }

    /** Плоский POJO БЕЗ {@code @RmapSerializable} — кодируем ТОЛЬКО через ValueCodec из .codec(...). */
    public static class Widget {
        private String name;
        private int count;

        public Widget() { }
        public Widget(String name, int count) { this.name = name; this.count = count; }
        public String getName() { return name; }
        public int getCount() { return count; }
    }

    static final class WidgetCodec implements ValueCodec<Widget> {
        public Class<Widget> type() { return Widget.class; }
        public void write(RmapOutput out, Widget v) {
            out.writeString(v.name);
            out.writeInt(v.count);
        }
        public Widget read(RmapInput in) {
            return new Widget(in.readString(), in.readInt());
        }
    }

    public static class EchoImpl implements Echo {
        public Widget echo(Widget w) { return new Widget(w.name + "-echo", w.count + 1); }
    }

    @Test
    void codec_config_reaches_encode_decode_and_roundtrips_custom_type() throws Exception {
        RmapNet net = RmapNet.create();
        server = net.newServer(baseCfg().codec(c -> c.register(new WidgetCodec())).build());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("Demo").export("Echo", Echo.class, new EchoImpl());
        server.start();

        client = net.newClient(baseCfg().codec(c -> c.register(new WidgetCodec())).build());
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        Echo echo = client.lookup("Demo/Echo", Echo.class);

        Widget result = echo.echo(new Widget("gizmo", 1));
        assertThat(result.getName()).isEqualTo("gizmo-echo");
        assertThat(result.getCount()).isEqualTo(2);
    }

    @Test
    void without_codec_config_unregistered_type_fails_export_audit() throws Exception {
        // Доказывает, что успех предыдущего теста — заслуга .codec(...), а не какого-то другого
        // пути: БЕЗ регистрации Widget не входит ни в один из путей encodability (§8) и export
        // обязан упасть RmapExportException.
        RmapNet net = RmapNet.create();
        server = net.newServer(baseCfg().build()); // дефолтный .codec — no-op
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        assertThatThrownBy(() -> server.put("Demo").export("Echo", Echo.class, new EchoImpl()))
                .isInstanceOf(RmapExportException.class)
                .hasMessageContaining("Widget");
    }

    // ---- сценарий 2: refLeaseTimeout из конфига применяется к ObjectTable --------------------

    public interface Box {
        // ДВА метода намеренно (не single-abstract-method) — иначе export-audit классифицирует
        // интерфейс как функциональный (callback) и отвергает его независимо от wrap-набора.
        int touch();
        int value();
    }

    public interface BoxHub {
        Box box();
    }

    public static class BoxImpl implements Box {
        private int n;
        public synchronized int touch() { return ++n; }
        public synchronized int value() { return n; }
    }

    public static class BoxHubImpl implements BoxHub {
        private final Box box = new BoxImpl();
        public Box box() { return box; }
    }

    @Test
    void ref_lease_timeout_from_config_expires_ref() throws Exception {
        RmapNet net = RmapNet.create();
        server = net.newServer(baseCfg().refLeaseTimeout(Duration.ofMillis(150)).build());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("Demo").export("BoxHub", BoxHub.class, new BoxHubImpl(),
                me.moonways.rmap.rpc.ExportOptions.builder().wrapReturnAsRemote(Box.class).build());
        server.start();

        client = net.newClient(baseCfg().build());
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        BoxHub hub = client.lookup("Demo/BoxHub", BoxHub.class);

        Box box = hub.box();
        assertThat(box.touch()).isEqualTo(1); // ref живой сразу после выдачи

        Thread.sleep(400); // дольше refLeaseTimeout(150мс) без обращений

        assertThatThrownBy(box::touch)
                .as("refLeaseTimeout из RmapConfig реально применяется к ObjectTable (без test-seam)")
                .isInstanceOf(RmapStaleRefException.class);
    }

    // ---- сценарий 3: callbackExecutor — continuation клиентских future исполняется на нём ----

    public interface Calc {
        CompletableFuture<Integer> addAsync(int a, int b);
    }

    public static class CalcImpl implements Calc {
        public CompletableFuture<Integer> addAsync(int a, int b) {
            return CompletableFuture.completedFuture(a + b);
        }
    }

    @Test
    void callback_executor_config_runs_future_continuation_on_marked_thread() throws Exception {
        String markerPrefix = "config-wiring-marker";
        ThreadFactory markedFactory = r -> {
            Thread t = new Thread(r, markerPrefix);
            t.setDaemon(true);
            return t;
        };
        Executor marked = Executors.newSingleThreadExecutor(markedFactory);

        RmapNet net = RmapNet.create();
        server = net.newServer(baseCfg().build());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("Demo").export("Calc", Calc.class, new CalcImpl());
        server.start();

        client = net.newClient(baseCfg().callbackExecutor(marked).build());
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        Calc calc = client.lookup("Demo/Calc", Calc.class);

        AtomicReference<String> continuationThread = new AtomicReference<>();
        CompletableFuture<Integer> withContinuation = calc.addAsync(2, 3)
                .whenComplete((v, t) -> continuationThread.set(Thread.currentThread().getName()));
        assertThat(withContinuation.get(5, TimeUnit.SECONDS)).isEqualTo(5);

        assertThat(continuationThread.get())
                .as("continuation клиентского future исполнилась на сконфигурированном callbackExecutor")
                .startsWith(markerPrefix);
    }

    @Test
    void default_callback_pool_shuts_down_on_client_close_no_leak() throws Exception {
        RmapNet net = RmapNet.create();
        server = net.newServer(baseCfg().build());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("Demo").export("Calc", Calc.class, new CalcImpl());
        server.start();

        client = net.newClient(baseCfg().build()); // callbackExecutor не задан → внутренний owned-пул
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        Calc calc = client.lookup("Demo/Calc", Calc.class);
        assertThat(calc.addAsync(1, 2).get(5, TimeUnit.SECONDS)).isEqualTo(3); // прогреваем пул

        client.close();
        client = null; // tearDown не должен закрывать повторно

        boolean stillAlive = threadNamedStillAliveAfter("rmap-client-callback", 2000L);
        assertThat(stillAlive)
                .as("дефолтный (owned) callback-пул закрыт в close() — нет утечки потоков").isFalse();
    }

    private static boolean threadNamedStillAliveAfter(String namePrefix, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            boolean any = Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(t -> t.getName().startsWith(namePrefix) && t.isAlive());
            if (!any) {
                return false;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
        }
        return true;
    }

    // ---- долг-фикс задачи 5→6: не-CodecException из ValueCodec.write → OTHER(INTERNAL_ERROR) ----

    public interface Boomer {
        Boom boom();
        String ping();
    }

    /** Маркерный тип, чей ValueCodec гарантированно бросает НЕ-{@code RmapCodecException} на write. */
    public static class Boom {
    }

    static final class BoomCodec implements ValueCodec<Boom> {
        public Class<Boom> type() { return Boom.class; }
        public void write(RmapOutput out, Boom v) {
            throw new IllegalStateException("boom on write"); // намеренно НЕ RmapCodecException
        }
        public Boom read(RmapInput in) { return new Boom(); }
    }

    public static class BoomerImpl implements Boomer {
        public Boom boom() { return new Boom(); }
        public String ping() { return "pong"; }
    }

    @Test
    void non_codec_runtime_exception_from_value_codec_write_yields_internal_error_not_hang() throws Exception {
        RmapNet net = RmapNet.create();
        server = net.newServer(baseCfg().codec(c -> c.register(new BoomCodec())).build());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("Demo").export("Boomer", Boomer.class, new BoomerImpl());
        server.start();

        client = net.newClient(baseCfg().codec(c -> c.register(new BoomCodec())).build());
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        Boomer boomer = client.lookup("Demo/Boomer", Boomer.class);

        long start = System.currentTimeMillis();
        assertThatThrownBy(boomer::boom)
                .as("IllegalStateException из ValueCodec.write долетает как OTHER(INTERNAL_ERROR), "
                        + "не проглатывается deadline'ом")
                .isInstanceOf(RmapRemoteException.class);
        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed)
                .as("быстрый ответ OTHER, а НЕ ожидание полного deadline (5с по умолчанию) — не hang")
                .isLessThan(2000L);

        // интернер/соединение не отравлены сбойным encode — обычный вызов после этого работает.
        assertThat(boomer.ping()).isEqualTo("pong");
    }
}
