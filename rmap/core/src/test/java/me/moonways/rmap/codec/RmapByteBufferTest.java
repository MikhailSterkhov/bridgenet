package me.moonways.rmap.codec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RmapByteBufferTest {

    private RmapByteReader reader(RmapByteWriter w) {
        byte[] b = w.toByteArray();
        return new RmapByteReader(b, 0, b.length);
    }

    @Test
    void roundtrips_all_primitives_big_endian() {
        RmapByteWriter w = new RmapByteWriter();
        w.writeByte(0xAB);
        w.writeBool(true);
        w.writeShort(-12345);
        w.writeChar('Ω');
        w.writeInt(0x01020304);
        w.writeLong(0x0102030405060708L);
        w.writeFloat(3.14f);
        w.writeDouble(2.718281828d);
        w.writeStr("привет");

        RmapByteReader r = reader(w);
        assertThat(r.readUnsignedByte()).isEqualTo(0xAB);
        assertThat(r.readBool()).isTrue();
        assertThat(r.readShort()).isEqualTo(-12345);
        assertThat(r.readChar()).isEqualTo('Ω');
        assertThat(r.readInt()).isEqualTo(0x01020304);
        assertThat(r.readLong()).isEqualTo(0x0102030405060708L);
        assertThat(r.readFloat()).isEqualTo(3.14f);
        assertThat(r.readDouble()).isEqualTo(2.718281828d);
        assertThat(r.readStr()).isEqualTo("привет");
        assertThat(r.remaining()).isZero();
    }

    @Test
    void int_is_big_endian_on_the_wire() {
        RmapByteWriter w = new RmapByteWriter();
        w.writeInt(1);
        assertThat(w.toByteArray()).containsExactly(0x00, 0x00, 0x00, 0x01);
    }

    @Test
    void empty_string_roundtrips() {
        RmapByteWriter w = new RmapByteWriter();
        w.writeStr("");
        assertThat(reader(w).readStr()).isEmpty();
    }

    @Test
    void reading_past_end_fails() {
        RmapByteReader r = new RmapByteReader(new byte[]{0x00, 0x01}, 0, 2);
        r.readShort();
        assertThatThrownBy(r::readUnsignedByte).isInstanceOf(RmapCodecException.class);
    }

    @Test
    void negative_string_length_fails() {
        RmapByteWriter w = new RmapByteWriter();
        w.writeInt(-1); // подделанная длина строки
        byte[] b = w.toByteArray();
        assertThatThrownBy(() -> new RmapByteReader(b, 0, b.length).readStr())
                .isInstanceOf(RmapCodecException.class);
    }

    @Test
    void string_length_exceeding_buffer_fails() {
        RmapByteWriter w = new RmapByteWriter();
        w.writeInt(1000); // длина больше остатка
        byte[] b = w.toByteArray();
        assertThatThrownBy(() -> new RmapByteReader(b, 0, b.length).readStr())
                .isInstanceOf(RmapCodecException.class);
    }

    @Test
    void max_int_string_length_does_not_oom() {
        RmapByteWriter w = new RmapByteWriter();
        w.writeInt(Integer.MAX_VALUE); // поддельная длина, буфер всего 4 байта
        byte[] b = w.toByteArray();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new RmapByteReader(b, 0, b.length).readStr())
                .isInstanceOf(RmapCodecException.class);
    }
}
