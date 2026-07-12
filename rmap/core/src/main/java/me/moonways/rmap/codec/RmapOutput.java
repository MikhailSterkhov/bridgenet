package me.moonways.rmap.codec;

/** Контекст записи, передаваемый в ValueCodec.write (спека §5.3). */
public final class RmapOutput {

    private final RmapByteWriter buf;
    private final RmapCodec.TlvWriter tlv; // рекурсивная запись произвольного значения

    RmapOutput(RmapByteWriter buf, RmapCodec.TlvWriter tlv) {
        this.buf = buf;
        this.tlv = tlv;
    }

    public void writeBool(boolean v) { buf.writeBool(v); }
    public void writeInt(int v) { buf.writeInt(v); }
    public void writeLong(long v) { buf.writeLong(v); }
    public void writeString(String v) { buf.writeStr(v); }
    public void writeBytes(byte[] v) { buf.writeInt(v.length); buf.writeRaw(v); }
    public void writeTlv(Object v) { tlv.write(v); }
}
