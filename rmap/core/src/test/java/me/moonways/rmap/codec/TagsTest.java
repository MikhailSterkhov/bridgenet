package me.moonways.rmap.codec;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TagsTest {

    @Test
    void tags_are_distinct_and_in_spec_range() {
        int[] all = {Tags.NULL, Tags.TRUE, Tags.FALSE, Tags.BYTE, Tags.SHORT, Tags.INT,
                Tags.LONG, Tags.FLOAT, Tags.DOUBLE, Tags.CHAR, Tags.STRING, Tags.UUID,
                Tags.ENUM, Tags.LIST, Tags.SET, Tags.MAP, Tags.ARRAY, Tags.OBJECT,
                Tags.BACK_REF, Tags.VALUE_CODEC, Tags.REMOTE_REF, Tags.EXCEPTION, Tags.BYTES};
        assertThat(all).doesNotHaveDuplicates();
        // AbstractIntArrayAssert (assertj-core 3.25.3) не предоставляет allMatch(...) —
        // проверка диапазона выполняется через boxed Integer[].
        assertThat(Arrays.stream(all).boxed().toArray(Integer[]::new))
                .allMatch(t -> t >= 0x00 && t <= 0x16);
    }
}
