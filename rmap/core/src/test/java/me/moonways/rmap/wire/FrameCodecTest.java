package me.moonways.rmap.wire;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrameCodecTest {

    @Test
    void frame_type_codes_are_distinct_and_roundtrip() {
        for (FrameType t : FrameType.values()) {
            assertThat(FrameType.byCode(t.code())).isEqualTo(t);
        }
    }

    @Test
    void unknown_frame_code_rejected() {
        assertThatThrownBy(() -> FrameType.byCode(0x7F))
                .isInstanceOf(me.moonways.rmap.codec.RmapCodecException.class);
    }

    @Test
    void encode_layout_is_len_type_callid_payload() {
        byte[] payload = {10, 20, 30};
        byte[] wire = FrameCodec.encode(new Frame(FrameType.PING, 42L, payload));
        me.moonways.rmap.codec.RmapByteReader r =
                new me.moonways.rmap.codec.RmapByteReader(wire, 0, wire.length);
        int len = r.readInt();
        assertThat(len).isEqualTo(1 + 8 + payload.length); // после поля len
        assertThat(r.readUnsignedByte()).isEqualTo(FrameType.PING.code());
        assertThat(r.readLong()).isEqualTo(42L);
        assertThat(r.readRaw(payload.length)).containsExactly(payload);
        assertThat(r.remaining()).isZero();
    }

    @Test
    void empty_payload_roundtrips() {
        byte[] wire = FrameCodec.encode(new Frame(FrameType.AUTH_OK, 0L, new byte[0]));
        me.moonways.rmap.codec.RmapByteReader r =
                new me.moonways.rmap.codec.RmapByteReader(wire, 0, wire.length);
        assertThat(r.readInt()).isEqualTo(9);
        assertThat(r.readUnsignedByte()).isEqualTo(FrameType.AUTH_OK.code());
        assertThat(r.readLong()).isZero();
        assertThat(r.remaining()).isZero();
    }
}
