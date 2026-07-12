package me.moonways.rmap.codec;

/** SPI remote-refs (§10). Реализация — rpc-слой (ObjectTable/прокси-фабрика); кодек про них не знает. */
public interface RefContext {

    /** Интерфейс из wrap-списка, которым значение уходит как REMOTE_REF; null — кодировать значением. */
    Class<?> remoteInterfaceFor(Object value);

    /** encode-сторона: зарегистрировать объект, вернуть refId (повторный объект → тот же refId). */
    long registerRef(Object value, Class<?> iface);

    /** decode-сторона: построить ref-прокси интерфейса, привязанный к (connection, refId). */
    Object proxyForRef(long refId, Class<?> iface);
}
