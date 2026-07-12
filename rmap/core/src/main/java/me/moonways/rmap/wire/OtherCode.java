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
}
