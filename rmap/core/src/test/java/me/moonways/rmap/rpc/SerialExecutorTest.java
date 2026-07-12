package me.moonways.rmap.rpc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerialExecutorTest {

    @Test
    void tasks_run_in_fifo_order_and_never_concurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            SerialExecutor serial = new SerialExecutor(pool);
            List<Integer> order = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch done = new CountDownLatch(100);
            for (int i = 0; i < 100; i++) {
                int n = i;
                serial.execute(() -> {
                    order.add(n);
                    done.countDown();
                });
            }
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 100; i++) {
                assertThat(order.get(i)).isEqualTo(i);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failing_task_does_not_kill_the_queue() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            SerialExecutor serial = new SerialExecutor(pool);
            CountDownLatch survived = new CountDownLatch(1);
            serial.execute(() -> { throw new RuntimeException("boom"); });
            serial.execute(survived::countDown);
            assertThat(survived.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Финревью-фикс A(I7а): задача, бросившая {@link Error} (реальный источник — Enum.valueOf на
     * serial-потоке инициализирует whitelisted-класс → ExceptionInInitializerError), НЕ должна убить
     * drain-цикл (иначе running=true навсегда, per-connection декод мёртв). Детерминизм: Error-задача
     * ждёт, пока следующая задача заведомо стоит в очереди, ЗАТЕМ бросает.
     */
    @Test
    void task_throwing_error_does_not_kill_the_queue() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            SerialExecutor serial = new SerialExecutor(pool);
            CountDownLatch bothEnqueued = new CountDownLatch(1);
            CountDownLatch survived = new CountDownLatch(1);
            serial.execute(() -> {
                try { bothEnqueued.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                throw new StackOverflowError("boom on serial thread"); // Error, не RuntimeException
            });
            serial.execute(survived::countDown); // должен исполниться ПОСЛЕ Error-задачи
            bothEnqueued.countDown();
            assertThat(survived.await(5, TimeUnit.SECONDS))
                    .as("очередь пережила Error задачи и продолжила дренаж").isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Финревью-фикс A(I7б): бросок {@code delegate.execute} (RejectedExecutionException при shutdown
     * пула) уже ПОСЛЕ running=true оставлял running=true навсегда — следующий execute() не перезапускал
     * дренаж. После фикса running сброшен, следующий успешный execute() дренирует ОБЕ задачи.
     */
    @Test
    void delegate_execute_throwing_lets_next_execute_restart_drain() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            AtomicBoolean failNext = new AtomicBoolean(true);
            Executor flaky = task -> {
                if (failNext.getAndSet(false)) {
                    throw new RejectedExecutionException("boom on first submit");
                }
                pool.execute(task);
            };
            SerialExecutor serial = new SerialExecutor(flaky);
            CountDownLatch ran = new CountDownLatch(1);
            // первый execute: delegate бросает → running сброшен, задача осталась в очереди
            assertThatThrownBy(() -> serial.execute(ran::countDown))
                    .isInstanceOf(RejectedExecutionException.class);
            // второй execute: delegate работает → дренаж перезапущен, дренирует ОБЕ задачи
            serial.execute(() -> { });
            assertThat(ran.await(5, TimeUnit.SECONDS))
                    .as("дренаж перезапущен после броска delegate.execute").isTrue();
        } finally {
            pool.shutdownNow();
        }
    }
}
