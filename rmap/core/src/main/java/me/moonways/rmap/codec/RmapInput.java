package me.moonways.rmap.codec;

/** Контекст чтения, передаваемый в ValueCodec.read (спека §5.3). */
public final class RmapInput {

    private final RmapByteReader buf;
    private final RmapCodec.TlvReader tlv;

    RmapInput(RmapByteReader buf, RmapCodec.TlvReader tlv) {
        this.buf = buf;
        this.tlv = tlv;
    }

    public boolean readBool() { return buf.readBool(); }
    public int readInt() { return buf.readInt(); }
    public long readLong() { return buf.readLong(); }
    public String readString() { return buf.readStr(); }
    public byte[] readBytes() { return buf.readRaw(buf.readInt()); }
    public Object readTlv() { return tlv.read(); }
}
