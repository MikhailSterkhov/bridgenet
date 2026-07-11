package me.moonways.rmap.codec;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.moonways.rmap.api.RmapSerializable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoundtripPropertyTest {

    @RmapSerializable
    @Getter @Setter @NoArgsConstructor
    static class Bag {
        private int n;
        private String s;
        private List<Object> items = new ArrayList<>();
        private Bag child; // допускает циклы
    }

    private final RmapCodec codec = new RmapCodec();

    // Детерминированный LCG (без Math.random — воспроизводимость).
    private long seed = 0x9E3779B97F4A7C15L;
    private int nextInt(int bound) {
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        return (int) ((seed >>> 33) % bound);
    }

    private Bag randomBag(int depth) {
        Bag b = new Bag();
        b.setN(nextInt(1000) - 500);
        b.setS("s" + nextInt(100));
        int items = nextInt(4);
        for (int i = 0; i < items; i++) {
            switch (nextInt(3)) {
                case 0: b.getItems().add(nextInt(1000)); break;
                case 1: b.getItems().add("x" + nextInt(50)); break;
                default: b.getItems().add(nextInt(2) == 0); break;
            }
        }
        if (depth > 0 && nextInt(2) == 0) {
            b.setChild(randomBag(depth - 1));
        }
        return b;
    }

    @Test
    void random_graphs_roundtrip() {
        for (int i = 0; i < 500; i++) {
            Bag original = randomBag(3);
            RmapByteWriter w = new RmapByteWriter();
            codec.encode(w, original);
            byte[] bytes = w.toByteArray();
            Bag back = (Bag) codec.decode(new RmapByteReader(bytes, 0, bytes.length), null);
            assertThat(back.getN()).isEqualTo(original.getN());
            assertThat(back.getS()).isEqualTo(original.getS());
            assertThat(back.getItems()).isEqualTo(original.getItems());
            assertThat(back.getChild() == null).isEqualTo(original.getChild() == null);
        }
    }

    @Test
    void shared_reference_stays_shared() {
        Bag shared = new Bag();
        shared.setS("shared");
        Bag a = new Bag();
        a.setChild(shared);
        a.getItems().add(shared); // тот же объект в двух местах
        RmapByteWriter w = new RmapByteWriter();
        codec.encode(w, a);
        byte[] bytes = w.toByteArray();
        Bag back = (Bag) codec.decode(new RmapByteReader(bytes, 0, bytes.length), null);
        assertThat(back.getChild()).isSameAs(back.getItems().get(0));
    }
}
