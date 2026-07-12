package me.moonways.rmap.api;

/**
 * Метрики RMAP (спека §9, §11): счётчики кадров/байтов/вызовов/поздних ответов/remote-ref
 * событий. Дефолт — {@link #NO_OP}; сеттер — {@link RmapNet#metrics(RmapMetrics)}.
 */
public interface RmapMetrics {

    /** Входящий кадр (payload-байты, без заголовка). */
    void frameIn(int bytes);

    /** Исходящий кадр (payload-байты, без заголовка). */
    void frameOut(int bytes);

    /** Вызов поставлен на исполнение (§9). */
    void callStarted();

    /** Вызов завершён (успех/прикладной или протокольный отказ). */
    void callCompleted(boolean success);

    /** Ответ (DONE/OTHER/LOOKUP_ACK) на неизвестный/уже завершённый callId — молча отброшен (§7.2). */
    void lateAnswerDropped();

    /** {@code REF_RELEASE} обработан — количество refId в батче (§10). */
    void refReleased(int count);

    /** Вызов адресован протухшему по lease remote-ref'у (§10). */
    void staleRefHit();

    RmapMetrics NO_OP = new RmapMetrics() {
        @Override public void frameIn(int bytes) { }
        @Override public void frameOut(int bytes) { }
        @Override public void callStarted() { }
        @Override public void callCompleted(boolean success) { }
        @Override public void lateAnswerDropped() { }
        @Override public void refReleased(int count) { }
        @Override public void staleRefHit() { }
    };
}
