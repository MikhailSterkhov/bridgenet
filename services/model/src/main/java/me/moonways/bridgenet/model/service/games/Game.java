package me.moonways.bridgenet.model.service.games;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Game {

    /**
     * Получить уникальный регистрационный номер игры.
     */
    UUID getUniqueId();

    /**
     * Получить название игры.
     */
    String getName();

    /**
     * Получить активную игровую карту, загруженную в игру
     * по ее уникальному идентификатору.
     *
     * @param uniqueId - ключ активной игры.
     */
    Optional<ActiveGame> getActiveGame(UUID uniqueId);

    /**
     * Получить список загруженных карт в этой игре
     */
    List<String> getLoadedMaps();

    /**
     * Получить список загруженных серверов для этой игры
     */
    List<GameServer> getLoadedServers();

    /**
     * Получить список активных игровых карт для этой игры.
     */
    List<ActiveGame> getActiveGames();

    /**
     * Получить список активных игровых карт, запущенных
     * на указанной карте.
     *
     * @param map - название игровой карты.
     */
    List<ActiveGame> getActiveGamesByMap(@NotNull String map);
}
