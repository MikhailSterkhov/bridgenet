package me.moonways.rmap.codec;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.moonways.rmap.api.RmapSerializable;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectCodecTest {

    @RmapSerializable
    @Getter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    static class Point {
        private int x;
        private int y;
        private String label; // порядок полей на проводе: label, x, y (по имени)
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
    @Getter @NoArgsConstructor
    static class Node {
        private String tag;
        private Node next; // для циклов/back-ref
    }

    @RmapSerializable
    @Getter @NoArgsConstructor @AllArgsConstructor
    static class Pair {
        private Point a;
        private Point b; // контейнер для проверки back-ref/интернирования без массивов (массивы — задача 5)
    }

    enum Color { RED, GREEN, BLUE }

    private final RmapCodec codec = new RmapCodec();

    @SuppressWarnings("unchecked")
    private <T> T roundtrip(T v) {
        RmapByteWriter w = new RmapByteWriter();
        codec.encode(w, v);
        byte[] b = w.toByteArray();
        return (T) codec.decode(new RmapByteReader(b, 0, b.length), null);
    }

    @Test
    void flat_object_roundtrips() {
        Point p = new Point(3, 4, "origin");
        assertThat(roundtrip(p)).isEqualTo(p);
    }

    @Test
    void object_with_null_field_roundtrips() {
        assertThat(roundtrip(new Point(1, 2, null))).isEqualTo(new Point(1, 2, null));
    }

    @Test
    void inheritance_roundtrips_fields_root_to_leaf() {
        Derived d = new Derived(99L, "leaf");
        Derived back = roundtrip(d);
        assertThat(back.getId()).isEqualTo(99L);
        assertThat(back.getName()).isEqualTo("leaf");
    }

    @Test
    void field_order_is_by_name_not_declaration() {
        // Point поля объявлены x,y,label — на проводе идут label,x,y (по имени).
        java.util.List<java.lang.reflect.Field> schema = ClassSchema.of(Point.class);
        assertThat(schema).extracting(java.lang.reflect.Field::getName)
                .containsExactly("label", "x", "y");
    }

    @Test
    void repeated_identical_object_becomes_back_ref() {
        // Один и тот же объект, на который ссылаются дважды (поля a и b), на проводе
        // становится BACK_REF; после декодирования identity должна сохраниться.
        Point shared = new Point(7, 7, "s");
        Pair pair = new Pair(shared, shared);
        Pair back = roundtrip(pair);
        assertThat(back.getA()).isSameAs(back.getB()); // identity сохранена через BACK_REF
    }

    @Test
    void self_cycle_does_not_overflow() {
        Node n = new Node();
        n.tag = "loop";
        n.next = n; // цикл
        Node back = roundtrip(n);
        assertThat(back.tag).isEqualTo("loop");
        assertThat(back.next).isSameAs(back);
    }

    @Test
    void class_interning_second_occurrence_is_shorter() {
        // Два разных Point в одном сообщении: второй пишется classRef-use, поэтому
        // FQN Point встречается в потоке ровно один раз (интернирование §5.2a).
        Pair two = new Pair(new Point(1, 1, "a"), new Point(2, 2, "b"));
        RmapByteWriter w = new RmapByteWriter();
        codec.encode(w, two);
        String hex = new String(w.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1);
        String fqn = Point.class.getName();
        assertThat(countOccurrences(hex, fqn)).isEqualTo(1);
    }

    @Test
    void whitelist_rejects_unknown_class_before_loading() {
        Point p = new Point(1, 2, "x");
        RmapByteWriter w = new RmapByteWriter();
        codec.encode(w, p);
        byte[] b = w.toByteArray();
        java.util.Set<String> whitelist = java.util.Collections.singleton("some.other.Class");
        assertThatThrownBy(() -> codec.decode(new RmapByteReader(b, 0, b.length), whitelist))
                .isInstanceOf(RmapCodecException.class);
    }

    @Test
    void depth_limit_is_enforced() {
        // Цепочка Node глубиной 40 > MAX_DEPTH(32). Лимит ловится симметрично на обеих
        // сторонах; encode отвергает её первым (checkDepth в OBJECT-ветке) — важно, что
        // 40 > 32 отвергается вообще (см. замечание брифа к Step 8).
        Node head = new Node();
        Node cur = head;
        for (int i = 0; i < 40; i++) {
            cur.tag = "n" + i;
            cur.next = new Node();
            cur = cur.next;
        }
        assertThatThrownBy(() -> {
            RmapByteWriter w = new RmapByteWriter();
            codec.encode(w, head);
        }).isInstanceOf(RmapCodecException.class);
    }

    @Test
    void wrong_type_in_primitive_field_throws_codec_exception() {
        // Кадр OBJECT для Point с подделанным полем x (int): вместо Tags.INT стоит
        // Tags.STRING — type-confusion в примитивном слоте должна давать CODEC_ERROR
        // (спека §5.1), а не NPE/CCE из UnsafeAllocator.putField.
        RmapByteWriter w = new RmapByteWriter();
        w.writeByte(Tags.OBJECT);
        ClassInterner ci = new ClassInterner();
        ci.writeClassRef(w, Point.class); // classRef def
        // поля Point по имени: label(String), x(int), y(int)
        w.writeByte(Tags.STRING);           // label = "L"
        w.writeStr("L");
        w.writeByte(Tags.STRING);           // x — ПОДДЕЛКА: String вместо INT
        w.writeStr("not-an-int");
        w.writeByte(Tags.INT);              // y = 0
        w.writeInt(0);
        byte[] b = w.toByteArray();
        assertThatThrownBy(() -> new RmapCodec().decode(new RmapByteReader(b, 0, b.length), null))
                .isInstanceOf(RmapCodecException.class);
    }

    @Test
    void wrong_type_in_object_field_throws_codec_exception() {
        // Кадр OBJECT для Point, где в слот поля label (String) записан не-STRING тег (LIST,
        // size 0 → пустой ArrayList). isInstance-гейт в UnsafeAllocator.putField обязан
        // отвергнуть type-confusion объектного поля (спека §5.1), а не писать ArrayList в String.
        RmapByteWriter w = new RmapByteWriter();
        w.writeByte(Tags.OBJECT);
        ClassInterner ci = new ClassInterner();
        ci.writeClassRef(w, Point.class);
        // поля Point по имени: label(String), x(int), y(int)
        w.writeByte(Tags.LIST);             // label — ПОДДЕЛКА: LIST вместо STRING
        w.writeInt(0);                      // пустой список
        w.writeByte(Tags.INT);              // x = 0
        w.writeInt(0);
        w.writeByte(Tags.INT);              // y = 0
        w.writeInt(0);
        byte[] b = w.toByteArray();
        assertThatThrownBy(() -> new RmapCodec().decode(new RmapByteReader(b, 0, b.length), null))
                .isInstanceOf(RmapCodecException.class);
    }

    @Test
    void object_tag_with_enum_class_is_rejected() {
        // Кадр OBJECT с classRef на enum-класс: enum-guard в decodeObject обязан отвергнуть
        // (иначе Unsafe.allocateInstance(enum) + запись name/ordinal → фейковая «константа»).
        RmapByteWriter w = new RmapByteWriter();
        w.writeByte(Tags.OBJECT);
        new ClassInterner().writeClassRef(w, Color.class);
        byte[] b = w.toByteArray();
        assertThatThrownBy(() -> new RmapCodec().decode(new RmapByteReader(b, 0, b.length), null))
                .isInstanceOf(RmapCodecException.class);
    }

    @Test
    void deep_list_chain_is_rejected_on_decode() {
        // Ручной кадр: 40 вложенных LIST (size=1) минуя encode-гейт. decode обязан отвергнуть
        // глубину >32 (checkDepth на decode-стороне), доказывая лимит глубины на весь граф.
        RmapByteWriter w = new RmapByteWriter();
        for (int i = 0; i < 40; i++) {
            w.writeByte(Tags.LIST);
            w.writeInt(1); // ровно один элемент — следующий вложенный LIST
        }
        w.writeByte(Tags.NULL); // дно
        byte[] b = w.toByteArray();
        assertThatThrownBy(() -> new RmapCodec().decode(new RmapByteReader(b, 0, b.length), null))
                .isInstanceOf(RmapCodecException.class);
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
