package me.moonways.rmap.rpc;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Per-connection таблица remote-ref'ов сервера (§10). {@code refId(int64, счётчик с 1) →
 * {strong ref, interface class, opts, lastAccessMillis}} плюс обратная identity-map для повторной
 * выдачи ТОГО ЖЕ refId на тот же объект. Живёт на время одной аутентифицированной сессии; на разрыв
 * — {@link #clear()}. Все операции под одним монитором (per-connection, низкая конкуренция).
 *
 * <p><b>Lease.</b> Порог {@code leaseTimeoutMillis} (дефолт 10 мин; {@code RmapConfig.refLeaseTimeout},
 * конструктор — единственная точка ввода, immutable на время жизни таблицы). {@link #get(long)}
 * лениво эвиктит запись, к которой не обращались дольше
 * порога (возврат {@code null} = «нет/умер» → сервер отвечает {@code STALE_REF}); общий scheduler
 * сервера дополнительно раз в минуту зовёт {@link #sweepExpired(long)} для проактивной очистки.
 */
final class ObjectTable {

    private final Object lock = new Object();
    private final Map<Long, Entry> byId = new HashMap<>();
    private final Map<Object, Long> byIdentity = new IdentityHashMap<>();
    private long nextRefId = 1L;
    private final long leaseTimeoutMillis;
    private long unknownReleaseCount;

    ObjectTable(long leaseTimeoutMillis) {
        this.leaseTimeoutMillis = leaseTimeoutMillis;
    }

    /** Регистрирует {@code impl} под refId; повторный (по identity) объект → прежний refId,
     *  обновляя lastAccess. {@code opts} — опции экспортирующего subject'а (для аудита манифеста
     *  ref-интерфейса при последующих ref-вызовах). */
    long register(Object impl, Class<?> iface, ExportOptions opts) {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            Long existing = byIdentity.get(impl);
            if (existing != null) {
                Entry e = byId.get(existing);
                if (e != null) {
                    e.lastAccessMillis = now;
                    return existing;
                }
                byIdentity.remove(impl); // осиротевшая identity-запись (эвиктнута) — регистрируем заново
            }
            long id = nextRefId++;
            byId.put(id, new Entry(id, impl, iface, opts, now));
            byIdentity.put(impl, id);
            return id;
        }
    }

    /** Запись по refId или {@code null} (нет либо истёк lease). На попадании обновляет lastAccess. */
    Entry get(long refId) {
        synchronized (lock) {
            Entry e = byId.get(refId);
            if (e == null) {
                return null;
            }
            long now = System.currentTimeMillis();
            if (now - e.lastAccessMillis >= leaseTimeoutMillis) {
                evict(e); // lease истёк — «умер»
                return null;
            }
            e.lastAccessMillis = now;
            return e;
        }
    }

    /** Освобождение refId. Неизвестный refId (гонка release↔lease-expiry) — МОЛЧА + метрика (§10). */
    void release(long refId) {
        synchronized (lock) {
            Entry e = byId.remove(refId);
            if (e == null) {
                unknownReleaseCount++;
                return;
            }
            unlinkIdentity(e);
        }
    }

    /** Разрыв соединения: таблица очищается целиком (все refs мертвы). */
    void clear() {
        synchronized (lock) {
            byId.clear();
            byIdentity.clear();
        }
    }

    int size() {
        synchronized (lock) {
            return byId.size();
        }
    }

    /** Lease-эвикция: убрать все записи без обращений дольше {@code olderThanMillis}. Возвращает
     *  число эвиктнутых записей (задача 6: лог sweep-эвикций на вызывающей стороне). */
    int sweepExpired(long olderThanMillis) {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            int evicted = 0;
            Iterator<Entry> it = byId.values().iterator();
            while (it.hasNext()) {
                Entry e = it.next();
                if (now - e.lastAccessMillis >= olderThanMillis) {
                    it.remove();
                    unlinkIdentity(e);
                    evicted++;
                }
            }
            return evicted;
        }
    }

    long getLeaseTimeoutMillis() {
        synchronized (lock) {
            return leaseTimeoutMillis;
        }
    }

    long unknownReleaseCount() {
        synchronized (lock) {
            return unknownReleaseCount;
        }
    }

    private void evict(Entry e) {
        byId.remove(e.refId);
        unlinkIdentity(e);
    }

    private void unlinkIdentity(Entry e) {
        Long cur = byIdentity.get(e.impl);
        if (cur != null && cur.longValue() == e.refId) {
            byIdentity.remove(e.impl);
        }
    }

    /** Запись таблицы: сильная ссылка на объект + его ref-интерфейс + опции-родителя + lastAccess. */
    static final class Entry {
        final long refId;
        final Object impl;
        final Class<?> iface;
        final ExportOptions opts;
        volatile long lastAccessMillis;

        Entry(long refId, Object impl, Class<?> iface, ExportOptions opts, long lastAccessMillis) {
            this.refId = refId;
            this.impl = impl;
            this.iface = iface;
            this.opts = opts;
            this.lastAccessMillis = lastAccessMillis;
        }
    }
}
