package me.moonways.rmap.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Минор финревью B: {@link RmapLogging#get(String)} возвращает ДЕЛЕГИРУЮЩИЙ логгер, перечитывающий
 * фабрику на каждый вызов. Иначе {@code static final LOG}, захваченный на class-load, игнорировал бы
 * {@link RmapLogging#setFactory}, вызванный позже. Делегат к тому же гардит бэкенд от Throwable.
 */
class RmapLoggingTest {

    @Test
    void set_factory_after_get_is_honored_and_backend_throw_is_guarded() {
        // логгер получен ДО setFactory (симулирует захват static final LOG на class-load).
        RmapLogger log = RmapLogging.get("test.Logger");
        List<String> captured = new CopyOnWriteArrayList<>();
        try {
            RmapLogging.setFactory(name -> new RmapLogger() {
                @Override public void debug(String m) { captured.add("D:" + m); }
                @Override public void info(String m) { captured.add("I:" + m); }
                @Override public void warn(String m, Throwable t) { captured.add("W:" + m); }
                @Override public void error(String m, Throwable t) { captured.add("E:" + m); }
            });
            // фабрика установлена ПОСЛЕ get() — лог всё равно доходит (перечитывание, не снимок).
            log.info("hello");
            assertThat(captured).contains("I:hello");

            // бросок бэкенда на горячем пути гардится — не пробивается наружу.
            RmapLogging.setFactory(name -> new RmapLogger() {
                @Override public void debug(String m) { throw new RuntimeException("boom"); }
                @Override public void info(String m) { throw new RuntimeException("boom"); }
                @Override public void warn(String m, Throwable t) { throw new RuntimeException("boom"); }
                @Override public void error(String m, Throwable t) { throw new RuntimeException("boom"); }
            });
            assertThatCode(() -> {
                log.debug("x");
                log.info("x");
                log.warn("x", null);
                log.error("x", null);
            }).doesNotThrowAnyException();
        } finally {
            RmapLogging.setFactory(null); // сброс на no-op — состояние не течёт в другие тесты
        }
    }
}
