package me.moonways.rmap.wire;

/** Коды кадра OTHER (спека §7.3). */
public final class OtherCode {

    public static final int VERSION_MISMATCH = 1;
    public static final int APP_VERSION_MISMATCH = 2;
    public static final int CODEC_MISMATCH = 3;
    public static final int ACCESS_DENIED = 4;
    public static final int PROTOCOL_ERROR = 5;
    public static final int SUBJECT_UNDEFINED = 6;
    public static final int DIGEST_MISMATCH = 7;
    public static final int INVALID_SIGNATURE = 8;
    public static final int TIMED_OUT = 9;
    public static final int INTERNAL_ERROR = 10;
    public static final int STALE_REF = 11;
    public static final int FRAME_TOO_LARGE = 12;
    public static final int CODEC_ERROR = 13;
    public static final int BACKPRESSURE = 14;

    private OtherCode() {
    }

    /** Символьное имя кода OTHER для сообщений об ошибках handshake (§4.2a). */
    public static String name(int code) {
        switch (code) {
            case VERSION_MISMATCH: return "VERSION_MISMATCH";
            case APP_VERSION_MISMATCH: return "APP_VERSION_MISMATCH";
            case CODEC_MISMATCH: return "CODEC_MISMATCH";
            case ACCESS_DENIED: return "ACCESS_DENIED";
            case PROTOCOL_ERROR: return "PROTOCOL_ERROR";
            case SUBJECT_UNDEFINED: return "SUBJECT_UNDEFINED";
            case DIGEST_MISMATCH: return "DIGEST_MISMATCH";
            case INVALID_SIGNATURE: return "INVALID_SIGNATURE";
            case TIMED_OUT: return "TIMED_OUT";
            case INTERNAL_ERROR: return "INTERNAL_ERROR";
            case STALE_REF: return "STALE_REF";
            case FRAME_TOO_LARGE: return "FRAME_TOO_LARGE";
            case CODEC_ERROR: return "CODEC_ERROR";
            case BACKPRESSURE: return "BACKPRESSURE";
            default: return "OTHER(" + code + ")";
        }
    }
}
