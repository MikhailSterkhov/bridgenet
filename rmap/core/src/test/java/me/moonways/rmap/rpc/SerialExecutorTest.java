package me.moonways.rmap.rpc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
}
