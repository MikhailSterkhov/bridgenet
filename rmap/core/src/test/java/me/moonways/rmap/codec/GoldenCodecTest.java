package me.moonways.rmap.codec;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.moonways.rmap.api.RmapSerializable;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-эталоны wire-формата: фиксируют точные байты кодека, чтобы любой будущий дрейф
 * TLV-представления был пойман тестом. Поток работы: при отсутствии ресурса golden/&lt;name&gt;.hex
 * тест печатает актуальный hex (строка "GENERATE ...") и падает; hex вписывается в ресурс,
 * повторный прогон проверяет побайтовое совпадение. Декодирование проверяется roundtrip- и
 * property-тестами; golden фиксирует именно провод.
 */
class GoldenCodecTest {

    @RmapSerializable
    @Getter @AllArgsConstructor @EqualsAndHashCode
    static class Pair {
        private final String key;
        private final int value;
    }

    @RmapSerializable
    @Getter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    static class Base {
        private long id;
    }

    @RmapSerializable
    @Getter @NoArgsConstructor @EqualsAndHashCode(callSuper = true)
    static class Derived extends Base {
        private String name;
        Derived(long id, String name) { super(id); this.name = name; }
    }

    @RmapSerializable
    @Getter @AllArgsConstructor @EqualsAndHashCode
    static class Holder {
        private final Pair a;
        private final Pair b;
    }

    enum Color { RED, GREEN, BLUE }

    private final RmapCodec codec = new RmapCodec();

    private byte[] encode(Object v) {
        RmapByteWriter w = new RmapByteWriter();
        codec.encode(w, v);
        return w.toByteArray();
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String encodeHex(Object v) {
        return toHex(encode(v));
    }

    private String golden(String name) {
        try (InputStream is = getClass().getResourceAsStream("/golden/" + name + ".hex")) {
            if (is == null) {
                return null;
            }
            return new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next().trim();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Единая проверка golden: печать GENERATE при отсутствии эталона, иначе — побайтовое сравнение. */
    private void assertGolden(String name, String hex) {
        String expected = golden(name);
        if (expected == null) {
            System.out.println("GENERATE golden/" + name + ".hex = " + hex);
        }
        assertThat(expected).as("создай ресурс golden/" + name + ".hex со значением из stdout").isNotNull();
        assertThat(hex).as("wire-дрейф golden/" + name).isEqualTo(expected);
    }

    @Test
    void golden_pair() {
        assertGolden("pair", encodeHex(new Pair("k", 42)));
    }

    @Test
    void golden_pair_list() {
        // Список из двух Pair: FQN Pair пишется один раз (classRef-интернирование §5.2a),
        // второй Pair ссылается на интернированный класс. Проверяем это и напрямую по потоку.
        List<Pair> list = Arrays.asList(new Pair("a", 1), new Pair("b", 2));
        byte[] bytes = encode(list);
        String fqn = Pair.class.getName();
        String latin1 = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(countOccurrences(latin1, fqn))
                .as("FQN Pair должен встречаться в потоке ровно один раз (интернирование)")
                .isEqualTo(1);
        assertGolden("pair_list", toHex(bytes));
    }

    @Test
    void golden_inheritance() {
        // Поля пишутся от корня к листу (Base.id, затем Derived.name).
        assertGolden("inheritance", encodeHex(new Derived(99L, "leaf")));
    }

    @Test
    void golden_enum() {
        assertGolden("enum", encodeHex(Color.GREEN));
    }

    @Test
    void golden_map() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        assertGolden("map", encodeHex(map));
    }

    @Test
    void golden_backref() {
        // Holder держит ОДИН и тот же инстанс Pair в обоих полях: второе вхождение
        // кодируется как BACK_REF (0x12) на уже записанный объект (RefTable), а не
        // как повторная сериализация — это самая хрупкая часть wire-формата.
        Pair shared = new Pair("k", 42);
        Holder h = new Holder(shared, shared);
        assertGolden("backref", encodeHex(h));
    }

    private static int countOccurrences(String haystack, String needle) {
        int c = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            c++;
            i += needle.length();
        }
        return c;
    }
}
