package me.moonways.rmap.codec;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScalarCodecTest {

    enum Color {RED, GREEN, BLUE}

    private final RmapCodec codec = new RmapCodec();

    private Object roundtrip(Object v) {
        RmapByteWriter w = new RmapByteWriter();
        codec.encode(w, v);
        byte[] b = w.toByteArray();
        return codec.decode(new RmapByteReader(b, 0, b.length));
    }

    @Test
    void null_roundtrips() {
        assertThat(roundtrip(null)).isNull();
    }

    @Test
    void primitives_roundtrip_as_boxed() {
        assertThat(roundtrip(true)).isEqualTo(Boolean.TRUE);
        assertThat(roundtrip((byte) 7)).isEqualTo((byte) 7);
        assertThat(roundtrip((short) -9)).isEqualTo((short) -9);
        assertThat(roundtrip(42)).isEqualTo(42);
        assertThat(roundtrip(42L)).isEqualTo(42L);
        assertThat(roundtrip(1.5f)).isEqualTo(1.5f);
        assertThat(roundtrip(2.5d)).isEqualTo(2.5d);
        assertThat(roundtrip('x')).isEqualTo('x');
    }

    @Test
    void string_and_uuid_roundtrip() {
        assertThat(roundtrip("hello")).isEqualTo("hello");
        UUID id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        assertThat(roundtrip(id)).isEqualTo(id);
    }

    @Test
    void byte_array_uses_bytes_tag_and_roundtrips() {
        byte[] data = {1, 2, 3, -4, 127};
        RmapByteWriter w = new RmapByteWriter();
        codec.encode(w, data);
        byte[] wire = w.toByteArray();
        assertThat(wire[0] & 0xFF).isEqualTo(Tags.BYTES);
        assertThat((byte[]) codec.decode(new RmapByteReader(wire, 0, wire.length))).containsExactly(data);
    }

    @Test
    void enum_roundtrips_by_name() {
        assertThat(roundtrip(Color.GREEN)).isEqualTo(Color.GREEN);
    }

    @Test
    void tags_match_spec() {
        RmapByteWriter w = new RmapByteWriter();
        codec.encode(w, 42);
        assertThat(w.toByteArray()[0] & 0xFF).isEqualTo(Tags.INT);
    }

    @Test
    void invalid_enum_name_fails() {
        // Кадр ENUM с валидным классом, но именем "MAGENTA", которого нет.
        RmapByteWriter w = new RmapByteWriter();
        w.writeByte(Tags.ENUM);
        w.writeStr(Color.class.getName());   // FQN (задача 3 — без интернирования)
        w.writeStr("MAGENTA");
        byte[] b = w.toByteArray();
        assertThatThrownBy(() -> codec.decode(new RmapByteReader(b, 0, b.length)))
                .isInstanceOf(RmapCodecException.class);
    }

    @Test
    void golden_int_42() {
        RmapByteWriter w = new RmapByteWriter();
        codec.encode(w, 42);
        // тег INT (0x05) + big-endian 42
        assertThat(bytesToHex(w.toByteArray())).isEqualTo("050000002a");
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }
}
