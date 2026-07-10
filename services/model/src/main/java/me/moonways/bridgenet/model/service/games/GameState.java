package me.moonways.bridgenet.model.service.games;

import org.jetbrains.annotations.NotNull;


public interface GameState {

    /**
     * Получить статус игрового сервера.
     */
    GameStatus getStatus();

    /**
     * Получить название карты игрового сервера.
     */
    String getMap();

    /**
     * Сверить статус игрового сервера на необходимый.
     *
     * @param gameStatus - игровой статус.
     */
    boolean checkStatus(@NotNull GameStatus gameStatus);

    /**
     * Получить максимальное возможное количество игроков,
     * которые могут одновременно играть на данном игровом сервере.
     */
    int getMaxPlayers();

    /**
     * Получить максимальное возможное количество игроков
     * в команде.
     */
    int getPlayersInTeam();

    /**
     * Получить количество игроков, играющих или находящихся
     * в ожидании сейчас на сервере.
     */
    int getPlayers();

    /**
     * Получить количество наблюдателей за игрой
     */
    int getSpectators();

    /**
     * Получить общее суммарное количество игроков на данном
     * игровом сервере.
     */
    default int getTotalPlayers() {
        int players = getPlayers();
        int spectators = getSpectators();

        return players + spectators;
    }
}
