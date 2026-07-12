package me.moonways.rmap.transport;

import me.moonways.rmap.wire.Frame;

/**
 * Коллбеки транспорта. Все вызовы — на worker-потоке, НЕ на selector (спека §9).
 *
 * <p><b>Контракт конкурентности (спека §9, §5.2a).</b> Кадры ОДНОГО соединения транспорт
 * доставляет в per-connection очередь строго в wire-порядке (selector-поток) и дренирует одним
 * in-flight worker'ом, поэтому:
 * <ul>
 *   <li>{@link #onFrame} одного соединения вызывается <b>строго последовательно и в wire-порядке</b>
 *       (следующий кадр не начнёт обрабатываться, пока не вернулся {@code onFrame} предыдущего) —
 *       это даёт wire-порядок class-интернирования для serial-decode (§5.2a). Между <b>разными</b>
 *       соединениями {@code onFrame} по-прежнему конкурентен (общий worker-pool);</li>
 *   <li>{@link #onClosed} может <b>гоняться</b> с ещё выполняющимся (in-flight) {@code onFrame}
 *       того же соединения;</li>
 *   <li>{@link #onClosed} для одного соединения вызывается <b>ровно один раз</b>.</li>
 * </ul>
 * Реализация listener'а <b>обязана быть thread-safe и синхронизироваться сама</b> (один инстанс
 * обслуживает все соединения; плюс onClosed гоняется с onFrame). Транспорт синхронизацию не
 * предоставляет.
 */
public interface ConnectionListener {
    void onOpened(RmapConnection connection);
    void onFrame(RmapConnection connection, Frame frame);
    void onClosed(RmapConnection connection, Throwable cause);
}
