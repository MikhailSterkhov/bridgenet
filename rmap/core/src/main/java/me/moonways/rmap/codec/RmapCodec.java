package me.moonways.rmap.codec;

import me.moonways.rmap.api.RmapSerializable;

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

    public void encode(RmapByteWriter out, Object value) {
        encode(out, value, new ClassInterner(), new RefTable(), 0);
    }

    public Object decode(RmapByteReader in, Set<String> whitelist) {
        return decode(in, whitelist, new ClassInterner(), new RefTable(), 0);
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
        // объекты (задача 4)
        if (c.isAnnotationPresent(RmapSerializable.class)) {
            int existing = refs.writeIndexOrRegister(value);
            if (existing >= 0) {
                out.writeByte(Tags.BACK_REF);
                out.writeInt(existing);
                return;
            }
            checkDepth(depth);
            out.writeByte(Tags.OBJECT);
            interner.writeClassRef(out, c);
            for (Field f : ClassSchema.of(c)) {
                encode(out, UnsafeAllocator.getField(value, f), interner, refs, depth + 1);
            }
            return;
        }
        throw new RmapCodecException("no encoding for " + c.getName() + " (коллекции — задача 5, ValueCodec — задача 6)");
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
        Object instance = UnsafeAllocator.allocate(c);
        refs.readRegister(instance); // pre-order: до чтения полей (self-reference)
        for (Field f : ClassSchema.of(c)) {
            Object fieldValue = decode(in, whitelist, interner, refs, depth + 1);
            UnsafeAllocator.putField(instance, f, fieldValue);
        }
        return instance;
    }

    private static void checkDepth(int depth) {
        if (depth >= MAX_DEPTH) {
            throw new RmapCodecException("max decode depth exceeded: " + depth);
        }
    }
}
