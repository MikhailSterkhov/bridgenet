package me.moonways.bridgenet.api.container;

import java.util.*;

public class MapContainerImpl<K, V> implements MapContainer<K, V> {

    private final Map<K, V> parent = new HashMap<>();

    @Override
    public void add(K key, V value) {
        parent.put(key, value);
    }

    @Override
    public void remove(Object object) {
        parent.remove(object);
    }

    @Override
    public void removeAll() {
        parent.clear();
    }

    @Override
    public V get(Object object) {
        return parent.get(object);
    }

    @Override
    public boolean contains(K key) {
        return parent.containsKey(key);
    }

    public Collection<V> values() {
        return parent.values();
    }
}
