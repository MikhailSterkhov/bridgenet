package me.moonways.rmap.codec;

import me.moonways.rmap.api.RmapSerializable;
import me.moonways.rmap.codec.builtin.DateCodec;
import me.moonways.rmap.codec.builtin.InetSocketAddressCodec;
import me.moonways.rmap.codec.builtin.LocaleCodec;
import me.moonways.rmap.codec.builtin.TimestampCodec;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Реестр ValueCodec'ов и serializable-классов + резолв типа (спека §5.3). */
public final class CodecRegistry {

    private final Map<Class<?>, ValueCodec<?>> exact = new HashMap<>();
    private final Set<Class<?>> serializable = new HashSet<>();
    private final Map<Class<?>, ValueCodec<?>> resolveCache = new ConcurrentHashMap<>();

    private static final ValueCodec<?> NONE = new ValueCodec<Object>() {
        public Class<Object> type() { return Object.class; }
        public void write(RmapOutput out, Object value) { }
        public Object read(RmapInput in) { return null; }
    };

    /** Реестр стартует с 4 встроенными кодеками платформенных типов (спека §5.3). */
    public CodecRegistry() {
        register(new DateCodec());
        register(new TimestampCodec());
        register(new LocaleCodec());
        register(new InetSocketAddressCodec());
    }

    public CodecRegistry register(ValueCodec<?> codec) {
        exact.put(codec.type(), codec);
        resolveCache.clear();
        return this;
    }

    public CodecRegistry serializable(Class<?>... classes) {
        for (Class<?> c : classes) {
            serializable.add(c);
        }
        return this;
    }

    public boolean isSerializable(Class<?> c) {
        return serializable.contains(c) || c.isAnnotationPresent(RmapSerializable.class);
    }

    /** Точный тип → наиболее специфичный (самый узкий) зарегистрированный супертип/интерфейс;
     *  при несравнимых кандидатах tie-break по FQN {@code type().getName()} (детерминизм между JVM);
     *  null если совпадений нет. */
    public ValueCodec<?> findCodec(Class<?> type) {
        ValueCodec<?> cached = resolveCache.get(type);
        if (cached != null) {
            return cached == NONE ? null : cached;
        }
        ValueCodec<?> found = resolve(type);
        resolveCache.put(type, found == null ? NONE : found);
        return found;
    }

    private ValueCodec<?> resolve(Class<?> type) {
        ValueCodec<?> direct = exact.get(type);
        if (direct != null) {
            return direct;
        }
        // Среди всех assignable-кандидатов выбираем НАИБОЛЕЕ СПЕЦИФИЧНЫЙ (самый узкий);
        // при несравнимых типах — детерминированный tie-break по FQN. Порядок итерации
        // HashMap недетерминирован между JVM, поэтому «первый попавшийся» брать нельзя.
        ValueCodec<?> best = null;
        for (Map.Entry<Class<?>, ValueCodec<?>> e : exact.entrySet()) {
            if (e.getKey().isAssignableFrom(type)) {
                if (best == null
                        || best.type().isAssignableFrom(e.getKey())            // e — более узкий, чем best
                        || (!e.getKey().isAssignableFrom(best.type())          // несравнимы →
                            && e.getKey().getName().compareTo(best.type().getName()) < 0)) { // tie-break по FQN
                    best = e.getValue();
                }
            }
        }
        return best;
    }
}
