package me.moonways.rmap.wire;

import me.moonways.rmap.codec.RmapByteWriter;

/** Кодирование кадра: [int32 len][byte frameType][int64 callId][payload] (спека §4.1). */
public final class FrameCodec {

    /** Байты заголовка ПОСЛЕ поля len: frameType(1) + callId(8). */
    public static final int HEADER_AFTER_LEN = 9;

    private FrameCodec() {
    }

    public static byte[] encode(Frame frame) {
        RmapByteWriter out = new RmapByteWriter();
        out.writeInt(HEADER_AFTER_LEN + frame.getPayload().length);
        out.writeByte(frame.getType().code());
        out.writeLong(frame.getCallId());
        out.writeRaw(frame.getPayload());
        return out.toByteArray();
    }

    /** Собрать кадр из уже разобранного транспортом заголовка (length снят декодером кадрирования). */
    public static Frame decodeBody(int frameTypeCode, long callId, byte[] payload) {
        return new Frame(FrameType.byCode(frameTypeCode), callId, payload);
    }
}
