package me.moonways.rmap.rpc;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Финревью-фикс A(C1) регрессия — ДЕТЕРМИНИРОВАННАЯ, in-memory, без сети (закрывает и T7-minor
 * «механизм OP_READ не заперт»).
 *
 * <p>Гонка pause/resume flow-control проявляется как ПЕРМАНЕНТНЫЙ stall только через петлю обратной связи
 * транспорта: снятый {@code OP_READ} останавливает чтение новых RGET → нет новых enqueue → потерянный
 * resume уже некому исправить. Симулируем эту петлю в памяти: один «читатель» (роль selector'а) ставит
 * новый вызов ТОЛЬКО пока reads не сняты; пул «воркеров» завершает вызовы конкурентно. {@code onEnqueue}
 * (инкремент + возможный pause) читателя и {@code onFinish} (декремент + возможный resume) воркеров
 * пересекают границу {@code max=2} тысячи раз.
 *
 * <p>На НЕзапертом коде interleaving «pause постановлен после resume» оставляет reads снятыми при
 * {@code inFlight<max}: читатель встаёт, воркеры дренируют in-flight до нуля, resume-триггер больше не
 * наступает → прогресс останавливается навсегда (страховочный дедлайн ловит зависание → ассерт валится).
 * Лок в {@link FlowController} исключает это: reads всегда согласованы со счётчиком, читатель не встаёт,
 * все вызовы обработаны.
 */
class FlowControllerTest {

    @Test
    void feedback_loop_never_permanently_stalls_under_concurrency() throws Exception {
        final int max = 2;
        final int total = 200_000;

        AtomicBoolean readsPaused = new AtomicBoolean(false);
        FlowController fc = new FlowController(max, new FlowController.Signal() {
            public void pauseReads() { readsPaused.set(true); }
            public void resumeReads() { readsPaused.set(false); }
        });

        ExecutorService workers = Executors.newFixedThreadPool(4);
        AtomicInteger submitted = new AtomicInteger(0);
        AtomicInteger finished = new AtomicInteger(0);
        try {
            long stallDeadline = System.currentTimeMillis() + 15_000L;
            int lastProgress = 0;
            long lastProgressAt = System.currentTimeMillis();
            while (finished.get() < total) {
                if (!readsPaused.get() && submitted.get() < total) {
                    submitted.incrementAndGet();
                    fc.onEnqueue();                 // роль selector'а: инкремент + возможный pauseReads
                    workers.execute(() -> {
                        fc.onFinish();              // воркер: декремент + возможный resumeReads
                        finished.incrementAndGet();
                    });
                } else {
                    Thread.onSpinWait();            // reads сняты — ждём resume от завершающегося воркера
                }
                // Страховка от вечного зависания теста: на НЕзапертом коде петля встаёт (reads сняты
                // навсегда) → прогресс замирает. Ловим «нет прогресса N мс» и выходим — ассерт ниже валит.
                int now = finished.get();
                if (now != lastProgress) {
                    lastProgress = now;
                    lastProgressAt = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - lastProgressAt > 3_000L
                        || System.currentTimeMillis() > stallDeadline) {
                    break;
                }
            }
        } finally {
            workers.shutdownNow();
        }

        assertThat(finished.get())
                .as("петля обратной связи не заглохла — все вызовы обработаны (порядок pause/resume согласован)")
                .isEqualTo(total);
        assertThat(readsPaused.get())
                .as("после дренажа reads НЕ сняты (inFlight=0 < max)").isFalse();
        assertThat(fc.inFlight()).as("in-flight вернулся к нулю").isZero();
    }
}
