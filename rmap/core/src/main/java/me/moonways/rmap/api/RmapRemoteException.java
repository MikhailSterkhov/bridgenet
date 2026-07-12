package me.moonways.rmap.api;

import me.moonways.rmap.codec.ExceptionData;

import java.util.Arrays;

/**
 * Удалённое исполнение упало (§7.3): сервер вернул {@code OTHER(INTERNAL_ERROR)} с {@code EXCEPTION}-TLV.
 * Оригинальный {@link Throwable} НЕ инстанцируется (никакой десериализации Throwable — только данные):
 * message = {@code "<origClass>: <origMessage>"}, а стектрейс склеен из синтетических remote-кадров,
 * разделителя {@code rmap.remote.boundary} и локального хвоста вызывающего. Полная цепочка причин
 * доступна как данные через {@link #getCauseData()}. Unchecked.
 */
public final class RmapRemoteException extends RuntimeException {

    /** Разделитель remote-стека от локального хвоста. */
    private static final StackTraceElement BOUNDARY =
            new StackTraceElement("rmap.remote", "boundary", null, -1);

    private final transient ExceptionData causeData;
    /** Синтетические remote-кадры (включая {@link #BOUNDARY}); локальный хвост добавляется поверх. */
    private final StackTraceElement[] remoteFrames;

    /** Из {@link ExceptionData}-цепочки (§7.3): message + синтетический remote-стек. */
    public RmapRemoteException(ExceptionData data) {
        super(buildMessage(data));
        this.causeData = data;
        this.remoteFrames = buildRemoteFrames(data);
        setStackTrace(concat(remoteFrames, super.getStackTrace()));
    }

    /** Прочий код OTHER без {@code EXCEPTION}-TLV (маппинг "code=ИМЯ: message", §7.3). */
    public RmapRemoteException(String message) {
        super(message);
        this.causeData = null;
        this.remoteFrames = new StackTraceElement[0];
    }

    /** Данные оригинального исключения (класс/message/стек/cause-цепочка), НЕ живой {@link Throwable}. */
    public ExceptionData getCauseData() {
        return causeData;
    }

    /**
     * Пришить локальный хвост стека вызывающего поверх remote-кадров (§7.3). Вызывается на
     * синхронном пути перед пробросом — чтобы стек нёс и remote-кадры сервера, и точку вызова клиента.
     */
    public RmapRemoteException relocalize() {
        if (remoteFrames.length == 0) {
            return this;
        }
        StackTraceElement[] here = Thread.currentThread().getStackTrace();
        // отрезаем верхушку: Thread.getStackTrace + сам relocalize (+ возможный кадр вызывающего снятия).
        int skip = Math.min(2, here.length);
        setStackTrace(concat(remoteFrames, Arrays.copyOfRange(here, skip, here.length)));
        return this;
    }

    private static String buildMessage(ExceptionData data) {
        String cls = data.getClassName();
        String msg = data.getMessage();
        return msg == null ? cls : cls + ": " + msg;
    }

    private static StackTraceElement[] buildRemoteFrames(ExceptionData data) {
        ExceptionData.StackFrame[] frames = data.getFrames();
        StackTraceElement[] out = new StackTraceElement[frames.length + 1];
        for (int i = 0; i < frames.length; i++) {
            ExceptionData.StackFrame f = frames[i];
            String file = (f.getFileName() == null || f.getFileName().isEmpty()) ? null : f.getFileName();
            out[i] = new StackTraceElement(f.getDeclaringClass(), f.getMethodName(), file, f.getLineNumber());
        }
        out[frames.length] = BOUNDARY;
        return out;
    }

    private static StackTraceElement[] concat(StackTraceElement[] a, StackTraceElement[] b) {
        StackTraceElement[] out = new StackTraceElement[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
