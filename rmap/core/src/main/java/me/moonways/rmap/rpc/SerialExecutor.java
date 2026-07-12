package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapLogger;
import me.moonways.rmap.api.RmapLogging;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

/** FIFO-сериализация задач поверх общего пула: один in-flight, порядок submission (§5.2a×§9). */
public final class SerialExecutor implements Executor {

    private static final RmapLogger LOG = RmapLogging.get(SerialExecutor.class.getName());

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
        // Финревью-фикс A(I7б): delegate.execute может бросить (RejectedExecutionException при shutdown
        // пула) уже ПОСЛЕ running=true — тогда дренаж не стартовал, а running остался бы true НАВСЕГДА
        // (следующий execute видел бы running=true и не перезапустил дренаж → per-connection декод мёртв,
        // очередь копит кадры). Сбрасываем running под тем же локом и пробрасываем: задача остаётся в
        // очереди, последующий execute() перезапустит дренаж; вызывающий (транспорт) закроет соединение.
        try {
            delegate.execute(this::drain);
        } catch (RuntimeException e) {
            synchronized (this) {
                running = false;
            }
            throw e;
        }
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
            } catch (Throwable t) {
                // Финревью-фикс A(I7а): очередь ДОЛЖНА пережить любой бросок задачи. RuntimeException
                // обрабатывает сама задача (agent шлёт OTHER) — молча. Но Error (реальный источник:
                // decodeEnum→Enum.valueOf инициализирует whitelisted-класс → ExceptionInInitializerError
                // прямо на serial-потоке) раньше убивал drain-цикл с running=true → per-connection декод
                // МЁРТВ, очередь бесконечно копит кадры (OOM). Ловим Throwable — заклинивание хуже
                // (§I7); non-RuntimeException логируем (для RuntimeException задача уже ответила).
                if (!(t instanceof RuntimeException)) {
                    LOG.warn("serial task threw " + t.getClass().getName() + "; queue continues draining", t);
                }
            }
        }
    }
}
