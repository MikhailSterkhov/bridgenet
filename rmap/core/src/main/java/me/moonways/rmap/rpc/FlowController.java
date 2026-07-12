package me.moonways.rmap.rpc;

/**
 * Per-connection input flow-control (§9): считает in-flight вызовы и решает, снять/вернуть интерес
 * {@code OP_READ} соединения.
 *
 * <p><b>Финревью-фикс A(C1).</b> Инкремент in-flight + решение о {@code pauseReads} и декремент +
 * решение о {@code resumeReads} выполняются под ОДНИМ монитором. Сигналы pause/resume — это FIFO-задачи
 * селектора: их порядок ПОСТАНОВКИ обязан соответствовать порядку изменения счётчика. Без общей
 * синхронизации возможен interleaving «pause постановлен ПОСЛЕ resume» (воркер инкрементит до max и
 * вытесняется до pauseReads; параллельное завершение декрементит max→max-1 и ставит resumeReads; воркер
 * ставит pauseReads после resume) → {@code OP_READ} снят при {@code inFlight<max}, а resume-триггер
 * ({@code ==max-1}) больше не наступит → соединение глохнет навсегда (до idle-sweep). Лок исключает это.
 *
 * <p>Лок НЕ охватывает исполнение вызова (invoke) — только пару «счётчик + постановка selector-задачи»,
 * поэтому конкурентность инвокаций не сериализуется.
 */
final class FlowController {

    /** Сигнал транспорту: снять/вернуть интерес {@code OP_READ} (идемпотентно; no-op на закрытом). */
    interface Signal {
        void pauseReads();

        void resumeReads();
    }

    private final int max;
    private final Signal signal;
    private final Object lock = new Object();
    private int inFlight;

    FlowController(int max, Signal signal) {
        this.max = max;
        this.signal = signal;
    }

    /** Постановка вызова: инкремент in-flight; достигнут лимит → снять {@code OP_READ}. */
    void onEnqueue() {
        synchronized (lock) {
            if (++inFlight >= max) {
                signal.pauseReads();
            }
        }
    }

    /** Завершение вызова: декремент in-flight; упали ниже лимита → вернуть {@code OP_READ}. */
    void onFinish() {
        synchronized (lock) {
            if (--inFlight == max - 1) {
                signal.resumeReads();
            }
        }
    }

    /** Текущее число in-flight (интроспекция для тестов/метрик). */
    int inFlight() {
        synchronized (lock) {
            return inFlight;
        }
    }
}
