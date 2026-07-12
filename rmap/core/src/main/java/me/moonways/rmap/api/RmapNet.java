package me.moonways.rmap.api;

import me.moonways.rmap.transport.RmapConfig;

/** Точка входа публичного транспортного API RMAP (спека §11). */
public final class RmapNet {

    private volatile RmapMetrics metrics = RmapMetrics.NO_OP;

    private RmapNet() {
    }

    public static RmapNet create() {
        return new RmapNet();
    }

    /** Метрики (§11, SPI {@link RmapMetrics}) — применяются ко ВСЕМ серверам/клиентам, созданным
     *  ЭТИМ {@code RmapNet} ПОСЛЕ вызова. {@code null} → сброс на no-op. Возвращает {@code this}
     *  для чейнинга. */
    public RmapNet metrics(RmapMetrics metrics) {
        this.metrics = metrics != null ? metrics : RmapMetrics.NO_OP;
        return this;
    }

    public RmapServer newServer(RmapConfig config) {
        return new RmapServer(config, metrics);
    }

    public RmapClient newClient(RmapConfig config) {
        return new RmapClient(config, metrics);
    }
}
