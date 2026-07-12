package me.moonways.rmap.wire;

import me.moonways.rmap.codec.RmapCodecException;

/** Коды кадров RMAP (спека §4.2). */
public enum FrameType {

    HELLO(0x01), HELLO_ACK(0x02), AUTH_RESPONSE(0x03), AUTH_OK(0x04),
    LOOKUP(0x05), LOOKUP_ACK(0x06),
    RGET(0x10), DONE(0x11), OTHER(0x12), CANCEL(0x13),
    PING(0x20), PONG(0x21),
    REF_RELEASE(0x30);

    private final int code;

    FrameType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static FrameType byCode(int code) {
        for (FrameType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new RmapCodecException("unknown frame type 0x" + Integer.toHexString(code));
    }
}
