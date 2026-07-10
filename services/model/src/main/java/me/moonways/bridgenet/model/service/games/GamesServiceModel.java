package me.moonways.bridgenet.model.service.games;

import me.moonways.bridgenet.model.service.servers.EntityServer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public interface GamesServiceModel {

    /**
     * Получить игру по ее уникальному регистрационному
     * идентификатору.
     *
     * @param uuid - идентификатор игры.
     */
    Game getGame(@NotNull UUID uuid);

    /**
     * Получить игру по ее названию.
     *
     * @param name - название игры.
     */
    Game getGame(@NotNull String name);

    /**
     * Получить список всех загруженных игр
     */
    List<Game> getLoadedGames();

    /**
     * Проверить, является ли сервер игровым.
     *
     * @param server - сервер.
     */
    boolean isGame(@NotNull EntityServer server);
}
