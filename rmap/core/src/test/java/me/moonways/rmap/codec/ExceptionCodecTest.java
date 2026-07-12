package me.moonways.rmap.codec;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExceptionCodecTest {

    private final RmapCodec codec = new RmapCodec();

    private CodecContext freshCtx() {
        return CodecContext.of(new ClassInterner(), RmapCodec.ACCEPT_ALL_CLASSES);
    }

    private ExceptionData roundtrip(Throwable t) {
        RmapByteWriter out = new RmapByteWriter();
        codec.encodeThrowable(out, t, freshCtx());
        byte[] wire = out.toByteArray();
        assertThat(wire[0]).isEqualTo((byte) Tags.EXCEPTION);
        return (ExceptionData) codec.decode(new RmapByteReader(wire, 0, wire.length), freshCtx());
    }

    @Test
    void exception_roundtrips_with_class_message_and_frames() {
        IllegalStateException boom = new IllegalStateException("boom");
        ExceptionData d = roundtrip(boom);
        assertThat(d.getClassName()).isEqualTo("java.lang.IllegalStateException");
        assertThat(d.getMessage()).isEqualTo("boom");
        assertThat(d.getFrames()).isNotEmpty();
        assertThat(d.getFrames()[0].getMethodName()).isNotEmpty();
        assertThat(d.getCause()).isNull();
    }

    @Test
    void cause_chain_preserved_and_null_message_becomes_null() {
        RuntimeException root = new RuntimeException((String) null);
        IllegalArgumentException wrap = new IllegalArgumentException("outer", root);
        ExceptionData d = roundtrip(wrap);
        assertThat(d.getMessage()).isEqualTo("outer");
        assertThat(d.getCause()).isNotNull();
        assertThat(d.getCause().getClassName()).isEqualTo("java.lang.RuntimeException");
        assertThat(d.getCause().getMessage()).isNull();
    }

    @Test
    void cause_chain_truncated_at_8() {
        RuntimeException e = new RuntimeException("level-0");
        for (int i = 1; i <= 20; i++) {
            e = new RuntimeException("level-" + i, e);
        }
        ExceptionData d = roundtrip(e);
        int depth = 1;
        for (ExceptionData c = d.getCause(); c != null; c = c.getCause()) depth++;
        assertThat(depth).isLessThanOrEqualTo(8);
    }

    @Test
    void stack_depth_capped_at_64_on_write_and_rejected_beyond_on_read() {
        RuntimeException deep = new RuntimeException("deep");
        ExceptionData d = roundtrip(deep);
        assertThat(d.getFrames().length).isLessThanOrEqualTo(64);

        // враждебный кадр: stackDepth=65 → CODEC_ERROR
        RmapByteWriter out = new RmapByteWriter();
        out.writeByte(Tags.EXCEPTION);
        out.writeStr("java.lang.RuntimeException");
        out.writeStr("evil");
        out.writeInt(65);
        byte[] wire = out.toByteArray();
        assertThatThrownBy(() -> codec.decode(new RmapByteReader(wire, 0, wire.length), freshCtx()))
                .isInstanceOf(RmapCodecException.class);
    }

    @Test
    void throwable_value_inside_graph_uses_exception_tag() {
        RmapByteWriter out = new RmapByteWriter();
        codec.encode(out, new IllegalStateException("in-graph"), freshCtx());
        assertThat(out.toByteArray()[0]).isEqualTo((byte) Tags.EXCEPTION);
    }

    @Test
    void remote_ref_tag_rejected_without_ref_context() {
        RmapByteWriter out = new RmapByteWriter();
        out.writeByte(Tags.REMOTE_REF);
        out.writeLong(1L);
        byte[] wire = out.toByteArray();
        assertThatThrownBy(() -> codec.decode(new RmapByteReader(wire, 0, wire.length), freshCtx()))
                .isInstanceOf(RmapCodecException.class)
                .hasMessageContaining("remote refs");
    }
}
