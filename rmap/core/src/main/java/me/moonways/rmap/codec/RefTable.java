package me.moonways.rmap.codec;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Identity-map для write-стороны и таблица прочитанных объектов для read-стороны
 *  (спека §5.1, pre-order регистрация). Отдельный инстанс на проход. */
public final class RefTable {

    // write-сторона: объект → его индекс (pre-order)
    private final Map<Object, Integer> writeIndex = new IdentityHashMap<>();
    private int nextWrite = 0;

    // read-сторона
    private final List<Object> readObjects = new ArrayList<>();

    /** Если объект уже писался — вернуть его индекс (писать BACK_REF); иначе
     *  зарегистрировать под следующим индексом и вернуть -1 (писать полноценно). */
    public int writeIndexOrRegister(Object o) {
        Integer existing = writeIndex.get(o);
        if (existing != null) {
            return existing;
        }
        writeIndex.put(o, nextWrite++);
        return -1;
    }

    /** read-сторона: зарегистрировать создаваемый объект ДО чтения полей (pre-order). */
    public void readRegister(Object o) {
        readObjects.add(o);
    }

    public Object readGet(int index) {
        if (index < 0 || index >= readObjects.size()) {
            throw new RmapCodecException("back-ref index out of range: " + index);
        }
        return readObjects.get(index);
    }
}
