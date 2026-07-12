package me.moonways.rmap.codec;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/** Единственная точка контакта с sun.misc.Unsafe (спека §9): инстанцирование без
 *  конструктора + запись полей (в т.ч. final) по offset. */
public final class UnsafeAllocator {

    private static final Unsafe UNSAFE;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private UnsafeAllocator() {
    }

    public static Object allocate(Class<?> type) {
        try {
            return UNSAFE.allocateInstance(type);
        } catch (InstantiationException e) {
            throw new RmapCodecException("cannot allocate " + type.getName(), e);
        }
    }

    public static Object getField(Object owner, Field field) {
        long off = UNSAFE.objectFieldOffset(field);
        Class<?> t = field.getType();
        if (t == int.class) return UNSAFE.getInt(owner, off);
        if (t == long.class) return UNSAFE.getLong(owner, off);
        if (t == boolean.class) return UNSAFE.getBoolean(owner, off);
        if (t == byte.class) return UNSAFE.getByte(owner, off);
        if (t == short.class) return UNSAFE.getShort(owner, off);
        if (t == char.class) return UNSAFE.getChar(owner, off);
        if (t == float.class) return UNSAFE.getFloat(owner, off);
        if (t == double.class) return UNSAFE.getDouble(owner, off);
        return UNSAFE.getObject(owner, off);
    }

    public static void putField(Object owner, Field field, Object value) {
        long off = UNSAFE.objectFieldOffset(field);
        Class<?> t = field.getType();
        if (t.isPrimitive()) {
            if (value == null) {
                throw new RmapCodecException("null value for primitive field " + field.getName());
            }
            try {
                if (t == int.class) UNSAFE.putInt(owner, off, ((Number) value).intValue());
                else if (t == long.class) UNSAFE.putLong(owner, off, ((Number) value).longValue());
                else if (t == boolean.class) UNSAFE.putBoolean(owner, off, (Boolean) value);
                else if (t == byte.class) UNSAFE.putByte(owner, off, ((Number) value).byteValue());
                else if (t == short.class) UNSAFE.putShort(owner, off, ((Number) value).shortValue());
                else if (t == char.class) UNSAFE.putChar(owner, off, (Character) value);
                else if (t == float.class) UNSAFE.putFloat(owner, off, ((Number) value).floatValue());
                else if (t == double.class) UNSAFE.putDouble(owner, off, ((Number) value).doubleValue());
            } catch (ClassCastException e) {
                throw new RmapCodecException("type mismatch for primitive field " + field.getName()
                        + ": got " + value.getClass().getName(), e);
            }
        } else {
            if (value != null && !t.isInstance(value)) {
                throw new RmapCodecException("type mismatch for field " + field.getName()
                        + ": expected " + t.getName() + ", got " + value.getClass().getName());
            }
            UNSAFE.putObject(owner, off, value);
        }
    }
}
