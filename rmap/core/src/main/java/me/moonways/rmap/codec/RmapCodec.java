package me.moonways.rmap.codec;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;

/**
 * Движок TLV-кодека (спека §5). Задача 3 — скаляры; задача 4 добавляет объекты
 * (@RmapSerializable через Unsafe), интернирование классов (classRef), identity-map
 * с back-ref и лимит глубины. Составные типы/ValueCodec — задачи 5–6. Задача B2/1
 * добавляет {@link CodecContext} (connection-scoped interner/whitelist/refs) как
 * canonical-вход: каждый encode/decode-проход держит собственный {@link RefTable}
 * и счётчик глубины, а ClassInterner берётся из контекста (может жить дольше прохода).
 */
public final class RmapCodec {

    static final int MAX_DEPTH = 32; // спека §5.1

    /**
     * Whitelist-значение «принять любой класс из провода» для {@link #decode}. Резолв FQN
     * идёт БЕЗ проверки набора — небезопасно против враждебного ввода (произвольная загрузка
     * классов). Использовать ТОЛЬКО в тестах или для доверенного источника; в проде передавать
     * реальный набор разрешённых FQN.
     */
    public static final Set<String> ACCEPT_ALL_CLASSES = null;

    @FunctionalInterface interface TlvWriter { void write(Object value); }
    @FunctionalInterface interface TlvReader { Object read(); }

    private final CodecRegistry registry;

    /** Дефолтный реестр: {@link CodecRegistry} с 4 встроенными кодеками платформенных типов. */
    public RmapCodec() {
        this(new CodecRegistry());
    }

    public RmapCodec(CodecRegistry registry) {
        this.registry = registry;
    }

    public void encode(RmapByteWriter out, Object value) {
        encode(out, value, CodecContext.of(new ClassInterner(), ACCEPT_ALL_CLASSES));
    }

    public Object decode(RmapByteReader in, Set<String> whitelist) {
        return decode(in, CodecContext.of(new ClassInterner(), whitelist));
    }

    /** Декод с {@link #ACCEPT_ALL_CLASSES}: без проверки whitelist. Только тесты/доверенный ввод. */
    public Object decode(RmapByteReader in) {
        return decode(in, ACCEPT_ALL_CLASSES);
    }

    /** Canonical-вход encode: interner/whitelist/refs — из ctx; RefTable свежий per-вызов
     *  (back-ref НЕ пересекает границу кадра). */
    public void encode(RmapByteWriter out, Object value, CodecContext ctx) {
        encode(out, value, ctx, new RefTable(), 0);
    }

    /** Canonical-вход decode: см. {@link #encode(RmapByteWriter, Object, CodecContext)}. */
    public Object decode(RmapByteReader in, CodecContext ctx) {
        return decode(in, ctx, new RefTable(), 0);
    }

    /** EXCEPTION-тег 0x15 (§5.2): исключение уходит как ДАННЫЕ (см. {@link ExceptionData}). */
    public void encodeThrowable(RmapByteWriter out, Throwable t, CodecContext ctx) {
        encodeThrowable(out, t, ctx, new RefTable(), 0);
    }

    // ---- encode ----
    private void encode(RmapByteWriter out, Object value, CodecContext ctx, RefTable refs, int depth) {
        if (value == null) {
            out.writeByte(Tags.NULL);
            return;
        }
        Class<?> c = value.getClass();
        // скаляры (задача 3)
        if (c == Boolean.class) { out.writeByte(((Boolean) value) ? Tags.TRUE : Tags.FALSE); return; }
        if (c == Byte.class) { out.writeByte(Tags.BYTE); out.writeByte((Byte) value); return; }
        if (c == Short.class) { out.writeByte(Tags.SHORT); out.writeShort((Short) value); return; }
        if (c == Integer.class) { out.writeByte(Tags.INT); out.writeInt((Integer) value); return; }
        if (c == Long.class) { out.writeByte(Tags.LONG); out.writeLong((Long) value); return; }
        if (c == Float.class) { out.writeByte(Tags.FLOAT); out.writeFloat((Float) value); return; }
        if (c == Double.class) { out.writeByte(Tags.DOUBLE); out.writeDouble((Double) value); return; }
        if (c == Character.class) { out.writeByte(Tags.CHAR); out.writeChar((Character) value); return; }
        if (c == String.class) { out.writeByte(Tags.STRING); out.writeStr((String) value); return; }
        if (c == UUID.class) {
            out.writeByte(Tags.UUID);
            out.writeLong(((UUID) value).getMostSignificantBits());
            out.writeLong(((UUID) value).getLeastSignificantBits());
            return;
        }
        if (c == byte[].class) {
            byte[] d = (byte[]) value;
            out.writeByte(Tags.BYTES);
            out.writeInt(d.length);
            out.writeRaw(d);
            return;
        }
        if (Enum.class.isAssignableFrom(c)) {
            out.writeByte(Tags.ENUM);
            ctx.interner().writeClassRef(out, c.isEnum() ? c : c.getSuperclass());
            out.writeStr(((Enum<?>) value).name());
            return;
        }
        // REMOTE_REF (§10): ПЕРВОЙ в объектной части, ДО ValueCodec-резолва — live-ref
        // семантика бьёт кодирование значением. Полные тесты — задача 5.
        if (ctx.refs() != null) {
            Class<?> refIface = ctx.refs().remoteInterfaceFor(value);
            if (refIface != null) {
                out.writeByte(Tags.REMOTE_REF);
                out.writeLong(ctx.refs().registerRef(value, refIface));
                ctx.interner().writeClassRef(out, refIface);
                return;
            }
        }
        // приоритет резолва (спека §5.3): ValueCodec бьёт serializable/встроенный тег.
        // Тип под ValueCodec — лист (в поля не спускаемся).
        @SuppressWarnings("unchecked")
        ValueCodec<Object> vc = (ValueCodec<Object>) registry.findCodec(c);
        if (vc != null) {
            checkDepth(depth); // лимит глубины на весь граф: рекурсивный ValueCodec не должен обходить гейт
            out.writeByte(Tags.VALUE_CODEC);
            ctx.interner().writeClassRef(out, c);
            RmapByteWriter body = new RmapByteWriter();
            TlvWriter nested = v -> encode(body, v, ctx, refs, depth + 1);
            vc.write(new RmapOutput(body, nested), value);
            byte[] bodyBytes = body.toByteArray();
            out.writeInt(bodyBytes.length);
            out.writeRaw(bodyBytes);
            return;
        }
        // EXCEPTION (§5.2, §7.3): Throwable/ExceptionData — ПОСЛЕ ValueCodec-резолва, ДО isSerializable.
        if (value instanceof Throwable) { encodeThrowable(out, (Throwable) value, ctx, refs, depth); return; }
        if (value instanceof ExceptionData) { writeExceptionData(out, (ExceptionData) value, 0); return; }
        // объекты (задача 4)
        if (registry.isSerializable(c)) {
            if (refBackRef(out, refs, value)) return;
            checkDepth(depth);
            out.writeByte(Tags.OBJECT);
            ctx.interner().writeClassRef(out, c);
            for (Field f : ClassSchema.of(c)) {
                encode(out, UnsafeAllocator.getField(value, f), ctx, refs, depth + 1);
            }
            return;
        }
        // коллекции/массивы (задача 5)
        if (value instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) value;
            if (refBackRef(out, refs, value)) return;
            checkDepth(depth);
            out.writeByte(Tags.MAP);
            out.writeInt(m.size());
            for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                encode(out, e.getKey(), ctx, refs, depth + 1);
                encode(out, e.getValue(), ctx, refs, depth + 1);
            }
            return;
        }
        if (value instanceof java.util.Set) {
            encodeSequence(out, (java.util.Collection<?>) value, Tags.SET, ctx, refs, depth);
            return;
        }
        if (value instanceof java.util.Collection) { // List и прочие Collection → LIST
            encodeSequence(out, (java.util.Collection<?>) value, Tags.LIST, ctx, refs, depth);
            return;
        }
        if (c.isArray() && !c.getComponentType().isPrimitive()) { // byte[] уже обработан выше как BYTES
            Object[] arr = (Object[]) value;
            if (refBackRef(out, refs, value)) return;
            checkDepth(depth);
            out.writeByte(Tags.ARRAY);
            ctx.interner().writeClassRef(out, c.getComponentType());
            out.writeInt(arr.length);
            for (Object el : arr) {
                encode(out, el, ctx, refs, depth + 1);
            }
            return;
        }
        throw new RmapCodecException("no encoding for " + c.getName() + " (ValueCodec — задача 6)");
    }

    private boolean refBackRef(RmapByteWriter out, RefTable refs, Object value) {
        int existing = refs.writeIndexOrRegister(value);
        if (existing >= 0) {
            out.writeByte(Tags.BACK_REF);
            out.writeInt(existing);
            return true;
        }
        return false;
    }

    private void encodeSequence(RmapByteWriter out, java.util.Collection<?> coll, int tag,
                                CodecContext ctx, RefTable refs, int depth) {
        if (refBackRef(out, refs, coll)) return;
        checkDepth(depth);
        out.writeByte(tag);
        out.writeInt(coll.size());
        for (Object el : coll) {
            encode(out, el, ctx, refs, depth + 1);
        }
    }

    // ---- EXCEPTION (§5.2, §7.3) ----
    private void encodeThrowable(RmapByteWriter out, Throwable t, CodecContext ctx, RefTable refs, int depth) {
        writeExceptionData(out, toExceptionData(t, 0), 0);
    }

    private ExceptionData toExceptionData(Throwable t, int level) {
        StackTraceElement[] st = t.getStackTrace();
        int n = Math.min(64, st.length);
        ExceptionData.StackFrame[] frames = new ExceptionData.StackFrame[n];
        for (int i = 0; i < n; i++) {
            frames[i] = new ExceptionData.StackFrame(st[i].getClassName(), st[i].getMethodName(),
                    st[i].getFileName() == null ? "" : st[i].getFileName(), st[i].getLineNumber());
        }
        ExceptionData cause = (t.getCause() != null && t.getCause() != t && level < 7)
                ? toExceptionData(t.getCause(), level + 1) : null;
        return new ExceptionData(t.getClass().getName(),
                t.getMessage(), frames, cause);
    }

    private void writeExceptionData(RmapByteWriter out, ExceptionData d, int level) {
        if (level >= 8) { out.writeByte(Tags.NULL); return; } // страховка обрезки
        out.writeByte(Tags.EXCEPTION);
        out.writeStr(d.getClassName());
        out.writeStr(d.getMessage() == null ? "" : d.getMessage());
        out.writeInt(d.getFrames().length);
        for (ExceptionData.StackFrame f : d.getFrames()) {
            out.writeStr(f.getDeclaringClass());
            out.writeStr(f.getMethodName());
            out.writeStr(f.getFileName());
            out.writeInt(f.getLineNumber());
        }
        if (d.getCause() != null) writeExceptionData(out, d.getCause(), level + 1);
        else out.writeByte(Tags.NULL);
    }

    private ExceptionData readExceptionData(RmapByteReader in, int chainDepth) {
        if (chainDepth >= 8) throw new RmapCodecException("exception cause chain too deep");
        String cls = in.readStr();
        String msg = in.readStr();
        int depth = in.readInt();
        if (depth < 0 || depth > 64) throw new RmapCodecException("bad exception stack depth: " + depth);
        ExceptionData.StackFrame[] frames = new ExceptionData.StackFrame[depth];
        for (int i = 0; i < depth; i++) {
            frames[i] = new ExceptionData.StackFrame(in.readStr(), in.readStr(), in.readStr(), in.readInt());
        }
        int causeTag = in.readUnsignedByte();
        ExceptionData cause;
        if (causeTag == Tags.EXCEPTION) cause = readExceptionData(in, chainDepth + 1);
        else if (causeTag == Tags.NULL) cause = null;
        else throw new RmapCodecException("bad exception cause tag 0x" + Integer.toHexString(causeTag));
        return new ExceptionData(cls, msg.isEmpty() ? null : msg, frames, cause);
    }

    // ---- decode ----
    private Object decode(RmapByteReader in, CodecContext ctx, RefTable refs, int depth) {
        int tag = in.readUnsignedByte();
        switch (tag) {
            case Tags.NULL: return null;
            case Tags.TRUE: return Boolean.TRUE;
            case Tags.FALSE: return Boolean.FALSE;
            case Tags.BYTE: return (byte) in.readUnsignedByte();
            case Tags.SHORT: return (short) in.readShort();
            case Tags.INT: return in.readInt();
            case Tags.LONG: return in.readLong();
            case Tags.FLOAT: return in.readFloat();
            case Tags.DOUBLE: return in.readDouble();
            case Tags.CHAR: return in.readChar();
            case Tags.STRING: return in.readStr();
            case Tags.UUID: return new UUID(in.readLong(), in.readLong());
            case Tags.BYTES: return in.readRaw(in.readInt());
            case Tags.ENUM: return decodeEnum(in, ctx);
            case Tags.BACK_REF: return refs.readGet(in.readInt());
            case Tags.OBJECT: return decodeObject(in, ctx, refs, depth);
            case Tags.REMOTE_REF: {
                if (ctx.refs() == null) {
                    throw new RmapCodecException("remote refs not supported in this context");
                }
                long refId = in.readLong();
                Class<?> iface = ctx.interner().readClassRef(in, ctx.whitelist());
                return ctx.refs().proxyForRef(refId, iface);
            }
            case Tags.EXCEPTION: return readExceptionData(in, 0);
            case Tags.VALUE_CODEC: {
                checkDepth(depth); // симметрично encode: лимит глубины и на decode-стороне VC
                Class<?> vcClass = ctx.interner().readClassRef(in, ctx.whitelist());
                ValueCodec<?> codec = registry.findCodec(vcClass);
                if (codec == null) {
                    throw new RmapCodecException("no ValueCodec for " + vcClass.getName());
                }
                int len = in.readInt();
                if (len < 0 || len > in.remaining()) {
                    throw new RmapCodecException("bad value-codec length " + len);
                }
                int startPos = in.position();
                TlvReader nested = () -> decode(in, ctx, refs, depth + 1);
                Object v = codec.read(new RmapInput(in, nested));
                if (in.position() - startPos != len) {
                    throw new RmapCodecException("value-codec read " + (in.position() - startPos)
                            + " bytes, framed " + len);
                }
                return v;
            }
            case Tags.LIST: return decodeSequence(in, ctx, refs, depth, false);
            case Tags.SET: return decodeSequence(in, ctx, refs, depth, true);
            case Tags.MAP: return decodeMap(in, ctx, refs, depth);
            case Tags.ARRAY: return decodeArray(in, ctx, refs, depth);
            default:
                throw new RmapCodecException("unknown or not-yet-supported tag 0x" + Integer.toHexString(tag));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object decodeEnum(RmapByteReader in, CodecContext ctx) {
        Class<?> enumClass = ctx.interner().readClassRef(in, ctx.whitelist());
        String name = in.readStr();
        try {
            return Enum.valueOf((Class<Enum>) enumClass.asSubclass(Enum.class), name);
        } catch (IllegalArgumentException | ClassCastException e) {
            throw new RmapCodecException("bad enum " + enumClass.getName() + "#" + name, e);
        }
    }

    private Object decodeObject(RmapByteReader in, CodecContext ctx, RefTable refs, int depth) {
        checkDepth(depth);
        Class<?> c = ctx.interner().readClassRef(in, ctx.whitelist());
        if (c.isEnum() || Enum.class.isAssignableFrom(c)) {
            throw new RmapCodecException("enum must use ENUM tag, not OBJECT: " + c.getName());
        }
        Object instance = UnsafeAllocator.allocate(c);
        refs.readRegister(instance); // pre-order: до чтения полей (self-reference)
        for (Field f : ClassSchema.of(c)) {
            Object fieldValue = decode(in, ctx, refs, depth + 1);
            UnsafeAllocator.putField(instance, f, fieldValue);
        }
        return instance;
    }

    private Object decodeSequence(RmapByteReader in, CodecContext ctx,
                                  RefTable refs, int depth, boolean asSet) {
        checkDepth(depth);
        int size = in.readInt();
        if (size < 0) {
            throw new RmapCodecException("negative collection size: " + size);
        }
        java.util.Collection<Object> coll = asSet ? new java.util.LinkedHashSet<>() : new java.util.ArrayList<>();
        refs.readRegister(coll); // pre-order
        for (int i = 0; i < size; i++) {  // НЕ пре-аллоцируем по size: буфер отвергнет лишнее
            coll.add(decode(in, ctx, refs, depth + 1));
        }
        return coll;
    }

    private Object decodeMap(RmapByteReader in, CodecContext ctx, RefTable refs, int depth) {
        checkDepth(depth);
        int size = in.readInt();
        if (size < 0) {
            throw new RmapCodecException("negative map size: " + size);
        }
        java.util.Map<Object, Object> map = new java.util.LinkedHashMap<>();
        refs.readRegister(map);
        for (int i = 0; i < size; i++) {
            Object k = decode(in, ctx, refs, depth + 1);
            Object v = decode(in, ctx, refs, depth + 1);
            map.put(k, v);
        }
        return map;
    }

    private Object decodeArray(RmapByteReader in, CodecContext ctx, RefTable refs, int depth) {
        checkDepth(depth);
        Class<?> component = ctx.interner().readClassRef(in, ctx.whitelist());
        int len = in.readInt();
        if (len < 0) {
            throw new RmapCodecException("negative array length: " + len);
        }
        // Растим через список, потом копируем в типизированный массив (не пре-аллоцируем len).
        java.util.List<Object> tmp = new java.util.ArrayList<>();
        Object arr = java.lang.reflect.Array.newInstance(component, 0); // placeholder для регистрации
        refs.readRegister(arr);
        for (int i = 0; i < len; i++) {
            tmp.add(decode(in, ctx, refs, depth + 1));
        }
        Object result = java.lang.reflect.Array.newInstance(component, tmp.size());
        for (int i = 0; i < tmp.size(); i++) {
            java.lang.reflect.Array.set(result, i, tmp.get(i));
        }
        return result;
    }

    private static void checkDepth(int depth) {
        if (depth >= MAX_DEPTH) {
            throw new RmapCodecException("max decode depth exceeded: " + depth);
        }
    }
}
