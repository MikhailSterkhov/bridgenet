package me.moonways.bridgenet.api.container;

public interface MapContainer<K, V> {

    void add(K key, V value);

    void remove(Object object);

    void removeAll();

    V get(Object object);

    boolean contains(K key);

}
