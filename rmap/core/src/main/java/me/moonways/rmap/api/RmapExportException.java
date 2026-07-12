package me.moonways.rmap.api;

/** Export-time audit (§8) нашёл непригодные для v1 не-исключённые методы интерфейса.
 *  Message перечисляет все проблемы «метод → причина», по одной на строку —
 *  {@code server.start()} не стартует до устранения. */
public class RmapExportException extends RuntimeException {

    public RmapExportException(String message) {
        super(message);
    }
}
