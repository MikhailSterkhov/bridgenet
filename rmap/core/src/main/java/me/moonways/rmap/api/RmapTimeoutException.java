package me.moonways.rmap.api;

/** Вызов не завершился в отведённый deadline (§7.1): клиентский таймер сработал и (best-effort)
 *  отправил {@code CANCEL}, либо сервер вернул {@code OTHER(TIMED_OUT)}. Unchecked. */
public final class RmapTimeoutException extends RuntimeException {

    public RmapTimeoutException(String message) {
        super(message);
    }
}
