package me.moonways.rmap.codec;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;

/**
 * Движок TLV-кодека (спека §5). Задача 3 — скаляры; задача 4 добавляет объекты
 * (@RmapSerializable через Unsafe), интернирование классов (classRef), identity-map
 * с back-ref и лимит глубины. Составные типы/ValueCodec — задачи 5–6. Каждый
 * encode/decode-проход держит собственные ClassInterner/RefTable и счётчик глубины.
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
        encode(out, value, new ClassInterner(), new RefTable(), 0);
    }

    public Object decode(RmapByteReader in, Set<String> whitelist) {
        return decode(in, whitelist, new ClassInterner(), new RefTable(), 0);
    }

    /** Декод с {@link #ACCEPT_ALL_CLASSES}: без проверки whitelist. Только тесты/доверенный ввод. */
    public Object decode(RmapByteReader in) {
        return decode(in, ACCEPT_ALL_CLASSES);
    }

    // ---- encode ----
    private void encode(RmapByteWriter out, Object value, ClassInterner interner, RefTable refs, int depth) {
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
            interner.writeClassRef(out, c.isEnum() ? c : c.getSuperclass());
            out.writeStr(((Enum<?>) value).name());
            return;
        }
        // приоритет резолва (спека §5.3): ValueCodec бьёт serializable/встроенный тег.
        // Тип под ValueCodec — лист (в поля не спускаемся).
        @SuppressWarnings("unchecked")
        ValueCodec<Object> vc = (ValueCodec<Object>) registry.findCodec(c);
        if (vc != null) {
            checkDepth(depth); // лимит глубины на весь граф: рекурсивный ValueCodec не должен обходить гейт
            out.writeByte(Tags.VALUE_CODEC);
            interner.writeClassRef(out, c);
            RmapByteWriter body = new RmapByteWriter();
            TlvWriter nested = v -> encode(body, v, interner, refs, depth + 1);
            vc.write(new RmapOutput(body, nested), value);
            byte[] bodyBytes = body.toByteArray();
            out.writeInt(bodyBytes.length);
            out.writeRaw(bodyBytes);
            return;
        }
        // объекты (задача 4)
        if (registry.isSerializable(c)) {
            if (refBackRef(out, refs, value)) return;
            checkDepth(depth);
            out.writeByte(Tags.OBJECT);
            interner.writeClassRef(out, c);
            for (Field f : ClassSchema.of(c)) {
                encode(out, UnsafeAllocator.getField(value, f), interner, refs, depth + 1);
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
                encode(out, e.getKey(), interner, refs, depth + 1);
                encode(out, e.getValue(), interner, refs, depth + 1);
            }
            return;
        }
        if (value instanceof java.util.Set) {
            encodeSequence(out, (java.util.Collection<?>) value, Tags.SET, interner, refs, depth);
            return;
        }
        if (value instanceof java.util.Collection) { // List и прочие Collection → LIST
            encodeSequence(out, (java.util.Collection<?>) value, Tags.LIST, interner, refs, depth);
            return;
        }
        if (c.isArray() && !c.getComponentType().isPrimitive()) { // byte[] уже обработан выше как BYTES
            Object[] arr = (Object[]) value;
            if (refBackRef(out, refs, value)) return;
            checkDepth(depth);
            out.writeByte(Tags.ARRAY);
            interner.writeClassRef(out, c.getComponentType());
            out.writeInt(arr.length);
            for (Object el : arr) {
                encode(out, el, interner, refs, depth + 1);
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
                                ClassInterner interner, RefTable refs, int depth) {
        if (refBackRef(out, refs, coll)) return;
        checkDepth(depth);
        out.writeByte(tag);
        out.writeInt(coll.size());
        for (Object el : coll) {
            encode(out, el, interner, refs, depth + 1);
        }
    }

    // ---- decode ----
    private Object decode(RmapByteReader in, Set<String> whitelist, ClassInterner interner, RefTable refs, int depth) {
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
            case Tags.ENUM: return decodeEnum(in, whitelist, interner);
            case Tags.BACK_REF: return refs.readGet(in.readInt());
            case Tags.OBJECT: return decodeObject(in, whitelist, interner, refs, depth);
            case Tags.VALUE_CODEC: {
                checkDepth(depth); // симметрично encode: лимит глубины и на decode-стороне VC
                Class<?> vcClass = interner.readClassRef(in, whitelist);
                ValueCodec<?> codec = registry.findCodec(vcClass);
                if (codec == null) {
                    throw new RmapCodecException("no ValueCodec for " + vcClass.getName());
                }
                int len = in.readInt();
                if (len < 0 || len > in.remaining()) {
                    throw new RmapCodecException("bad value-codec length " + len);
                }
                int startPos = in.position();
                TlvReader nested = () -> decode(in, whitelist, interner, refs, depth + 1);
                Object v = codec.read(new RmapInput(in, nested));
                if (in.position() - startPos != len) {
                    throw new RmapCodecException("value-codec read " + (in.position() - startPos)
                            + " bytes, framed " + len);
                }
                return v;
            }
            case Tags.LIST: return decodeSequence(in, whitelist, interner, refs, depth, false);
            case Tags.SET: return decodeSequence(in, whitelist, interner, refs, depth, true);
            case Tags.MAP: return decodeMap(in, whitelist, interner, refs, depth);
            case Tags.ARRAY: return decodeArray(in, whitelist, interner, refs, depth);
            default:
                throw new RmapCodecException("unknown or not-yet-supported tag 0x" + Integer.toHexString(tag));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object decodeEnum(RmapByteReader in, Set<String> whitelist, ClassInterner interner) {
        Class<?> enumClass = interner.readClassRef(in, whitelist);
        String name = in.readStr();
        try {
            return Enum.valueOf((Class<Enum>) enumClass.asSubclass(Enum.class), name);
        } catch (IllegalArgumentException | ClassCastException e) {
            throw new RmapCodecException("bad enum " + enumClass.getName() + "#" + name, e);
        }
    }

    private Object decodeObject(RmapByteReader in, Set<String> whitelist, ClassInterner interner, RefTable refs, int depth) {
        checkDepth(depth);
        Class<?> c = interner.readClassRef(in, whitelist);
        if (c.isEnum() || Enum.class.isAssignableFrom(c)) {
            throw new RmapCodecException("enum must use ENUM tag, not OBJECT: " + c.getName());
        }
        Object instance = UnsafeAllocator.allocate(c);
        refs.readRegister(instance); // pre-order: до чтения полей (self-reference)
        for (Field f : ClassSchema.of(c)) {
            Object fieldValue = decode(in, whitelist, interner, refs, depth + 1);
            UnsafeAllocator.putField(instance, f, fieldValue);
        }
        return instance;
    }

    private Object decodeSequence(RmapByteReader in, Set<String> whitelist, ClassInterner interner,
                                  RefTable refs, int depth, boolean asSet) {
        checkDepth(depth);
        int size = in.readInt();
        if (size < 0) {
            throw new RmapCodecException("negative collection size: " + size);
        }
        java.util.Collection<Object> coll = asSet ? new java.util.LinkedHashSet<>() : new java.util.ArrayList<>();
        refs.readRegister(coll); // pre-order
        for (int i = 0; i < size; i++) {  // НЕ пре-аллоцируем по size: буфер отвергнет лишнее
            coll.add(decode(in, whitelist, interner, refs, depth + 1));
        }
        return coll;
    }

    private Object decodeMap(RmapByteReader in, Set<String> whitelist, ClassInterner interner,
                             RefTable refs, int depth) {
        checkDepth(depth);
        int size = in.readInt();
        if (size < 0) {
            throw new RmapCodecException("negative map size: " + size);
        }
        java.util.Map<Object, Object> map = new java.util.LinkedHashMap<>();
        refs.readRegister(map);
        for (int i = 0; i < size; i++) {
            Object k = decode(in, whitelist, interner, refs, depth + 1);
            Object v = decode(in, whitelist, interner, refs, depth + 1);
            map.put(k, v);
        }
        return map;
    }

    private Object decodeArray(RmapByteReader in, Set<String> whitelist, ClassInterner interner,
                               RefTable refs, int depth) {
        checkDepth(depth);
        Class<?> component = interner.readClassRef(in, whitelist);
        int len = in.readInt();
        if (len < 0) {
            throw new RmapCodecException("negative array length: " + len);
        }
        // Растим через список, потом копируем в типизированный массив (не пре-аллоцируем len).
        java.util.List<Object> tmp = new java.util.ArrayList<>();
        Object arr = java.lang.reflect.Array.newInstance(component, 0); // placeholder для регистрации
        refs.readRegister(arr);
        for (int i = 0; i < len; i++) {
            tmp.add(decode(in, whitelist, interner, refs, depth + 1));
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
