package me.moonways.rmap.codec;

import java.nio.charset.StandardCharsets;

/** Чтение big-endian байтового буфера с валидацией границ (спека §5.1). */
public final class RmapByteReader {

    private final byte[] buf;
    private final int end;
    private int pos;

    public RmapByteReader(byte[] buf, int off, int len) {
        this.buf = buf;
        this.pos = off;
        this.end = off + len;
    }

    public int position() {
        return pos;
    }

    public int remaining() {
        return end - pos;
    }

    private void need(int n) {
        // Сравниваем с remaining() (end - pos), а не pos + n > end: при n, близком к
        // Integer.MAX_VALUE, сумма pos + n переполняет int и уходит в отрицательное,
        // что делало проверку ложной (anti-DoS §5.1 п.3).
        if (n < 0 || n > remaining()) {
            throw new RmapCodecException("buffer underflow: need " + n + ", remaining " + remaining());
        }
    }

    public int readUnsignedByte() {
        need(1);
        return buf[pos++] & 0xFF;
    }

    public boolean readBool() {
        return readUnsignedByte() != 0;
    }

    public int readShort() {
        need(2);
        return (short) (((buf[pos++] & 0xFF) << 8) | (buf[pos++] & 0xFF));
    }

    public char readChar() {
        need(2);
        return (char) (((buf[pos++] & 0xFF) << 8) | (buf[pos++] & 0xFF));
    }

    public int readInt() {
        need(4);
        return ((buf[pos++] & 0xFF) << 24) | ((buf[pos++] & 0xFF) << 16)
                | ((buf[pos++] & 0xFF) << 8) | (buf[pos++] & 0xFF);
    }

    public long readLong() {
        need(8);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (buf[pos++] & 0xFF);
        }
        return v;
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public byte[] readRaw(int n) {
        need(n);
        byte[] out = new byte[n];
        System.arraycopy(buf, pos, out, 0, n);
        pos += n;
        return out;
    }

    public String readStr() {
        int len = readInt();
        byte[] utf8 = readRaw(len); // need(len) внутри валидирует len>=0 и len<=remaining, без риска integer-overflow
        return new String(utf8, StandardCharsets.UTF_8);
    }
}
