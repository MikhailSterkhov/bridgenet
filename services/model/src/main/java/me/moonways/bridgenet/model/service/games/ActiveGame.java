package me.moonways.bridgenet.model.service.games;

import java.util.UUID;

public interface ActiveGame {

    /**
     * Получить уникальный регистрационный номер активной игры.
     */
    UUID getUniqueId();

    /**
     * Получить текущую загруженную карту на данный сервер.
     */
    String getMap();

    /**
     * Получить текущее состояние игры
     */
    GameState getState();

    /**
     * Получить родительскую игру.
     */
    Game getParent();
}
