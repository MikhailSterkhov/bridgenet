package me.moonways.rmap.rpc;

import lombok.Value;
import me.moonways.rmap.codec.CodecContext;
import me.moonways.rmap.codec.ExceptionData;
import me.moonways.rmap.codec.RmapByteReader;
import me.moonways.rmap.codec.RmapByteWriter;
import me.moonways.rmap.codec.RmapCodec;
import me.moonways.rmap.codec.RmapCodecException;
import me.moonways.rmap.wire.OtherCode;

import java.util.function.Consumer;

/**
 * Wire-грамматики call-кадров RMAP (§4.2, §7.3, §10.1). Безтеговые поля — через
 * {@link RmapByteWriter}/{@link RmapByteReader}; TLV-части (EXCEPTION в OTHER) — через
 * {@link RmapCodec} с {@link CodecContext} соединения. Утилитный класс — только static.
 *
 * <p>Верхний кламп deadline и структурные проверки заголовка RGET выполняются на decode
 * (§7.2): нарушение, требующее {@code PROTOCOL_ERROR} + close, поднимается как
 * {@link WireProtocolException} с явным {@link OtherCode}; прочий малформ — {@link RmapCodecException}
 * (маппится вызывающим на {@code CODEC_ERROR}).
 */
public final class CallWire {

    /** Верхний кламп deadline входящего RGET (§7.2). */
    public static final int MAX_DEADLINE_MILLIS = 300_000;

    private CallWire() {
    }

    /** Структурное нарушение заголовка call-кадра с конкретным кодом OTHER (§7.2, §10.1). */
    public static final class WireProtocolException extends RmapCodecException {
        private final int otherCode;

        public WireProtocolException(int otherCode, String message) {
            super(message);
            this.otherCode = otherCode;
        }

        public int otherCode() {
            return otherCode;
        }
    }

    // ---- LOOKUP / LOOKUP_ACK (§4.2) ----

    @Value
    public static class Lookup {
        String path;
        long digest;
    }

    public static void encodeLookup(RmapByteWriter out, String path, long interfaceDigest) {
        out.writeStr(path);
        out.writeLong(interfaceDigest);
    }

    public static Lookup decodeLookup(RmapByteReader in) {
        String path = in.readStr();
        long digest = in.readLong();
        return new Lookup(path, digest);
    }

    public static void encodeLookupAck(RmapByteWriter out, int subjectId) {
        out.writeInt(subjectId);
    }

    /** Сырое чтение subjectId. Клиентская валидация «отрицательный → PROTOCOL_ERROR» (§4.2) —
     *  в клиентском прокси (задача 4); здесь возвращается значение как есть. */
    public static int decodeLookupAck(RmapByteReader in) {
        return in.readInt();
    }

    // ---- RGET-заголовок (§10.1) ----

    @Value
    public static class RgetHeader {
        int subjectId;
        long refId;
        long methodId;
        int deadlineMillis;
        int argCount;
    }

    public static void encodeRgetHeader(RmapByteWriter out, int subjectId, long refId,
                                        long methodId, int deadlineMillis, int argCount) {
        out.writeInt(subjectId);
        if (subjectId == -1) {
            out.writeLong(refId);   // ref-форма: refId вставным полем сразу за сентинелом (§10.1)
        }
        out.writeLong(methodId);
        out.writeInt(deadlineMillis);
        out.writeByte(argCount & 0xFF); // uint8
    }

    public static RgetHeader decodeRgetHeader(RmapByteReader in) {
        int subjectId = in.readInt();
        if (subjectId < -1) {
            throw new WireProtocolException(OtherCode.PROTOCOL_ERROR, "subjectId < -1: " + subjectId);
        }
        long refId = 0L;
        if (subjectId == -1) {
            refId = in.readLong();
        }
        long methodId = in.readLong();
        int deadlineMillis = in.readInt();
        if (deadlineMillis < 0) {
            throw new WireProtocolException(OtherCode.PROTOCOL_ERROR, "deadlineMillis < 0: " + deadlineMillis);
        }
        if (deadlineMillis > MAX_DEADLINE_MILLIS) {
            deadlineMillis = MAX_DEADLINE_MILLIS; // верхний кламп (§7.2)
        }
        int argCount = in.readUnsignedByte(); // uint8 0..255
        return new RgetHeader(subjectId, refId, methodId, deadlineMillis, argCount);
    }

    // ---- OTHER (§4.2, §7.3) ----

    @Value
    public static class Other {
        int code;
        String message;
        ExceptionData exception; // null при hasException=0
    }

    public static void encodeOther(RmapByteWriter out, int code, String message) {
        out.writeInt(code);
        out.writeStr(message == null ? "" : message);
        out.writeBool(false); // hasException=0
    }

    /** OTHER с приложенным EXCEPTION-TLV (§7.3): {@code ctxWriter} пишет РОВНО один TLV с тегом 0x15
     *  (обычно {@code codec.encodeThrowable(out, cause, ctx)}), используя connection-scoped ctx. */
    public static void encodeOtherWithException(RmapByteWriter out, int code, String message,
                                                Consumer<RmapByteWriter> ctxWriter) {
        out.writeInt(code);
        out.writeStr(message == null ? "" : message);
        out.writeBool(true); // hasException=1
        ctxWriter.accept(out);
    }

    public static Other decodeOther(RmapByteReader in, RmapCodec codec, CodecContext ctx) {
        int code = in.readInt();
        String message = in.readStr();
        int hasException = in.readUnsignedByte();
        if (hasException == 0) {
            return new Other(code, message, null);
        }
        if (hasException != 1) {
            throw new RmapCodecException("bad OTHER hasException: " + hasException);
        }
        Object tlv = codec.decode(in, ctx);
        if (!(tlv instanceof ExceptionData)) {
            throw new RmapCodecException("OTHER hasException=1 but TLV is not EXCEPTION (tag 0x15)");
        }
        return new Other(code, message, (ExceptionData) tlv);
    }

    // ---- REF_RELEASE (§4.2) — используется задачей 5; кодек здесь для симметрии wire-слоя ----

    public static void encodeRefRelease(RmapByteWriter out, long[] refIds) {
        out.writeInt(refIds.length);
        for (long refId : refIds) {
            out.writeLong(refId);
        }
    }

    public static long[] decodeRefRelease(RmapByteReader in) {
        int count = in.readInt();
        if (count < 0 || count > in.remaining() / 8) { // §5.1: size ≤ остаток/мин.размер элемента
            throw new RmapCodecException("bad REF_RELEASE count: " + count);
        }
        long[] refIds = new long[count];
        for (int i = 0; i < count; i++) {
            refIds[i] = in.readLong();
        }
        return refIds;
    }
}
