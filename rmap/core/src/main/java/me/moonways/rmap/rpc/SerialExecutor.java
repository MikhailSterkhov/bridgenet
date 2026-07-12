package me.moonways.rmap.rpc;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

/** FIFO-сериализация задач поверх общего пула: один in-flight, порядок submission (§5.2a×§9). */
public final class SerialExecutor implements Executor {

    private final Executor delegate;
    private final Queue<Runnable> queue = new ArrayDeque<>();
    private boolean running;

    public SerialExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable task) {
        synchronized (this) {
            queue.add(task);
            if (running) return;
            running = true;
        }
        delegate.execute(this::drain);
    }

    private void drain() {
        while (true) {
            Runnable next;
            synchronized (this) {
                next = queue.poll();
                if (next == null) {
                    running = false;
                    return;
                }
            }
            try {
                next.run();
            } catch (RuntimeException ignored) {
                // задача упала — очередь живёт; ошибку обрабатывает сама задача (agent шлёт OTHER)
            }
        }
    }
}
