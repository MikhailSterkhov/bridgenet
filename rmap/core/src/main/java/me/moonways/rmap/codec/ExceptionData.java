package me.moonways.rmap.codec;

import lombok.Value;

/** Данные EXCEPTION-тега 0x15 (§5.2, §7.3): исключение как ДАННЫЕ, Throwable не инстанцируется. */
@Value
public class ExceptionData {

    @Value
    public static class StackFrame {
        String declaringClass;
        String methodName;
        String fileName;
        int lineNumber;
    }

    String className;
    String message;      // null, если у оригинала не было message
    StackFrame[] frames;
    ExceptionData cause; // null — конец цепочки
}
