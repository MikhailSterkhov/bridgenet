package me.moonways.rmap.rpc;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиентский GC-триггерируемый {@code REF_RELEASE} (§10). На каждый ref-прокси —
 * {@link PhantomReference} + общий {@link ReferenceQueue}; тик клиентского scheduler'а (раз в 1с)
 * дренирует очередь в батч и отправляет {@code REF_RELEASE} при {@value #BATCH_FLUSH} накопленных
 * либо по {@value #TIME_FLUSH_MILLIS}-мс таймеру. Явный синхронный release — {@code RmapRefs.release}
 * (немедленный батч из 1 через {@link ClientSession#releaseRefs}). Живёт per-session; тик снимается
 * при разрыве соединения.
 */
final class RefReleaser {

    private static final int BATCH_FLUSH = 128;
    private static final long TIME_FLUSH_MILLIS = 30_000L;

    private final ClientSession session;
    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
    /** Держим phantom'ы живыми до enqueue: иначе сам phantom соберётся GC и в очередь не попадёт. */
    private final Map<PhantomReference<Object>, Long> phantoms = new ConcurrentHashMap<>();
    private final List<Long> batch = new ArrayList<>();
    private long lastFlushMillis = System.currentTimeMillis();

    RefReleaser(ClientSession session) {
        this.session = session;
    }

    /** Отслеживать ref-прокси: при его сборке GC → REF_RELEASE его refId серверу. */
    void register(Object proxy, long refId) {
        phantoms.put(new PhantomReference<>(proxy, queue), refId);
    }

    /** Тик (раз в 1с): собрать enqueued phantom'ы в батч; флаш по порогу/таймеру. */
    void tick() {
        Reference<?> r;
        while ((r = queue.poll()) != null) {
            Long refId = phantoms.remove(r);
            if (refId != null) {
                synchronized (batch) {
                    batch.add(refId);
                }
            }
        }
        boolean flush;
        synchronized (batch) {
            flush = batch.size() >= BATCH_FLUSH
                    || (!batch.isEmpty() && System.currentTimeMillis() - lastFlushMillis >= TIME_FLUSH_MILLIS);
        }
        if (flush) {
            flush();
        }
    }

    private void flush() {
        long[] ids;
        synchronized (batch) {
            if (batch.isEmpty()) {
                return;
            }
            ids = new long[batch.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = batch.get(i);
            }
            batch.clear();
            lastFlushMillis = System.currentTimeMillis();
        }
        session.releaseRefs(ids);
    }
}
