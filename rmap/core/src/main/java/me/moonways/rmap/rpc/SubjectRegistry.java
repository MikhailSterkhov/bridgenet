package me.moonways.rmap.rpc;

import lombok.Value;
import me.moonways.rmap.api.RmapExportException;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр экспортированных subject'ов сервера (§7.2). {@code subjectId} — счётчик с 0;
 * резолв — map-lookup (НЕ индекс в массив, §7.2), поэтому «дыры» невозможны и подделанный
 * subjectId не адресует чужую память. Повторный экспорт того же path → {@link RmapExportException}.
 */
public final class SubjectRegistry {

    @Value
    public static class Subject {
        int id;
        String path;
        Class<?> iface;
        Object impl;
        InterfaceManifest manifest;
        ExportOptions opts;
    }

    private final Map<String, Subject> byPath = new ConcurrentHashMap<>();
    private final Map<Integer, Subject> byId = new ConcurrentHashMap<>();
    private int nextId; // 0, 1, 2 … — доступ под монитором register()

    /** Регистрирует subject; возвращает выданный {@code subjectId ≥ 0}. Повторный path → отказ. */
    public synchronized int register(String path, Class<?> iface, Object impl,
                                     InterfaceManifest manifest, ExportOptions opts) {
        if (byPath.containsKey(path)) {
            throw new RmapExportException("duplicate export path: " + path);
        }
        int id = nextId++;
        Subject subject = new Subject(id, path, iface, impl, manifest, opts);
        // impl может быть не-public классом — Method из интерфейса требует доступа при invoke.
        for (Method method : manifest.getMethodsById().values()) {
            try {
                method.setAccessible(true);
            } catch (RuntimeException ignored) {
                // SecurityManager запретил — invoke сам бросит IllegalAccessException → INTERNAL_ERROR
            }
        }
        byPath.put(path, subject);
        byId.put(id, subject);
        return id;
    }

    public Subject byPath(String path) {
        return byPath.get(path);
    }

    public Subject byId(int id) {
        return byId.get(id);
    }

    public Collection<Subject> all() {
        return byPath.values();
    }
}
