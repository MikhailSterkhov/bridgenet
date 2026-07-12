package me.moonways.rmap.codec;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import me.moonways.rmap.api.RmapSerializable;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueCodecTest {

    // DTO без @RmapSerializable — регистрируется через serializable()
    @Getter @AllArgsConstructor @EqualsAndHashCode
    static class Plain {
        private final int a;
        private final String b;
    }

    // Тип с собственным ValueCodec
    @Getter @AllArgsConstructor @EqualsAndHashCode
    static class Money {
        private final long cents;
    }

    static class MoneyCodec implements ValueCodec<Money> {
        public Class<Money> type() { return Money.class; }
        public void write(RmapOutput out, Money v) { out.writeLong(v.getCents()); }
        public Money read(RmapInput in) { return new Money(in.readLong()); }
    }

    // Рекурсивный тип с ValueCodec, зовущим writeTlv на ребёнка (эмуляция kyori Component плана B)
    static class Deep {
        final Deep child;
        Deep(Deep child) { this.child = child; }
    }

    static class DeepCodec implements ValueCodec<Deep> {
        public Class<Deep> type() { return Deep.class; }
        public void write(RmapOutput out, Deep v) { out.writeTlv(v.child); }
        public Deep read(RmapInput in) { return new Deep((Deep) in.readTlv()); }
    }

    // Иерархия для проверки «наиболее специфичного» резолва: GreenApple extends Apple implements Fruit
    interface Fruit { }
    static class Apple implements Fruit { }
    static class GreenApple extends Apple { }

    static class FruitCodec implements ValueCodec<Fruit> {
        public Class<Fruit> type() { return Fruit.class; }
        public void write(RmapOutput out, Fruit v) { }
        public Fruit read(RmapInput in) { return null; }
    }

    static class AppleCodec implements ValueCodec<Apple> {
        public Class<Apple> type() { return Apple.class; }
        public void write(RmapOutput out, Apple v) { }
        public Apple read(RmapInput in) { return null; }
    }

    private RmapCodec codecWith(CodecRegistry reg) {
        return new RmapCodec(reg);
    }

    @SuppressWarnings("unchecked")
    private <T> T roundtrip(RmapCodec codec, T v) {
        RmapByteWriter w = new RmapByteWriter();
        codec.encode(w, v);
        byte[] b = w.toByteArray();
        return (T) codec.decode(new RmapByteReader(b, 0, b.length), null);
    }

    @Test
    void serializable_registration_enables_reflective_codec() {
        CodecRegistry reg = new CodecRegistry();
        reg.serializable(Plain.class);
        Plain p = new Plain(5, "hi");
        assertThat(roundtrip(codecWith(reg), p)).isEqualTo(p);
    }

    @Test
    void value_codec_takes_priority_and_roundtrips() {
        CodecRegistry reg = new CodecRegistry();
        reg.register(new MoneyCodec());
        Money m = new Money(1999);
        RmapByteWriter w = new RmapByteWriter();
        codecWith(reg).encode(w, m);
        assertThat(w.toByteArray()[0] & 0xFF).isEqualTo(Tags.VALUE_CODEC);
        assertThat(roundtrip(codecWith(reg), m)).isEqualTo(m);
    }

    @Test
    void builtin_inet_socket_address_roundtrips() {
        CodecRegistry reg = new CodecRegistry();
        InetSocketAddress addr = new InetSocketAddress("example.com", 6790);
        InetSocketAddress back = roundtrip(codecWith(reg), addr);
        assertThat(back.getHostString()).isEqualTo("example.com");
        assertThat(back.getPort()).isEqualTo(6790);
    }

    @Test
    void builtin_locale_roundtrips() {
        CodecRegistry reg = new CodecRegistry();
        assertThat(roundtrip(codecWith(reg), Locale.forLanguageTag("ru-RU")))
                .isEqualTo(Locale.forLanguageTag("ru-RU"));
    }

    @Test
    void value_codec_with_corrupt_length_is_rejected() {
        // Кодек, который читает меньше, чем записал — рамка len должна поймать рассинхрон.
        CodecRegistry reg = new CodecRegistry();
        reg.register(new ValueCodec<Money>() {
            public Class<Money> type() { return Money.class; }
            public void write(RmapOutput out, Money v) { out.writeLong(v.getCents()); out.writeInt(7); }
            public Money read(RmapInput in) { return new Money(in.readLong()); } // не дочитал int
        });
        Money m = new Money(1);
        RmapByteWriter w = new RmapByteWriter();
        codecWith(reg).encode(w, m);
        byte[] b = w.toByteArray();
        assertThatThrownBy(
                () -> codecWith(reg).decode(new RmapByteReader(b, 0, b.length), null))
                .isInstanceOf(RmapCodecException.class);
    }

    @Test
    void recursive_value_codec_exceeds_depth_is_rejected() {
        // ValueCodec, рекурсивно спускающийся через writeTlv: цепочка Deep глубиной 40 > 32.
        // checkDepth в VALUE_CODEC-ветке обязан отвергнуть (иначе StackOverflow на всём графе).
        CodecRegistry reg = new CodecRegistry();
        reg.register(new DeepCodec());
        Deep d = null;
        for (int i = 0; i < 40; i++) {
            d = new Deep(d);
        }
        final Deep chain = d;
        assertThatThrownBy(() -> {
            RmapByteWriter w = new RmapByteWriter();
            codecWith(reg).encode(w, chain);
        }).isInstanceOf(RmapCodecException.class);
    }

    @Test
    void resolve_picks_most_specific_codec_deterministically() {
        // Кодеки на Fruit (интерфейс) и Apple (класс). GreenApple extends Apple точно не
        // зарегистрирован → резолв обязан выбрать наиболее специфичный (Apple), а не «первый
        // попавшийся» из недетерминированного порядка HashMap.
        CodecRegistry reg = new CodecRegistry();
        reg.register(new FruitCodec());
        reg.register(new AppleCodec());
        assertThat(reg.findCodec(GreenApple.class).type()).isEqualTo(Apple.class);
    }
}
