package me.moonways.rmap.api;

/**
 * Логирование RMAP (спека §11): библиотека сама лог-фреймворк не тянет (zero-dep), поэтому
 * логи идут через этот SPI. Дефолт — no-op ({@link RmapLogging}); адаптер приложения подключает
 * реальный бэкенд через {@link RmapLogging#setFactory(java.util.function.Function)}.
 *
 * <p>Сообщения — на английском (лог-конвенция, независимая от языка живого общения).
 */
public interface RmapLogger {

    void debug(String msg);

    void info(String msg);

    /** {@code t} может быть {@code null} (предупреждение без исключения). */
    void warn(String msg, Throwable t);

    /** {@code t} может быть {@code null} (ошибка без исключения). */
    void error(String msg, Throwable t);
}
