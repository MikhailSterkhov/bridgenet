package me.moonways.rmap.transport;

import me.moonways.rmap.wire.Frame;

/**
 * Коллбеки транспорта. Все вызовы — на worker-потоке, НЕ на selector (спека §9).
 *
 * <p><b>Контракт конкурентности (спека §9).</b> Транспорт диспатчит каждый входящий кадр
 * отдельной worker-задачей, поэтому:
 * <ul>
 *   <li>{@link #onFrame} может вызываться <b>конкурентно</b> для одного и того же соединения —
 *       несколько кадров одного connection обрабатываются параллельно на разных worker-потоках;</li>
 *   <li><b>порядок кадров НЕ гарантируется</b>: кадр, пришедший по сети позже, может быть обработан
 *       раньше (per-connection ordering отсутствует);</li>
 *   <li>{@link #onClosed} может <b>гоняться</b> с ещё выполняющимися (in-flight) {@code onFrame}
 *       того же соединения;</li>
 *   <li>{@link #onClosed} для одного соединения вызывается <b>ровно один раз</b>.</li>
 * </ul>
 * Реализация listener'а <b>обязана быть thread-safe и синхронизироваться сама</b> (например,
 * строгая state-машина хендшейка под собственным замком). Транспорт синхронизацию не предоставляет.
 */
public interface ConnectionListener {
    void onOpened(RmapConnection connection);
    void onFrame(RmapConnection connection, Frame frame);
    void onClosed(RmapConnection connection, Throwable cause);
}
