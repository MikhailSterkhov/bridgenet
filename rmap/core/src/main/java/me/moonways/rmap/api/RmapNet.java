package me.moonways.rmap.api;

import me.moonways.rmap.transport.RmapConfig;

/** Точка входа публичного транспортного API RMAP (спека §11). */
public final class RmapNet {

    private RmapNet() {
    }

    public static RmapNet create() {
        return new RmapNet();
    }

    public RmapServer newServer(RmapConfig config) {
        return new RmapServer(config);
    }

    public RmapClient newClient(RmapConfig config) {
        return new RmapClient(config);
    }
}
