package me.moonways.rmap.chaos;

import me.moonways.rmap.api.RmapClient;
import me.moonways.rmap.api.RmapNet;
import me.moonways.rmap.api.RmapServer;
import me.moonways.rmap.transport.Access;
import me.moonways.rmap.transport.RmapConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Финревью-фикс A(C1) — сквозной liveness-соак: реальный async-CF + flow-control путь под нагрузкой
 * не должен глохнуть. {@code enqueueAndSubmit} (инкремент inFlight + pauseReads на invoke-потоке) и
 * {@code onCallFinished} (декремент + resumeReads на потоке-завершителе CF) пересекают границу
 * {@code maxInFlightRequests=2} тысячи раз; клиент держит ОГРАНИЧЕННОЕ число outstanding-вызовов
 * (семафор чуть выше max) и непрерывно доливает — серверный inFlight пришпилен к границе весь прогон.
 *
 * <p><b>Роль теста.</b> Проверяет, что сервер под async-нагрузкой обрабатывает все вызовы и остаётся
 * живым (round-trip после шторма). ДЕТЕРМИНИРОВАННЫЙ регресс-гейт самой гонки pause/resume — в
 * {@code me.moonways.rmap.rpc.FlowControllerTest} (in-memory петля обратной связи, надёжно валится на
 * незапертом {@code FlowController}); здесь чистый network-соак не гарантирует воспроизведение перманентного
 * stall'а (уже вычитанные в буфер RGET могут «спасти» спурьёзный pause), поэтому он — liveness/no-regress,
 * не строгий repro. Также покрывает интеграцию неблокирующей CF-распаковки (I1) на реальном транспорте.
 */
class FlowControlRaceTest {

    public interface Flow {
        CompletableFuture<Integer> process(int i);
    }

    /** CF завершается через рандомные 1–6мс на отдельном пуле — держит серверный in-flight «в полёте». */
    public static class SlowFlow implements Flow {
        final ScheduledExecutorService delay;

        SlowFlow(ScheduledExecutorService delay) {
            this.delay = delay;
        }

        public CompletableFuture<Integer> process(int i) {
            CompletableFuture<Integer> f = new CompletableFuture<>();
            long ms = 1 + ThreadLocalRandom.current().nextInt(6);
            delay.schedule(() -> f.complete(i), ms, TimeUnit.MILLISECONDS);
            return f;
        }
    }

    private RmapServer server;
    private RmapClient client;
    private ScheduledExecutorService delayPool;

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
        if (delayPool != null) delayPool.shutdownNow();
    }

    @Test
    void flow_control_boundary_storm_never_stalls_connection() throws Exception {
        delayPool = Executors.newScheduledThreadPool(8);
        // maxInFlightRequests=2 → каждый заезд/съезд с 2 пересекает границу pause/resume.
        // callTimeout высокий: здоровый прогон завершается быстро; заглохшее соединение блеванёт по дедлайну.
        RmapConfig serverCfg = RmapConfig.builder().access(Access.publicAccess())
                .appVersion("fc-race").clientName("c")
                .maxInFlightRequests(2).callTimeout(Duration.ofSeconds(20)).build();
        RmapConfig clientCfg = RmapConfig.builder().access(Access.publicAccess())
                .appVersion("fc-race").clientName("c").callTimeout(Duration.ofSeconds(20)).build();

        RmapNet net = RmapNet.create();
        server = net.newServer(serverCfg);
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.put("F").export("Flow", Flow.class, new SlowFlow(delayPool));
        server.start();

        client = net.newClient(clientCfg);
        client.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
        Flow flow = client.lookup("F/Flow", Flow.class);
        flow.process(-1).get(5, TimeUnit.SECONDS); // прогрев: LOOKUP + subjectId

        final int total = 4000;
        // outstanding капнут чуть выше maxInFlight=2 → сервер РЕАЛЬНО троттлит OP_READ и сидит на границе,
        // доливка держит непрерывный поток enqueue↔finish = максимум окна гонки за весь прогон.
        Semaphore outstanding = new Semaphore(4);
        AtomicInteger next = new AtomicInteger(0);
        AtomicInteger completed = new AtomicInteger(0);
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        CountDownLatch allDone = new CountDownLatch(total);

        for (int k = 0; k < total; k++) {
            // ждём слот с ПОТОЛКОМ: заглохшее соединение (пре-фикс) не освобождает слоты → tryAcquire
            // истекает → выходим из цикла, allDone.await ниже вернёт false → тест валится чисто (не виснет).
            if (!outstanding.tryAcquire(20, TimeUnit.SECONDS)) {
                break;
            }
            if (firstError.get() != null) {
                outstanding.release();
                break;
            }
            final int i = next.getAndIncrement();
            flow.process(i).whenComplete((val, err) -> {
                try {
                    if (err != null) {
                        firstError.compareAndSet(null, err);
                    } else if (val == null || val != i) {
                        firstError.compareAndSet(null,
                                new AssertionError("mismatch: process(" + i + ")=" + val));
                    } else {
                        completed.incrementAndGet();
                    }
                } finally {
                    outstanding.release();
                    allDone.countDown();
                }
            });
        }

        // Заглохшее соединение (пре-фикс) → outstanding-слоты не освобождаются / вызовы падают по дедлайну
        // → либо семафор так и не доливает (зависает выше), либо firstError выставлен таймаутом.
        assertThat(allDone.await(40, TimeUnit.SECONDS))
                .as("все вызовы завершились много быстрее idleTimeout — соединение не заглохло").isTrue();
        assertThat(firstError.get()).as("ни одного отказа/потери корреляции").isNull();
        assertThat(completed.get()).as("все вызовы успешны").isEqualTo(total);

        // Соединение живо после шторма: контрольный round-trip проходит.
        assertThat(flow.process(777).get(5, TimeUnit.SECONDS)).isEqualTo(777);
    }
}
