package me.moonways.rmap.api;

import java.util.function.Function;

/**
 * Фабрика {@link RmapLogger} (спека §11). Дефолт — no-op логгер (библиотека молчит, пока
 * приложение явно не подключит бэкенд через {@link #setFactory}). Потокобезопасно (volatile).
 */
public final class RmapLogging {

    private static volatile Function<String, RmapLogger> factory = name -> NoOpLogger.INSTANCE;

    private RmapLogging() {
    }

    /** Подключить фабрику логгеров по имени (обычно FQN класса-владельца). {@code null} → сброс на no-op. */
    public static void setFactory(Function<String, RmapLogger> f) {
        factory = f != null ? f : (name -> NoOpLogger.INSTANCE);
    }

    public static RmapLogger get(String name) {
        RmapLogger logger = factory.apply(name);
        return logger != null ? logger : NoOpLogger.INSTANCE;
    }

    private enum NoOpLogger implements RmapLogger {
        INSTANCE;

        @Override public void debug(String msg) { }
        @Override public void info(String msg) { }
        @Override public void warn(String msg, Throwable t) { }
        @Override public void error(String msg, Throwable t) { }
    }
}
