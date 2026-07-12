package me.moonways.rmap.api;

/** Соединение оборвано/не аутентифицировано: pending-вызовы fail-fast (§7.2), а вызовы на
 *  уже закрытом клиенте не отправляются. Unchecked. */
public final class RmapConnectionException extends RuntimeException {

    public RmapConnectionException(String message) {
        super(message);
    }

    public RmapConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
