package me.moonways.rmap.codec;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionCodecTest {

    private final RmapCodec codec = new RmapCodec();

    @SuppressWarnings("unchecked")
    private <T> T roundtrip(T v) {
        RmapByteWriter w = new RmapByteWriter();
        codec.encode(w, v);
        byte[] b = w.toByteArray();
        return (T) codec.decode(new RmapByteReader(b, 0, b.length), null);
    }

    @Test
    void list_roundtrips_as_arraylist() {
        List<Object> in = new ArrayList<>(Arrays.asList("a", 1, true, null));
        Object back = roundtrip(in);
        assertThat(back).isInstanceOf(ArrayList.class).isEqualTo(in);
    }

    @Test
    void set_roundtrips_preserving_order() {
        Set<String> in = new LinkedHashSet<>(Arrays.asList("x", "y", "z"));
        Object back = roundtrip(in);
        assertThat(back).isInstanceOf(LinkedHashSet.class).isEqualTo(in);
    }

    @Test
    void map_roundtrips() {
        Map<String, Integer> in = new LinkedHashMap<>();
        in.put("one", 1);
        in.put("two", 2);
        assertThat(roundtrip(in)).isEqualTo(in);
    }

    @Test
    void string_array_roundtrips() {
        String[] in = {"p", "q", null, "r"};
        assertThat((String[]) roundtrip(in)).containsExactly(in);
    }

    @Test
    void empty_collections_roundtrip() {
        assertThat(roundtrip(new ArrayList<>())).isEqualTo(new ArrayList<>());
        assertThat(roundtrip(new LinkedHashMap<>())).isEqualTo(new LinkedHashMap<>());
    }

    @Test
    void negative_list_size_is_rejected() {
        RmapByteWriter w = new RmapByteWriter();
        w.writeByte(Tags.LIST);
        w.writeInt(-5); // подделанный размер
        byte[] b = w.toByteArray();
        assertThatThrownBy(() -> codec.decode(new RmapByteReader(b, 0, b.length), null))
                .isInstanceOf(RmapCodecException.class);
    }

    @Test
    void huge_declared_size_does_not_preallocate() {
        // size=2^31-1 при пустом хвосте: декодер не должен пытаться выделить массив на 2 млрд,
        // а обязан упасть при чтении первого элемента (буфер пуст).
        RmapByteWriter w = new RmapByteWriter();
        w.writeByte(Tags.LIST);
        w.writeInt(Integer.MAX_VALUE);
        byte[] b = w.toByteArray();
        assertThatThrownBy(() -> codec.decode(new RmapByteReader(b, 0, b.length), null))
                .isInstanceOf(RmapCodecException.class);
    }
}
