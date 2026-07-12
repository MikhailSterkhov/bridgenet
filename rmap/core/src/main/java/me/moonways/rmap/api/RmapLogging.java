package me.moonways.rmap.api;

import java.util.function.Function;

/**
 * Фабрика {@link RmapLogger} (спека §11). Дефолт — no-op логгер (библиотека молчит, пока
 * приложение явно не подключит бэкенд через {@link #setFactory}). Потокобезопасно (volatile).
 *
 * <p>{@link #get(String)} возвращает ДЕЛЕГИРУЮЩИЙ логгер, перечитывающий текущую {@link #factory} при
 * КАЖДОМ вызове (не снимок на class-load). Иначе {@code static final LOG = RmapLogging.get(...)}
 * захватывал бы фабрику в момент загрузки класса-владельца, и {@link #setFactory}, вызванный ПОЗЖЕ
 * загрузки, был бы тихим no-op навсегда. Делегат к тому же ГАРДИТ бэкенд: бросок из пользовательского
 * логгера на горячем пути (напр. перед отправкой OTHER) не должен подавлять протокольный ответ.
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
        return new DelegatingLogger(name);
    }

    /** Перечитывает {@link #factory} на каждый вызов (init-order) и гардит бэкенд от Throwable. */
    private static final class DelegatingLogger implements RmapLogger {
        private final String name;

        DelegatingLogger(String name) {
            this.name = name;
        }

        private RmapLogger backend() {
            RmapLogger l = factory.apply(name);
            return l != null ? l : NoOpLogger.INSTANCE;
        }

        @Override public void debug(String msg) {
            try { backend().debug(msg); } catch (Throwable ignored) { }
        }
        @Override public void info(String msg) {
            try { backend().info(msg); } catch (Throwable ignored) { }
        }
        @Override public void warn(String msg, Throwable t) {
            try { backend().warn(msg, t); } catch (Throwable ignored) { }
        }
        @Override public void error(String msg, Throwable t) {
            try { backend().error(msg, t); } catch (Throwable ignored) { }
        }
    }

    private enum NoOpLogger implements RmapLogger {
        INSTANCE;

        @Override public void debug(String msg) { }
        @Override public void info(String msg) { }
        @Override public void warn(String msg, Throwable t) { }
        @Override public void error(String msg, Throwable t) { }
    }
}
