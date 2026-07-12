package me.moonways.rmap.rpc;

import java.time.Duration;

/**
 * Per-call переопределения (§7.1). Пока — только {@code deadline}: применяется через
 * {@code RmapClient.withOptions(proxy, opts)}, который возвращает view-прокси с этим deadline
 * (тот же путь/интерфейс, другой дефолт таймаута). Иммутабелен.
 */
public final class RmapCallOptions {

    private final long deadlineMillis;

    private RmapCallOptions(long deadlineMillis) {
        this.deadlineMillis = deadlineMillis;
    }

    /** Deadline вызова. {@code d} клампится в неотрицательный диапазон; верхний кламп — на wire-слое. */
    public static RmapCallOptions deadline(Duration d) {
        long millis = d == null ? 0L : d.toMillis();
        return new RmapCallOptions(Math.max(0L, millis));
    }

    public long getDeadlineMillis() {
        return deadlineMillis;
    }
}
