package me.moonways.rmap.transport;

import me.moonways.rmap.wire.Frame;

/** Коллбеки транспорта. Все вызовы — на worker-потоке, НЕ на selector (спека §9). */
public interface ConnectionListener {
    void onOpened(RmapConnection connection);
    void onFrame(RmapConnection connection, Frame frame);
    void onClosed(RmapConnection connection, Throwable cause);
}
