package me.moonways.rmap.codec;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Детерминированный порядок полей класса для wire (спека §5.1): поля иерархии
 *  Object→…→класс, в каждом классе — по имени. static/transient пропускаются. */
public final class ClassSchema {

    private static final ConcurrentHashMap<Class<?>, List<Field>> CACHE = new ConcurrentHashMap<>();

    private ClassSchema() {
    }

    public static List<Field> of(Class<?> type) {
        return CACHE.computeIfAbsent(type, ClassSchema::compute);
    }

    private static List<Field> compute(Class<?> type) {
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            chain.add(c);
        }
        // от корня к листу
        List<Field> out = new ArrayList<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            Field[] declared = chain.get(i).getDeclaredFields();
            List<Field> level = new ArrayList<>();
            for (Field f : declared) {
                int m = f.getModifiers();
                if (Modifier.isStatic(m) || Modifier.isTransient(m)) {
                    continue;
                }
                f.setAccessible(true);
                level.add(f);
            }
            level.sort(Comparator.comparing(Field::getName));
            out.addAll(level);
        }
        return Collections.unmodifiableList(out);
    }
}
