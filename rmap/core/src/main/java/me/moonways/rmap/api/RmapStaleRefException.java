package me.moonways.rmap.api;

/** Вызов по умершему remote-ref (§10): сервер вернул {@code OTHER(STALE_REF)} — ref истёк по lease
 *  либо таблица очищена разрывом (refs не переживают reconnect). Unchecked. */
public final class RmapStaleRefException extends RuntimeException {

    public RmapStaleRefException(String message) {
        super(message);
    }
}
