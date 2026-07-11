package me.moonways.rmap.codec;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Запись в растущий big-endian байтовый буфер. Не потокобезопасен (один поток на инстанс). */
public final class RmapByteWriter {

    private byte[] buf = new byte[64];
    private int pos = 0;

    public int position() {
        return pos;
    }

    private void ensure(int extra) {
        int required = pos + extra;
        // extra < 0 не должно случаться при корректном использовании; required < 0 —
        // переполнение int (pos + extra ушли за Integer.MAX_VALUE). Оба случая — явная ошибка,
        // а не повод зациклить/переполнить cap в сдвиге ниже.
        if (extra < 0 || required < 0) {
            throw new RmapCodecException("buffer size overflow");
        }
        if (required > buf.length) {
            int cap = buf.length;
            while (cap < required) {
                int doubled = cap << 1;
                cap = (doubled > cap) ? doubled : required; // защита от переполнения/зацикливания сдвига
            }
            buf = Arrays.copyOf(buf, cap);
        }
    }

    public void writeByte(int v) {
        ensure(1);
        buf[pos++] = (byte) v;
    }

    public void writeBool(boolean v) {
        writeByte(v ? 1 : 0);
    }

    public void writeShort(int v) {
        ensure(2);
        buf[pos++] = (byte) (v >>> 8);
        buf[pos++] = (byte) v;
    }

    public void writeChar(char v) {
        writeShort(v);
    }

    public void writeInt(int v) {
        ensure(4);
        buf[pos++] = (byte) (v >>> 24);
        buf[pos++] = (byte) (v >>> 16);
        buf[pos++] = (byte) (v >>> 8);
        buf[pos++] = (byte) v;
    }

    public void writeLong(long v) {
        ensure(8);
        for (int shift = 56; shift >= 0; shift -= 8) {
            buf[pos++] = (byte) (v >>> shift);
        }
    }

    public void writeFloat(float v) {
        writeInt(Float.floatToIntBits(v));
    }

    public void writeDouble(double v) {
        writeLong(Double.doubleToLongBits(v));
    }

    public void writeRaw(byte[] bytes) {
        ensure(bytes.length);
        System.arraycopy(bytes, 0, buf, pos, bytes.length);
        pos += bytes.length;
    }

    public void writeStr(String s) {
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        writeInt(utf8.length);
        writeRaw(utf8);
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(buf, pos);
    }
}
