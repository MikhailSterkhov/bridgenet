package me.moonways.rmap.codec;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Интернирование classId per-direction (спека §5.2a). Отдельный инстанс на
 *  encode-проход и на decode-проход (в плане A — на сообщение; в плане B — на
 *  соединение, connection-scoped: {@code writeClassRef}/{@code readClassRef}
 *  synchronized — конкурентные encode-вызовы с общим write-interner'ом safe). */
public final class ClassInterner {

    static final int MAX_INTERNED = 4096; // спека §5.1

    private final int maxClasses;

    // write-сторона
    private final Map<Class<?>, Integer> writeIds = new HashMap<>();
    // read-сторона
    private final Map<Integer, Class<?>> readClasses = new HashMap<>();
    private int nextWriteId = 1;
    private int nextReadId = 1;

    public ClassInterner() {
        this(MAX_INTERNED);
    }

    /** maxClasses — лимит definition'ов на direction (спека §11 maxInternedClasses). */
    public ClassInterner(int maxClasses) {
        this.maxClasses = maxClasses;
    }

    /** Текущий рубеж write-стороны (следующий classId). Пара к {@link #writeRollback(int)}:
     *  encode ответа, бросивший исключение ПОСЛЕ записи classRef-def в выброшенный кадр, отравил бы
     *  write-интернер (клиент никогда не увидит def → его CLASSREF_USE не резолвится). */
    public synchronized int writeMark() {
        return nextWriteId;
    }

    /** Откат write-стороны к рубежу {@code mark}: снять все def'ы с classId ≥ mark (их кадр не ушёл). */
    public synchronized void writeRollback(int mark) {
        if (mark >= nextWriteId) {
            return;
        }
        writeIds.values().removeIf(id -> id >= mark);
        nextWriteId = mark;
    }

    public synchronized void writeClassRef(RmapByteWriter out, Class<?> type) {
        Integer id = writeIds.get(type);
        if (id != null) {
            out.writeByte(Tags.CLASSREF_USE);
            out.writeInt(id);
            return;
        }
        if (writeIds.size() >= maxClasses) {
            throw new RmapCodecException("interned classes limit exceeded: " + maxClasses);
        }
        writeIds.put(type, nextWriteId++);
        out.writeByte(Tags.CLASSREF_DEF);
        out.writeStr(type.getName());
    }

    /** whitelist == null → принять любой FQN (тесты/ACCEPT_ALL); иначе FQN обязан быть в наборе. */
    public synchronized Class<?> readClassRef(RmapByteReader in, Set<String> whitelist) {
        int disc = in.readUnsignedByte();
        if (disc == Tags.CLASSREF_USE) {
            Class<?> c = readClasses.get(in.readInt());
            if (c == null) {
                throw new RmapCodecException("classRef use of undefined id");
            }
            return c;
        }
        if (disc != Tags.CLASSREF_DEF) {
            throw new RmapCodecException("bad classRef discriminator 0x" + Integer.toHexString(disc));
        }
        String fqn = in.readStr();
        if (whitelist != null && !whitelist.contains(fqn)) {
            throw new RmapCodecException("class not in whitelist: " + fqn); // ДО Class.forName
        }
        if (readClasses.size() >= maxClasses) {
            throw new RmapCodecException("interned classes limit exceeded: " + maxClasses);
        }
        Class<?> c;
        try {
            c = Class.forName(fqn, false, ClassInterner.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RmapCodecException("class not found: " + fqn, e);
        }
        readClasses.put(nextReadId++, c);
        return c;
    }
}
