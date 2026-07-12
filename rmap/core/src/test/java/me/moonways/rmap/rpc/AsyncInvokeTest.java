package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapClient;
import me.moonways.rmap.api.RmapNet;
import me.moonways.rmap.api.RmapServer;
import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.RmapConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Финревью-фикс A(I1): серверная распаковка {@code CompletableFuture}-возврата НЕ блокирует invoke-поток
 * (whenComplete + deadline-таймер), поэтому N медленных/циклических CF-вызовов не морят invoke-пул
 * (и вместе с ним serial-decode всех соединений). Раньше блокирующий {@code future.get} на invoke-потоке
 * при {@code N=cores} медленных CF занимал все потоки → сервер переставал декодировать до дедлайнов.
 */
class AsyncInvokeTest {

    /** Сервис с CF, завершаемым ДРУГИМ RMAP-вызовом (циклическое ожидание — дедлок при блокирующем get). */
    public interface Rendezvous {
        CompletableFuture<String> awaitSignal();
        String signal(String value);
    }

    public static class RendezvousImpl implements Rendezvous {
        final Queue<CompletableFuture<String>> waiters = new ConcurrentLinkedQueue<>();

        public CompletableFuture<String> awaitSignal() {
            CompletableFuture<String> f = new CompletableFuture<>();
            waiters.add(f);
            return f; // НЕ завершён: завершит его серверный signal() (другой RMAP-вызов)
        }

        public String signal(String value) {
            int n = 0;
            CompletableFuture<String> f;
            while ((f = waiters.poll()) != null) {
                f.complete(value);
                n++;
            }
            return "signaled:" + n;
        }
    }

    /** Сервис с медленным CF (завершается через задержку) + мгновенный вызов. */
    public interface Mixed {
        CompletableFuture<Integer> slow(int i);
        int fast(int i);
    }

    public static class MixedImpl implements Mixed {
        final ScheduledExecutorService delay;

        MixedImpl(ScheduledExecutorService delay) {
            this.delay = delay;
        }

        public CompletableFuture<Integer> slow(int i) {
            CompletableFuture<Integer> f = new CompletableFuture<>();
            delay.schedule(() -> f.complete(i), 1500, TimeUnit.MILLISECONDS); // держим «в полёте» 1.5с
            return f;
        }

        public int fast(int i) {
            return i * 2;
        }
    }

    private RmapServer server;
    private RmapClient client;
    private ScheduledExecutorService delayPool;

    private RmapConfig cfg() {
        return RmapConfig.builder().access(Access.publicAccess())
                .appVersion("async-i1").clientName("c")
                .callTimeout(Duration.ofSeconds(20)).build(); // без спуриозных таймаутов в здоровом кейсе
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
        if (delayPool != null) delayPool.shutdownNow();
    }

    /**
     * Циклический CF: N&gt;cores вызовов {@code awaitSignal()} висят (CF не завершён), затем {@code signal()}
     * (другой RMAP-вызов на ТОМ ЖЕ invoke-пуле) должен исполниться и разбудить их. При блокирующем get все
     * invoke-потоки заняты get'ами awaitSignal → signal не декодируется/не исполняется → дедлок.
     */
    @Test
    void future_completed_by_another_call_does_not_deadlock_invoke_pool() throws Exception {
        RmapNet net = RmapNet.create();
        server = net.newServer(cfg());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("R").export("Rendezvous", Rendezvous.class, new RendezvousImpl());
        server.start();

        client = net.newClient(cfg());
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        Rendezvous svc = client.lookup("R/Rendezvous", Rendezvous.class);

        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        int n = cores + 4; // строго больше invoke-пула max(2,cores)
        List<CompletableFuture<String>> waits = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            waits.add(svc.awaitSignal()); // async: future возвращается сразу, CF на сервере ещё не завершён
        }
        // дать RGET'ам долететь и зарегистрироваться серверными awaitSignal-инвокациями
        Thread.sleep(400);

        // signal — sync-вызов; при блокирующем get он бы завис (все invoke-потоки на get awaitSignal).
        // несколько попыток на случай CI-медлительности регистрации (complete уже завершённого — no-op).
        boolean allDone = false;
        for (int attempt = 0; attempt < 5 && !allDone; attempt++) {
            String res = svc.signal("go");
            assertThat(res).as("signal исполнился, несмотря на " + n + " висящих CF").startsWith("signaled:");
            allDone = true;
            for (CompletableFuture<String> w : waits) {
                if (!w.isDone()) { allDone = false; break; }
            }
            if (!allDone) Thread.sleep(200);
        }

        CompletableFuture.allOf(waits.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        for (CompletableFuture<String> w : waits) {
            assertThat(w.get()).as("висевший CF разбужен другим вызовом").isEqualTo("go");
        }
        // соединение живо
        assertThat(svc.signal("again")).isEqualTo("signaled:0");
    }

    /**
     * N&gt;cores медленных CF-вызовов «в полёте» (1.5с) НЕ должны голодать decode/invoke других вызовов:
     * параллельный мгновенный {@code fast()} обязан вернуться много быстрее длительности медленных CF.
     * При блокирующем get все invoke-потоки заняты медленными get'ами → fast не декодируется ~1.5с.
     */
    @Test
    void pending_slow_futures_do_not_starve_other_calls() throws Exception {
        delayPool = Executors.newScheduledThreadPool(2);
        RmapNet net = RmapNet.create();
        server = net.newServer(cfg());
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("M").export("Mixed", Mixed.class, new MixedImpl(delayPool));
        server.start();

        client = net.newClient(cfg());
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        Mixed svc = client.lookup("M/Mixed", Mixed.class);
        assertThat(svc.fast(1)).isEqualTo(2); // прогрев: LOOKUP + subjectId

        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        int n = cores + 2;
        List<CompletableFuture<Integer>> slows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            slows.add(svc.slow(i)); // async: возвращается сразу, CF завершится через 1.5с
        }
        Thread.sleep(150); // дать медленным RGET'ам сесть на сервер

        long t0 = System.currentTimeMillis();
        int fast = svc.fast(21); // sync: должен пройти НЕ дожидаясь медленных CF
        long elapsed = System.currentTimeMillis() - t0;
        assertThat(fast).isEqualTo(42);
        assertThat(elapsed).as("fast() не заблокирован висящими CF (§I1)").isLessThan(1000);

        // все медленные CF в итоге завершаются корректно
        CompletableFuture.allOf(slows.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        for (int i = 0; i < n; i++) {
            assertThat(slows.get(i).get()).isEqualTo(i);
        }
    }
}
