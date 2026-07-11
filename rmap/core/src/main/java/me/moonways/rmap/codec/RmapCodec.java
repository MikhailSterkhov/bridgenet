package me.moonways.rmap.codec;

import java.util.UUID;

/**
 * Движок TLV-кодека (спека §5). Задача 3 — скаляры; составные типы, интернирование
 * классов и identity-map добавляются в задачах 4–6. Один инстанс на encode- или
 * decode-проход одного сообщения (держит счётчик глубины, позже — таблицы ссылок).
 */
public final class RmapCodec {

    static final int MAX_DEPTH = 32; // спека §5.1

    public void encode(RmapByteWriter out, Object value) {
        if (value == null) {
            out.writeByte(Tags.NULL);
            return;
        }
        Class<?> c = value.getClass();
        if (c == Boolean.class) {
            out.writeByte(((Boolean) value) ? Tags.TRUE : Tags.FALSE);
        } else if (c == Byte.class) {
            out.writeByte(Tags.BYTE);
            out.writeByte((Byte) value);
        } else if (c == Short.class) {
            out.writeByte(Tags.SHORT);
            out.writeShort((Short) value);
        } else if (c == Integer.class) {
            out.writeByte(Tags.INT);
            out.writeInt((Integer) value);
        } else if (c == Long.class) {
            out.writeByte(Tags.LONG);
            out.writeLong((Long) value);
        } else if (c == Float.class) {
            out.writeByte(Tags.FLOAT);
            out.writeFloat((Float) value);
        } else if (c == Double.class) {
            out.writeByte(Tags.DOUBLE);
            out.writeDouble((Double) value);
        } else if (c == Character.class) {
            out.writeByte(Tags.CHAR);
            out.writeChar((Character) value);
        } else if (c == String.class) {
            out.writeByte(Tags.STRING);
            out.writeStr((String) value);
        } else if (c == UUID.class) {
            out.writeByte(Tags.UUID);
            out.writeLong(((UUID) value).getMostSignificantBits());
            out.writeLong(((UUID) value).getLeastSignificantBits());
        } else if (c == byte[].class) {
            byte[] data = (byte[]) value;
            out.writeByte(Tags.BYTES);
            out.writeInt(data.length);
            out.writeRaw(data);
        } else if (Enum.class.isAssignableFrom(c)) {
            out.writeByte(Tags.ENUM);
            out.writeStr(enumClassName(c));
            out.writeStr(((Enum<?>) value).name());
        } else {
            throw new RmapCodecException("no scalar encoding for " + c.getName()
                    + " (составные типы — задачи 4-6)");
        }
    }

    /** Для анонимных enum-констант (SomeEnum$1) берём объявляющий enum-класс. */
    private static String enumClassName(Class<?> c) {
        return c.isEnum() ? c.getName() : c.getSuperclass().getName();
    }

    public Object decode(RmapByteReader in) {
        int tag = in.readUnsignedByte();
        switch (tag) {
            case Tags.NULL:
                return null;
            case Tags.TRUE:
                return Boolean.TRUE;
            case Tags.FALSE:
                return Boolean.FALSE;
            case Tags.BYTE:
                return (byte) in.readUnsignedByte();
            case Tags.SHORT:
                return (short) in.readShort();
            case Tags.INT:
                return in.readInt();
            case Tags.LONG:
                return in.readLong();
            case Tags.FLOAT:
                return in.readFloat();
            case Tags.DOUBLE:
                return in.readDouble();
            case Tags.CHAR:
                return in.readChar();
            case Tags.STRING:
                return in.readStr();
            case Tags.UUID:
                return new UUID(in.readLong(), in.readLong());
            case Tags.BYTES:
                return in.readRaw(in.readInt());
            case Tags.ENUM:
                return decodeEnum(in);
            default:
                throw new RmapCodecException("unknown or not-yet-supported tag 0x"
                        + Integer.toHexString(tag));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object decodeEnum(RmapByteReader in) {
        String className = in.readStr();
        String name = in.readStr();
        try {
            Class<?> enumClass = Class.forName(className);
            return Enum.valueOf((Class<Enum>) enumClass.asSubclass(Enum.class), name);
        } catch (ClassNotFoundException | IllegalArgumentException | ClassCastException e) {
            throw new RmapCodecException("bad enum " + className + "#" + name, e);
        }
    }
}
