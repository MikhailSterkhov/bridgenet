package me.moonways.rmap.api;

import me.moonways.rmap.rpc.ExportOptions;
import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.RmapConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Исполняемая витрина публичного API §11: код ЭТОГО теста целиком приведён в {@code rmap/README.md}
 * как quick-start-пример (двойное назначение — тест и документация). Показывает sync/async/Optional/
 * void вызовы, DTO-параметр, кодируемый ТОЛЬКО через {@code .codec(...)} (без {@code @RmapSerializable}),
 * и remote-исключение.
 */
class CalculatorDemoTest {

    public interface Calculator {
        int add(int a, int b);
        CompletableFuture<Integer> addAsync(int a, int b);
        Optional<Integer> lastResult();
        void reset();
        int divide(int a, int b);                            // b==0 → ArithmeticException удалённо
        int recordAndAdd(HistoryEntry entry, int a, int b);   // DTO-параметр через .codec(...)
    }

    /** Плоский DTO БЕЗ {@code @RmapSerializable} — кодируем только благодаря {@code .codec(...)}
     *  на обеих сторонах (§5.1: "манифесты... плюс явные регистрации"). */
    public static class HistoryEntry {
        private String label;
        private int value;

        public HistoryEntry() { }
        public HistoryEntry(String label, int value) {
            this.label = label;
            this.value = value;
        }
        public String getLabel() { return label; }
        public int getValue() { return value; }
    }

    public static class CalculatorImpl implements Calculator {
        private final List<Integer> history = new CopyOnWriteArrayList<>();
        private final List<HistoryEntry> entries = new CopyOnWriteArrayList<>();

        public synchronized int add(int a, int b) {
            int r = a + b;
            history.add(r);
            return r;
        }
        public CompletableFuture<Integer> addAsync(int a, int b) {
            return CompletableFuture.completedFuture(add(a, b));
        }
        public synchronized Optional<Integer> lastResult() {
            return history.isEmpty() ? Optional.empty() : Optional.of(history.get(history.size() - 1));
        }
        public synchronized void reset() {
            history.clear();
        }
        public int divide(int a, int b) {
            return a / b; // b==0 → ArithmeticException, летит клиенту RmapRemoteException
        }
        public synchronized int recordAndAdd(HistoryEntry entry, int a, int b) {
            entries.add(entry);
            return add(a, b);
        }
    }

    private RmapServer server;
    private RmapClient client;

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    private RmapConfig cfg() {
        return RmapConfig.builder()
                .access(Access.privateKey("demo-key"))
                .appVersion("demo-1.0")
                .clientName("demo-client")
                .codec(c -> c.serializable(HistoryEntry.class))
                .build();
    }

    @Test
    void calculator_demo_showcase() throws Exception {
        RmapNet net = RmapNet.create();

        server = net.newServer(cfg());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("Demo").export("Calculator", Calculator.class, new CalculatorImpl(),
                ExportOptions.defaults());
        server.start();

        client = net.newClient(cfg());
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);

        Calculator calc = client.lookup("Demo/Calculator", Calculator.class);

        // sync
        assertThat(calc.add(2, 3)).isEqualTo(5);
        // async
        assertThat(calc.addAsync(10, 20).get(5, TimeUnit.SECONDS)).isEqualTo(30);
        // Optional
        assertThat(calc.lastResult()).contains(30);
        // void
        calc.reset();
        assertThat(calc.lastResult()).isEmpty();
        // DTO-параметр (кодируется благодаря .codec(c -> c.serializable(HistoryEntry.class)))
        assertThat(calc.recordAndAdd(new HistoryEntry("first", 1), 4, 5)).isEqualTo(9);
        // remote-исключение
        assertThatThrownBy(() -> calc.divide(1, 0))
                .isInstanceOf(RmapRemoteException.class)
                .hasMessageContaining("ArithmeticException");
    }
}
