package me.moonways.bridgenet.model.util;

import me.moonways.bridgenet.model.service.players.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class PlayerIdMap<V> extends ConcurrentHashMap<UUID, V> {

    private static final long serialVersionUID = 8540228284873559054L;

    public PlayerIdMap() {
        super();
    }

    public PlayerIdMap(PlayerIdMap<V> other) {
        super(other);
    }

    public synchronized Optional<V> get(UUID playerId) {
        return Optional.ofNullable(super.get(playerId));
    }

    public synchronized Optional<V> get(Player player) {
        return get(player.getId());
    }

    public synchronized V getOrDefault(UUID playerId, Supplier<V> def) {
        return get(playerId).orElseGet(def);
    }

    public synchronized V getOrDefault(Player player, Supplier<V> def) {
        return getOrDefault(player.getId(), def);
    }

    public synchronized V getOrPut(UUID playerId, Supplier<V> factory) {
        Optional<V> optional = get(playerId);

        if (!optional.isPresent()) {
            V value = factory.get();
            put(playerId, value);

            return value;
        }

        return optional.get();
    }

    public synchronized V getOrPut(Player player, Supplier<V> factory) {
        return getOrPut(player.getId(), factory);
    }

    public synchronized V remove(Player player) {
        return remove(player.getId());
    }
}
