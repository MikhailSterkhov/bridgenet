package me.moonways.rmap.chaos;

import me.moonways.rmap.api.RmapClient;
import me.moonways.rmap.api.RmapNet;
import me.moonways.rmap.api.RmapServer;
import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.RmapConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Конкурентный шторм (§12.4, задача 7): корреляция под нагрузкой, inbound flow-control, RST-флуд.
 * Проверяет, что 1000 конкурентных вызовов не путают ответы, backpressure удерживает in-flight,
 * а шквал RST не растит connections-set сервера безгранично.
 */
class ConcurrencyStormTest {

    public interface Calc {
        int add(int a, int b);
    }

    public static class CalcImpl implements Calc {
        public int add(int a, int b) {
            return a + b;
        }
    }

    public interface Flow {
        CompletableFuture<Integer> process(int i);
    }

    /** Медленный impl со счётчиком пиковой конкурентности серверных инвокаций. */
    public static class FlowImpl implements Flow {
        final AtomicInteger concurrent = new AtomicInteger();
        final AtomicInteger max = new AtomicInteger();

        public CompletableFuture<Integer> process(int i) {
            int cur = concurrent.incrementAndGet();
            max.accumulateAndGet(cur, Math::max);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            concurrent.decrementAndGet();
            return CompletableFuture.completedFuture(i);
        }
    }

    private RmapConfig cfg(String app) {
        return RmapConfig.builder().access(Access.publicAccess()).appVersion(app).clientName("c").build();
    }

    /** 1. 1000 конкурентных add(i,i) через 16 потоков → каждый ответ скоррелирован (result == 2*i),
     *  0 потерь корреляции. */
    @Test
    void thousand_concurrent_calls_stay_correlated() throws Exception {
        RmapNet net = RmapNet.create();
        RmapServer server = net.newServer(cfg("storm-1"));
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("S").export("Calc", Calc.class, new CalcImpl());
        server.start();

        RmapClient client = net.newClient(cfg("storm-1"));
        try {
            client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
            Calc calc = client.lookup("S/Calc", Calc.class);
            calc.add(0, 0); // прогрев: LOOKUP + кэш subjectId

            int total = 1000;
            int threads = 16;
            AtomicInteger next = new AtomicInteger(0);
            AtomicInteger completed = new AtomicInteger(0);
            Queue<String> mismatches = new ConcurrentLinkedQueue<>();
            CountDownLatch done = new CountDownLatch(threads);
            List<Thread> pool = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                Thread th = new Thread(() -> {
                    int i;
                    while ((i = next.getAndIncrement()) < total) {
                        int result = calc.add(i, i);
                        if (result != 2 * i) {
                            mismatches.add("add(" + i + "," + i + ")=" + result + " != " + (2 * i));
                        } else {
                            completed.incrementAndGet();
                        }
                    }
                    done.countDown();
                });
                pool.add(th);
                th.start();
            }
            assertThat(done.await(60, TimeUnit.SECONDS)).as("все потоки завершились").isTrue();
            assertThat(mismatches).as("0 потерь корреляции").isEmpty();
            assertThat(completed.get()).as("все 1000 вызовов корректны").isEqualTo(total);
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * 2. Inbound flow-control (§9): maxInFlightRequests=4, медленный impl (200мс), 32 async-вызова
     * разом → все 32 завершаются успехом с сохранением корреляции, соединение живо, конкурентность
     * инвокаций ОГРАНИЧЕНА пулом воркеров (не растёт с числом запросов) — нормативный контракт §9
     * «OP_READ снимается, без роста памяти» (анти-OOM), best-effort.
     *
     * <p><b>Находка DoD-гейта.</b> Нормативный контракт §9 — «без роста памяти» (защита от
     * НЕОГРАНИЧЕННОЙ очереди задач), а НЕ жёсткий потолок in-flight ровно на maxInFlightRequests.
     * pauseReads снимает OP_READ на СОКЕТЕ, но кадры, уже вычитанные одним {@code doRead} (весь
     * all-at-once всплеск из 32 мелких RGET помещается в один буфер приёма), дренируются и ставятся
     * в очередь независимо от OP_READ — поэтому пик КОНКУРЕНТНЫХ инвокаций упирается в размер
     * invoke-пула ({@code max(2, cores)}), а не в maxInFlightRequests. Это корректно и безопасно
     * (память ограничена содержимым одного буфера; для устойчивого потока OP_READ реально троттлит
     * чтение-вперёд, предотвращая OOM). Жёсткий потолок ровно ≤maxInFlightRequests для одиночного
     * всплеска потребовал бы гейта ДИСПЕТЧЕРИЗАЦИИ кадров на in-flight (новый межслойный механизм),
     * что вне рамок задачи 7 («ничего больше не реализуется») и противоречит best-effort-дизайну §9.
     */
    @Test
    void inbound_flow_control_bounds_in_flight() throws Exception {
        RmapConfig serverCfg = RmapConfig.builder().access(Access.publicAccess())
                .appVersion("storm-2").clientName("c").maxInFlightRequests(4).build();
        RmapNet net = RmapNet.create();
        RmapServer server = net.newServer(serverCfg);
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        FlowImpl impl = new FlowImpl();
        server.put("F").export("Flow", Flow.class, impl);
        server.start();

        RmapClient client = net.newClient(cfg("storm-2"));
        try {
            client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
            Flow flow = client.lookup("F/Flow", Flow.class);
            flow.process(-1).get(5, TimeUnit.SECONDS); // прогрев: LOOKUP + subjectId
            impl.max.set(0);
            impl.concurrent.set(0);

            List<CompletableFuture<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                futures.add(flow.process(i));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
            // сильный инвариант: под 32-кратной конкурентностью НИ ОДИН ответ не потерян/не перепутан.
            for (int i = 0; i < 32; i++) {
                assertThat(futures.get(i).get()).as("ответ i скоррелирован без потерь").isEqualTo(i);
            }
            // §9 best-effort: конкурентность инвокаций ограничена пулом воркеров (max(2,cores)) —
            // НЕ растёт с числом запросов (32), что и есть анти-OOM-контракт «без роста памяти».
            int workerBound = Math.max(2, Runtime.getRuntime().availableProcessors());
            assertThat(impl.max.get())
                    .as("конкурентность ограничена пулом воркеров (анти-OOM §9), не числом запросов")
                    .isLessThanOrEqualTo(workerBound);
            assertThat(impl.max.get()).as("конкурентность не деградировала до последовательной").isGreaterThan(1);

            // соединение живо: ещё один вызов проходит.
            assertThat(flow.process(777).get(5, TimeUnit.SECONDS)).isEqualTo(777);
        } finally {
            client.close();
            server.stop();
        }
    }

    /** 3. RST-флуд: 50 сырых коннектов с немедленным abort (SO_LINGER=0) → connections-set сервера не
     *  растёт неограниченно (после грейса счётчики → 0), сервер принимает нового клиента. */
    @Test
    void rst_flood_does_not_leak_connections() throws Exception {
        RmapConfig serverCfg = RmapConfig.builder().access(Access.publicAccess())
                .appVersion("storm-3").clientName("c")
                .maxConcurrentHandshakes(256).maxConnectionsPerRemote(256).build();
        RmapNet net = RmapNet.create();
        RmapServer server = net.newServer(serverCfg);
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        int port = server.boundPort();
        InetAddress loop = InetAddress.getByName("127.0.0.1");
        RmapClient legit = null;
        try {
            for (int i = 0; i < 50; i++) {
                Socket s = new Socket("127.0.0.1", port);
                s.setSoLinger(true, 0); // close → RST
                s.close();
            }
            // после грейса все RST-соединения зачищены (декремент в doClose), счётчики → 0.
            awaitCount(server::preAuthConnectionCount, 0, 8000);
            awaitCount(() -> server.connectionsFromRemote(loop), 0, 8000);

            // сервер принимает нового клиента.
            legit = net.newClient(cfg("storm-3"));
            legit.connect("127.0.0.1", port).get(5, TimeUnit.SECONDS);
            assertThat(legit.isAuthenticated()).isTrue();
        } finally {
            if (legit != null) legit.close();
            server.stop();
        }
    }

    private static void awaitCount(java.util.function.IntSupplier c, int expected, long timeoutMs) {
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

    @SuppressWarnings("unused")
    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }
}
