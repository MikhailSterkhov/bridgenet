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

    /**
     * Оборачивает пользовательские метрики гардом: бросок из любого счётчика на ГОРЯЧЕМ пути не должен
     * ломать протокол. Особо опасен {@code frameOut}: он вызывается ПОСЛЕ {@code conn.send} внутри
     * {@code encodeAndSend}; необёрнутый бросок пробился бы как «сбой encode» → отправитель ответил бы
     * ВТОРЫМ OTHER на уже отправленный кадр (спурьёзный двойной ответ). {@link #NO_OP} не оборачиваем.
     */
    static RmapMetrics guarded(RmapMetrics delegate) {
        if (delegate == null || delegate == NO_OP || delegate instanceof Guarded) {
            return delegate == null ? NO_OP : delegate;
        }
        return new Guarded(delegate);
    }

    /** Гард-декоратор: делегирует счётчик, глотает Throwable бэкенда (§9/§11). */
    final class Guarded implements RmapMetrics {
        private final RmapMetrics d;

        Guarded(RmapMetrics d) {
            this.d = d;
        }

        @Override public void frameIn(int bytes) { try { d.frameIn(bytes); } catch (Throwable ignored) { } }
        @Override public void frameOut(int bytes) { try { d.frameOut(bytes); } catch (Throwable ignored) { } }
        @Override public void callStarted() { try { d.callStarted(); } catch (Throwable ignored) { } }
        @Override public void callCompleted(boolean success) { try { d.callCompleted(success); } catch (Throwable ignored) { } }
        @Override public void lateAnswerDropped() { try { d.lateAnswerDropped(); } catch (Throwable ignored) { } }
        @Override public void refReleased(int count) { try { d.refReleased(count); } catch (Throwable ignored) { } }
        @Override public void staleRefHit() { try { d.staleRefHit(); } catch (Throwable ignored) { } }
    }
}
