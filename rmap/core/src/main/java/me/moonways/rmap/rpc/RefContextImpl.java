package me.moonways.rmap.rpc;

import me.moonways.rmap.codec.RefContext;
import me.moonways.rmap.codec.RmapCodecException;

import java.util.Collections;
import java.util.Set;

/**
 * Серверная реализация {@link RefContext} (§10). Per-connection: держит {@link ObjectTable} и
 * «активный wrap-набор + snapshotRoot + opts» текущего ответа. Агент выставляет эту область прямо
 * перед encode ответа под write-локом {@link ConnectionCodec} ({@link #begin}/{@link #end} —
 * см. {@code ConnectionCodec.encodeAndSend}); поскольку encode ответов на одном соединении
 * сериализован тем же локом, простых полей достаточно.
 *
 * <ul>
 *   <li>{@link #remoteInterfaceFor(Object)} — значение уходит рефом, если реализует РОВНО один
 *       интерфейс активного wrap-набора; 2+ → {@link RmapCodecException} («ambiguous remote
 *       interface»); {@code value == snapshotRoot} (@Snapshot-корень) → {@code null} (кодировать
 *       значением, вложенные ref-поля при этом остаются рефами).</li>
 *   <li>{@link #registerRef(Object, Class)} → {@link ObjectTable#register}.</li>
 *   <li>{@link #proxyForRef(long, Class)} — на сервере ref-параметры запрещены аудитом → бросает.</li>
 * </ul>
 */
final class RefContextImpl implements RefContext {

    private final ObjectTable table;

    private volatile Set<Class<?>> activeWrap = Collections.emptySet();
    private volatile Object snapshotRoot;
    private volatile ExportOptions activeOpts;

    RefContextImpl(ObjectTable table) {
        this.table = table;
    }

    ObjectTable table() {
        return table;
    }

    /** Открыть область encode одного ответа (под write-локом ConnectionCodec). */
    void begin(Set<Class<?>> wrapSet, Object snapshotRoot, ExportOptions opts) {
        this.activeWrap = wrapSet == null ? Collections.<Class<?>>emptySet() : wrapSet;
        this.snapshotRoot = snapshotRoot;
        this.activeOpts = opts;
    }

    /** Закрыть область encode (finally, под тем же локом). */
    void end() {
        this.activeWrap = Collections.emptySet();
        this.snapshotRoot = null;
        this.activeOpts = null;
    }

    @Override
    public Class<?> remoteInterfaceFor(Object value) {
        if (value == null || value == snapshotRoot) {
            return null; // null / @Snapshot-корень — не реф (корень кодируется значением)
        }
        Set<Class<?>> wrap = activeWrap;
        if (wrap.isEmpty()) {
            return null;
        }
        Class<?> match = null;
        for (Class<?> iface : wrap) {
            if (iface.isInstance(value)) {
                if (match != null) {
                    throw new RmapCodecException("ambiguous remote interface for " + value.getClass().getName()
                            + ": " + match.getName() + " and " + iface.getName());
                }
                match = iface;
            }
        }
        return match;
    }

    @Override
    public long registerRef(Object value, Class<?> iface) {
        return table.register(value, iface, activeOpts);
    }

    @Override
    public Object proxyForRef(long refId, Class<?> iface) {
        // v1: ref-параметры запрещены export-аудитом → REMOTE_REF в позиции decode на сервере невозможен.
        throw new RmapCodecException("ref decode on server");
    }
}
