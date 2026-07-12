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

    private Object decode(byte[] bytes) {
        return codec.decode(new RmapByteReader(bytes, 0, bytes.length), null);
    }

    private static byte[] fromHex(String hex) {
        int n = hex.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
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

    /**
     * Двунаправленная проверка golden (спека §12): (1) encode(value) побайтово == эталону
     * (печать GENERATE при отсутствии ресурса); (2) decode(эталон) equals исходному value.
     */
    private void assertGolden(String name, Object value) {
        String hex = encodeHex(value);
        String expected = golden(name);
        if (expected == null) {
            System.out.println("GENERATE golden/" + name + ".hex = " + hex);
        }
        assertThat(expected).as("создай ресурс golden/" + name + ".hex со значением из stdout").isNotNull();
        assertThat(hex).as("wire-дрейф golden/" + name).isEqualTo(expected);
        // обратное направление: эталон → decode → equals исходному значению.
        Object decoded = decode(fromHex(expected));
        assertThat(decoded).as("decode эталона golden/" + name + " должен дать equals-значение").isEqualTo(value);
    }

    @Test
    void golden_pair() {
        assertGolden("pair", new Pair("k", 42));
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
        assertGolden("pair_list", list);
    }

    @Test
    void golden_inheritance() {
        // Поля пишутся от корня к листу (Base.id, затем Derived.name).
        assertGolden("inheritance", new Derived(99L, "leaf"));
    }

    @Test
    void golden_enum() {
        assertGolden("enum", Color.GREEN);
    }

    @Test
    void golden_map() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        assertGolden("map", map);
    }

    @Test
    void golden_backref() {
        // Holder держит ОДИН и тот же инстанс Pair в обоих полях: второе вхождение
        // кодируется как BACK_REF (0x12) на уже записанный объект (RefTable), а не
        // как повторная сериализация — это самая хрупкая часть wire-формата.
        Pair shared = new Pair("k", 42);
        Holder h = new Holder(shared, shared);
        assertGolden("backref", h);
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
