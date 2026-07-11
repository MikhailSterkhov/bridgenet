package me.moonways.rmap.codec;

/** Ошибка кодирования/декодирования — протокольный CODEC_ERROR (спека §7.3). Unchecked. */
public class RmapCodecException extends RuntimeException {

    public RmapCodecException(String message) {
        super(message);
    }

    public RmapCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
