package me.moonways.rmap.codec;

/** SPI: пользовательский кодек значения (спека §5.3). */
public interface ValueCodec<T> {

    Class<T> type();

    void write(RmapOutput out, T value);

    T read(RmapInput in);
}
