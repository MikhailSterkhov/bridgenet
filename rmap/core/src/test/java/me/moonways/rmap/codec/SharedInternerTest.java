package me.moonways.rmap.codec;

import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedInternerTest {

    private final RmapCodec codec = new RmapCodec();

    enum Colors { RED, GREEN }

    @Test
    void second_frame_reuses_class_id_from_shared_interner() {
        ClassInterner writeInterner = new ClassInterner();
        ClassInterner readInterner = new ClassInterner();
        CodecContext w = CodecContext.of(writeInterner, RmapCodec.ACCEPT_ALL_CLASSES);
        CodecContext r = CodecContext.of(readInterner, RmapCodec.ACCEPT_ALL_CLASSES);

        RmapByteWriter f1 = new RmapByteWriter();
        codec.encode(f1, Colors.RED, w);
        RmapByteWriter f2 = new RmapByteWriter();
        codec.encode(f2, Colors.GREEN, w);

        // кадр 2 короче кадра 1: classRef во втором — reference (0x01+int32), не definition (0x00+FQN)
        assertThat(f2.toByteArray().length).isLessThan(f1.toByteArray().length);

        byte[] b1 = f1.toByteArray();
        byte[] b2 = f2.toByteArray();
        assertThat(codec.decode(new RmapByteReader(b1, 0, b1.length), r)).isEqualTo(Colors.RED);
        assertThat(codec.decode(new RmapByteReader(b2, 0, b2.length), r)).isEqualTo(Colors.GREEN);
    }

    @Test
    void reference_before_definition_fails_on_fresh_reader() {
        ClassInterner writeInterner = new ClassInterner();
        CodecContext w = CodecContext.of(writeInterner, RmapCodec.ACCEPT_ALL_CLASSES);
        RmapByteWriter f1 = new RmapByteWriter();
        codec.encode(f1, Colors.RED, w);   // definition уехала в f1
        RmapByteWriter f2 = new RmapByteWriter();
        codec.encode(f2, Colors.GREEN, w); // f2 несёт reference

        // читатель, который видел ТОЛЬКО f2 (пропущенная definition) → CODEC_ERROR
        byte[] b2 = f2.toByteArray();
        assertThatThrownBy(() -> codec.decode(new RmapByteReader(b2, 0, b2.length),
                CodecContext.of(new ClassInterner(), RmapCodec.ACCEPT_ALL_CLASSES)))
                .isInstanceOf(RmapCodecException.class);
    }

    @Test
    void back_refs_do_not_leak_between_frames() {
        // RefTable свежий per-вызов: один и тот же список в двух кадрах — оба кадра самодостаточны
        ClassInterner wi = new ClassInterner();
        ClassInterner ri = new ClassInterner();
        List<String> shared = asList("a", "b");
        CodecContext w = CodecContext.of(wi, RmapCodec.ACCEPT_ALL_CLASSES);
        CodecContext r = CodecContext.of(ri, RmapCodec.ACCEPT_ALL_CLASSES);

        RmapByteWriter f1 = new RmapByteWriter();
        codec.encode(f1, shared, w);
        RmapByteWriter f2 = new RmapByteWriter();
        codec.encode(f2, shared, w); // НЕ BACK_REF на кадр 1 — свежая таблица

        byte[] b2 = f2.toByteArray();
        Object decoded = codec.decode(new RmapByteReader(b2, 0, b2.length), r);
        assertThat(decoded).isEqualTo(shared);
    }

    @Test
    void interner_limit_rejected() {
        ClassInterner tiny = new ClassInterner(1);
        CodecContext w = CodecContext.of(tiny, RmapCodec.ACCEPT_ALL_CLASSES);
        RmapByteWriter out = new RmapByteWriter();
        codec.encode(out, Colors.RED, w); // 1-й класс — ок
        assertThatThrownBy(() -> {
            RmapByteWriter out2 = new RmapByteWriter();
            codec.encode(out2, java.time.DayOfWeek.MONDAY, w); // 2-й класс — сверх лимита
        }).isInstanceOf(RmapCodecException.class).hasMessageContaining("limit");
    }
}
